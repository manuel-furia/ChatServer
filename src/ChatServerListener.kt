//Author: Manuel Furia
//Student ID: 1706247

/* ChatServerListener.kt
 * Mutable class that takes care of accepting clients and handling the communication between server and clients.
 * All the output actions of the server are carried out by ChatServerListener. It also takes care
 * of updating the current ChatServerState (immutable), keeping the list of active clients and
 * regularly verifying their status through pings.
 */

import java.io.BufferedReader
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Accept and manages clients, input and output for the chat server.
 */
class ChatServerListener (serverState: ChatServerState, val port: Int, val pluginDirectory: String? = null) : Observer<ClientMessageEvent>, SelectiveObservable<ServerMessageEvent> {

    /**
     * Contains the logical state of the server
     */
    var serverState: ChatServerState = serverState
    private set

    //Is the server running? Set to false to exit the main loop
    private var running: Boolean = false

    //The socket the server will be listening on
    private val listenerSocket: ServerSocket = ServerSocket(port)

    // Clients <-> Client ID(Long) bijection
    private var clients: Bijection<Observer<ServerMessageEvent>, Long> = BijectionMap()

    //Last used ID for a client
    private var currentID: Long = 0

    //Server Console
    private val serverConsole = ServerConsole(currentID, System.`in`.bufferedReader(), System.out, this, this)

    //List of scheduled actions to execute at a set future time
    private var schedule: List<ServerOutput.Schedule> = listOf()

    //List of banned IP addresses
    private var banList: Set<String> = mutableSetOf()

    /**
     * clientId to timestamp of last ping
     */
    private val lastPings: MutableMap<Long, Long> = mutableMapOf()

    /**
     * Start the server
     */
    fun listen(topChatterBot: Boolean = false){
        try {
            running = true

            //Load commands and plugins
            val commands = if (pluginDirectory != null)
                Commands(File(pluginDirectory), System.out).allCommands
            else
                Commands().allCommands

            //Set the commands for the server
            serverState = serverState.setCommands(commands)

            println("Server is running on port ${listenerSocket.localPort}")

            //Register the server console as a user
            serverState = serverState.registerUser(
                    Constants.serverConsoleUsername,
                    serverConsole.uid,
                    ChatUser.Level.ADMIN)

            //Process the output from the registration of the new user
            processOutputFromServer(serverState.currentOutput)

            if (topChatterBot){
                val topChatterUsername = "TopChatter"

                //Register the "TopChatter" bot as a client
                val topChatter = TopChatter(currentID, this, this, topChatterUsername)

                serverState = serverState.registerUser(
                        topChatterUsername,
                        topChatter.uid,
                        ChatUser.Level.NORMAL)
                        .userJoinRoom(Constants.mainRoomName, topChatterUsername)

                //Process the output from the registration of the new user
                processOutputFromServer(serverState.currentOutput)

                //Run the TopChatter bot
                //Thread(topChatter).start()
                thread { topChatter.run() }
            }

            //Run a timer for scheduling
            thread { this.timer() }

            //Run the server console
            thread { serverConsole.run() }


            while (running) {
                //Wait for the next client that will connect, accepting it when it does
                val client = listenerSocket.accept()

                //Check if the IP address of the new client is banned
                val isBanned = synchronized(this) {
                    banList.contains(client.inetAddress.hostAddress)
                }

                if (isBanned){
                    client.close()
                    println("Banned ip address ${client.inetAddress.hostAddress} tried to login")
                    continue
                }

                println("Client connected: ${client.inetAddress.hostAddress}")

                //Create a tcp client handler for the new client
                val clientHandler = TCPClientHandler(currentID, client, this, this)

                //Register the new unknown user with a temporary username
                serverState = serverState.registerUser(
                        Constants.unknownUsernamePrefix + clientHandler.uid,
                        clientHandler.uid,
                        ChatUser.Level.UNKNOWN)

                //Process the server output generated from the registration of the new user
                processOutputFromServer(serverState.currentOutput)

                // Run client in it's own thread.
                thread {
                    clientHandler.run()
                }
            }
        } catch (ex: Exception){
            println("${ex.message}")
        } finally {
            for (client in clients.domainEntries){
                client.update(ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
            }
            listenerStop()
        }
    }

    /**
     * Stop the listener and main loop thread, does not stop the client threads
     */
    private fun listenerStop(){
        if (!listenerSocket.isClosed)
            listenerSocket.close() //Stop the listener
        running = false
    }

    /**
     * Stop the client threads
     */
    private fun clientsStop(){
        for (client in clients.domainEntries){
            if (client != serverConsole)
                client.update(ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
        }
    }

    /**
     * Stop the client threads and the the listener and main loop thread
     */
    fun safeStop(){
        println("Stopping clients... ")
        clientsStop()
        while (clients.size() > 1) { Thread.sleep(500) } //Wait for all the clients to stop (except the server console)
        println("OK.\n")
        listenerStop()

        //Stop the server console at last
        serverConsole.update(ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
    }

    /**
     * Given a client id, it fetches the associated Observer as TCPClientHandler and makes it available for the code in
     * the "block" lambda.
     * If the client id is invalid or the observer is not a TCPClientHandler, does not execute the "block" lambda
     * Note: used for actions that apply only to TCP clients (like ip address ban)
     */
    private fun fetchTCPClientHandler(id: Long, block: (TCPClientHandler) -> Unit){
        val clientHandler = clients.inverse(id) //Get TCPClientHandler from its ID
        if (clientHandler != null && clientHandler is TCPClientHandler)
            block.invoke(clientHandler)
    }

    /**
     * Given a client id, it fetches the associated server message event Observer and makes it available for the code
     * in the "block" lambda.
     * If the client id is invalid, does not execute the "block" lambda.
     * Note: Prefer fetchServerMessageObserver over fetchClientHandler as it will work for any generic observer
     */
    private fun fetchServerMessageObserver(id: Long, block: (Observer<ServerMessageEvent>) -> Unit){
        val observer = clients.inverse(id) //Get Observer from its ID
        if (observer != null)
            block.invoke(observer)
    }

    /**
     * Send a service message (no user associated with it) to a client identified by clientID
     */
    private fun serviceMessageToClient(clientID: Long, msg: String): Unit {
        fetchServerMessageObserver(clientID) {
            notifyObserver(
                    it,
                    ServerMessageEvent(
                            ServerMessageEvent.Action.MESSAGE,
                            serverState,
                            serverMessageFormat(msg)
                    )
            )
        }
    }

    /**
     * Send a service message (no user associated with it) only to a specific room
     */
    private fun serviceMessageToRoom(roomName: String, msg: String): Unit {
        serverState.getClientIDsInRoom(roomName).forEach {
            serviceMessageToClient(it, Constants.roomSelectionPrefix + roomName + " " + msg)
        }
    }

    /**
     * Send a message from one user to all the clients in one room
     */
    private fun messageFromUserToRoom(message: ChatHistory.Entry): Unit {
        serverState
                .getUsersInRoom(message.room.name)
                .filter {user -> message.room.canUserRead(user) } //Send only to user with at least READ permissions
                .mapNotNull{ serverState.userToClientID(it) }
                .forEach {id ->
                    fetchServerMessageObserver(id) {
                        notifyObserver(
                                it,
                                ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, serverState, message.toFormattedMessage() + "\n")
                        )
                    }
                }
    }


    /**
     * Send a ping message to the specified client id
     */
    private fun pingClientID(id: Long, msg: String): Unit {
        //Send a ping message to the specified client id
        fetchServerMessageObserver(id) {
            notifyObserver(it, ServerMessageEvent(ServerMessageEvent.Action.PING, serverState, msg))
        }
    }

    /**
     * Send a ping message to the specified user
     */
    private fun pingUser(user: ChatUser, msg: String): Unit {
        //Get the clientid of the user
        val clientID = serverState.userToClientID(user)

        //Send a ping to the user's client if it exists
        if (clientID != null)
            pingClientID(clientID, msg)
    }

    /**
     * Format a service message adding the server prefix ":-" to every line, to allow the clients to identify that is a service message
     */
    private fun serverMessageFormat(msg: String): String{
        if (msg.contains('\n')){
            return Constants.serverMessagePrefix + " " + msg.replace("\n", "\n" + Constants.serverMessagePrefix + " ") + "\n"
        } else {
            return Constants.serverMessagePrefix + " " + msg + "\n"
        }
    }

    /**
     * Takes the output of a server state and executes the list of pending actions that it contains (currentOutput)
     * Once executed, the current server state is replaced with a new one without pending actions (currentOutput = empty list)
     */
    private fun processOutputFromServer(currentOutput: List<ServerOutput>){
        //Get the server output (list of pending actions)
        val outputs = serverState.currentOutput

        //For each output action, execute the appropriate effect
        //Note: An output action is an object of type ServerOutput.
        //      ServerOutput is an algebraic datatype composed of all the possible output actions the server can do.
        //      Kotlin converts automatically output into the correct component of the ADT inside the when statement.
        outputs.forEach { output ->

            when (output){
                is ServerOutput.DropClient -> {
                    //Tell the client to stop
                    fetchServerMessageObserver (output.clientID) {
                        notifyObserver(it, ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
                    }
                }
                is ServerOutput.BanClient -> {
                    //Add the client's ip address to the ban list
                    fetchTCPClientHandler(output.clientID) {
                        synchronized(this){banList = banList + it.hostAddress}
                        println("IP ${it.hostAddress} is now banned from the server")
                    }
                }
                //Remove the banned ip address from the ban list
                is ServerOutput.LiftBan -> {synchronized(this){banList = banList - output.bannedIP}}
                //Send a service message (not associated with a user) to the client identified by output.clientID
                is ServerOutput.ServiceMessageToClient -> serviceMessageToClient(output.clientID, output.msg)
                //Send a service message (not associated with a user) to all the clients in a room
                is ServerOutput.ServiceMessageToRoom -> serviceMessageToRoom(output.roomName, output.msg)
                //Send a service message (not associared with a user) to everybody
                is ServerOutput.ServiceMessageToEverybody -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, serverState, serverMessageFormat(output.msg)))
                }
                is ServerOutput.MessageFromUserToRoom -> {
                    //Send the messages only to clients of users that are members of the target room and have at least READ permissions
                    messageFromUserToRoom(output.message)
                }
                is ServerOutput.Ping -> {
                    //Send a ping message to the specified client id
                    fetchServerMessageObserver(output.clientID) {
                        notifyObserver(it, ServerMessageEvent(ServerMessageEvent.Action.PING, serverState))
                    }
                }
                //Send a ping message to the specified user, if it exists
                is ServerOutput.PingUser -> pingUser(output.toUser, " from ${output.fromUser.username}")
                //Send a ping message to the specified client, if it exists
                is ServerOutput.Ping -> pingClientID(output.clientID, "")
                //Send to all clients a notification that a new user joined
                is ServerOutput.UserJoinedNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.USER_JOINED, serverState, output.user))
                }
                //Send to all clients a notification that a logged user has left
                is ServerOutput.KnownUserLeftNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.KNOWN_USER_LEFT, serverState, output.user))
                }
                //Send to all clients a notification that an unknown (not logged) user has left
                is ServerOutput.UnknownUserLeftNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.UNKNOWN_USER_LEFT, serverState, output.user))
                }
                //Send to all clients a notification that a user changed
                is ServerOutput.UserNameChangedNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.USER_CHANGE, serverState, output.user))
                }
                //Add the command to be executed to the schedule list
                is ServerOutput.Schedule -> synchronized(this){schedule = schedule + output}
                is ServerOutput.Stop -> safeStop()
                //No action
                is ServerOutput.None -> {}
            }
        }

        //As the current output has been handled, create a new state with no output
        synchronized(this) {
            serverState = serverState.updateOutput(listOf())
        }
    }

    override fun update(event: ClientMessageEvent) {
        val uid = clients.direct(event.sender)

        if (uid != null) {

            synchronized(this) {
                //Record any message as a ping
                lastPings.put(uid, System.currentTimeMillis())

                if (event.msg.startsWith(Constants.pingString) && event.msg.length <= Constants.pingString.length + System.lineSeparator().length) {
                    return //Just a ping, we already recorded it. No more actions are needed.
                } else if (event.stopped){ //The client handler notified that its thread has been stopped
                    clients = clients.removeByDomainElement(event.sender) //Remove the client
                } else { //A normal message or command
                    serverState = serverState.processIncomingMessageFromClient(uid, event.msg)
                }
            }

            //Execute the server output
            processOutputFromServer(serverState.currentOutput)

        }
    }

    override fun registerObserver(observer: Observer<ServerMessageEvent>) {
        //We are modifying the client <-> id bijection, so we need to synchronize
        synchronized(this) {
            clients = clients + Pair(observer, currentID)
            currentID += 1
        }
    }

    override fun unregisterObserver(observer: Observer<ServerMessageEvent>) {
        //We are modifying the client <-> id bijection, so we need to synchronize
        synchronized(this){
            //Remove the observer id pair from the client <-> id bijection
            clients = clients.removeByDomainElement(observer)
        }
    }

    override fun notifyObservers(event: ServerMessageEvent) {
        //Keep a reference to clients object in case clients reference changes with another thread during the for loop
        val clientsRef = clients
        for (client in clientsRef.domainEntries){
            client.update(event)
        }
    }

    override fun notifyObserver(observer: Observer<ServerMessageEvent>, event: ServerMessageEvent) {
        observer.update(event)
    }

    /**
     * Will execute scheduled commands and check for ping from the clients in the background
     * @param resolutionMillis The resolution of the timer (one tick every resolutionMillis milliseconds)
     */
    private fun timer(resolutionMillis: Long = 1000){

        //When did we last checked the timer
        var pingsLastCkeckedMillisAgo = 0L

        while (running) {

            val timestamp = System.currentTimeMillis()

            //If there is some scheduled commands to execute
            if (schedule.size > 0) {
                //Synchronize because we are modifying the serverState and the scheduled events list
                synchronized(this) {
                    //Filter to get only events that we need to execute (execution timestamp < current timestamp)
                    val elapsed = schedule.filter { it.timestamp <= timestamp }
                    //Execute each one as an incoming message or command from the user that originally set the schedule
                    elapsed.forEach {
                        serverState = serverState.processIncomingMessageFromClient(
                                -1,
                                Constants.roomSelectionPrefix + it.room.name + " " + it.action,
                                userOverride = it.user)
                        processOutputFromServer(serverState.currentOutput)
                    }
                    //Remove the scheduled commands that we just executed
                    schedule = schedule - elapsed
                }
            }
            //Wait for the amount of time specified by the resolution
            Thread.sleep(resolutionMillis)
            //Add the elapsed time for this tick to the total time passed since the last ping check
            pingsLastCkeckedMillisAgo += resolutionMillis
            //If enough time has passed since the last check, do check the last ping time for all the users
            if (pingsLastCkeckedMillisAgo >= Constants.pingTimeoutCheckEverySeconds * 1000){
                checkPings()
                pingsLastCkeckedMillisAgo = 0
            }
        }


    }

    private fun checkPings(){
        //Add clients that just joined to the lastPings table
        clients.codomainEntries.forEach {
            if (it != serverConsole.uid && !lastPings.containsKey(it))
                lastPings.put(it, System.currentTimeMillis())
        }

        //For each client registered in the lastPing table
        lastPings.entries.forEach{
            //If it is not the server console, and too much time has passed without a ping, do remove the client (timeout)
            if (it.key != serverConsole.uid && System.currentTimeMillis() - it.value > Constants.pingTimeoutAfterSeconds * 1000){
                //Fetch the observer associated with the id of the client to remove
                fetchServerMessageObserver(it.key){ observer ->
                    //Fetch the user associated with the id of the client to remove
                    val user = serverState.clientIDToUser(it.key)
                    //Notify the observer of the timeout
                    notifyObserver(observer, ServerMessageEvent(ServerMessageEvent.Action.TIMEOUT, serverState))
                    //If we can find the user that was associated with the client ID...
                    if (user != null) {
                        //...send a message to all the rooms notifying the other users that this
                        //user has been dropped because of timeout
                        serverState.getRoomsByUser(user).forEach {
                            serviceMessageToRoom(it.name,"User ${user.username} timeout.")
                        }
                    }
                }
            }
        }

        //Synchronize, as we are modifying the server state
        synchronized(this){
            //Remove users without client
            val userIDs = serverState.users.mapNotNull {serverState.userToClientID(it)}
            val validIDs = clients.codomainEntries
            val idsToRemove = userIDs - validIDs
            val newServerState = idsToRemove.fold(serverState) {s, id ->
                val username = serverState.clientIDToUser(id)?.username
                if (username != null)
                    s.removeUser(username)
                else
                    s
            }

            serverState = newServerState
        }

    }


}