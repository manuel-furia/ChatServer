import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Takes care of accepting clients and handling the communication between server and clients
 */
class ChatServerListener (serverState: ChatServerState, val port: Int = 61673) : Observer<ClientMessageEvent>, SelectiveObservable<ServerMessageEvent> {

    /**
     * Contains the logical state of the server
     */
    var serverState: ChatServerState = serverState
    private set

    /**
     * Server Console
     */
    private val serverConsole = ServerConsole(this, this)

    /**
     * Last used ID for a client
     */
    private var currentID: Long = serverConsole.uid + 1


    /**
     * Clients <-> ClientStatus bijection
     */
    private var clients: Bijection<Observer<ServerMessageEvent>, Long> = BijectionMap(setOf(serverConsole to serverConsole.uid))

    private var schedule: List<ServerOutput.Schedule> = listOf()

    private var banList: Set<String> = mutableSetOf()

    /**
     * clientId to timestamp of last ping
     */
    private val lastPings: MutableMap<Long, Long> = mutableMapOf()




    fun listen(topChatterBot: Boolean = false){
        try {
            //Listen to the specified port
            val socket = ServerSocket(port)
            println("Server is running on port ${socket.localPort}")

            //Register the server console as a user
            serverState = serverState.registerUser(
                    Constants.serverConsoleUsername,
                    serverConsole.uid,
                    ChatUser.Level.ADMIN)

            //Process the output from the registration of the new user
            processOutputFromServer(serverState.currentOutput)

            if (topChatterBot){
                //Register the "TopChatter" bot as a client
                val topChatter = TopChatter(this, this)
                clients = clients + (topChatter to currentID)

                serverState = serverState.registerUser(
                        "TopChatter",
                        currentID,
                        ChatUser.Level.NORMAL)
                        .userJoinRoom(Constants.mainRoomName, "TopChatter")

                currentID += 1

                //Process the output from the registration of the new user
                processOutputFromServer(serverState.currentOutput)

                //Run the TopChatter bot
                thread { topChatter.run() }
            }

            //Run a timer for scheduling
            thread { this.timer() }

            //Run the server console
            thread { serverConsole.run() }


            while (true) {
                //Wait for the next client that will connect, accepting it when it does
                val client = socket.accept()

                val isBanned = synchronized(this) {banList.contains(client.inetAddress.hostAddress)}

                if (isBanned){
                    client.close()
                    println("Banned ip address ${client.inetAddress.hostAddress} tried to login")
                    continue
                }

                println("Client connected: ${client.inetAddress.hostAddress}")

                //Create a client handles for the new client
                val clientHandler = ClientHandler(currentID, client, this, this)

                //Register the new unknown user
                serverState = serverState.registerUser(
                        Constants.unknownUsernamePrefix + clientHandler.uid,
                        clientHandler.uid,
                        ChatUser.Level.UNKNOWN)

                //Process the output from the registration of the new user
                processOutputFromServer(serverState.currentOutput)

                // Run client in it's own thread.
                thread {
                    clientHandler.run()
                }
            }
        } catch (ex: Exception){
            println("Server Listener Error: ${ex.message}")
        }
    }

    private fun fetchClientHandler(id: Long, block: (ClientHandler) -> Unit){
        val clientHandler = clients.inverse(id) //Get ClientHandler from its ID
        if (clientHandler != null && clientHandler is ClientHandler)
            block.invoke(clientHandler)
    }

    private fun fetchServerMessageObserver(id: Long, block: (Observer<ServerMessageEvent>) -> Unit){
        val observer = clients.inverse(id) //Get ClientHandler from its ID
        if (observer != null)
            block.invoke(observer)
    }

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

    private fun serviceMessageToRoom(roomName: String, msg: String): Unit {
        serverState.getClientIDsInRoom(roomName).forEach {
            serviceMessageToClient(it, Constants.roomSelectionPrefix + roomName + " " + msg)
        }
    }

    private fun messageFromUserToRoom(message: ChatHistory.Entry): Unit {
        serverState
                .getUsersInRoom(message.room.name)
                .filter { message.room.canUserRead(it) }
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

    private fun serverMessageFormat(msg: String): String{
        if (msg.contains('\n')){
            return Constants.serverMessagePrefix + " " + msg.replace("\n", "\n" + Constants.serverMessagePrefix + " ") + "\n"
        } else {
            return Constants.serverMessagePrefix + " " + msg + "\n"
        }
    }

    /**
     * Takes the output of a server state and executes the list of pending actions that it contains
     * Once executed, the current server state is replaced with a new one without pending actions
     */
    private fun processOutputFromServer(currentOutput: List<ServerOutput>){
        //Get the server output (list of pending actions)
        val outputs = serverState.currentOutput

        //For each output action, execute the appropriate effect
        outputs.forEach { output ->

            when (output){
                is ServerOutput.DropClient -> {
                    fetchServerMessageObserver (output.clientID) {
                        notifyObserver(it, ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
                    }
                }
                is ServerOutput.BanClient -> {
                    fetchClientHandler(output.clientID) {
                        synchronized(this){banList = banList + it.hostAddress}
                    }
                }
                is ServerOutput.LiftBan -> {synchronized(this){banList = banList - output.bannedIP}}
                is ServerOutput.ServiceMessageToClient -> serviceMessageToClient(output.clientID, output.msg)
                is ServerOutput.ServiceMessageToRoom -> serviceMessageToRoom(output.roomName, output.msg)
                is ServerOutput.ServiceMessageToEverybody -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, serverState, serverMessageFormat(output.msg)))
                }
                is ServerOutput.MessageFromUserToRoom -> {
                    //Send the messages only to clients of users that are members of the target room and have at least READ permissions
                    messageFromUserToRoom(output.message)
                }
                is ServerOutput.Ping -> {
                    fetchServerMessageObserver(output.clientID) {
                        notifyObserver(it, ServerMessageEvent(ServerMessageEvent.Action.PING, serverState))
                    }
                }
                is ServerOutput.UserJoinedNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.USER_JOINED, serverState, output.user))
                }
                is ServerOutput.KnownUserLeftNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.KNOWN_USER_LEFT, serverState, output.user))
                }
                is ServerOutput.UnknownUserLeftNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.UNKNOWN_USER_LEFT, serverState, output.user))
                }
                is ServerOutput.UserNameChangedNotification -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.USER_CHANGE, serverState, output.user))
                }
                is ServerOutput.Schedule -> synchronized(this){schedule = schedule + output}
                is ServerOutput.None -> {} //No action
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
                lastPings.put(uid, System.currentTimeMillis())

                if (event.msg.startsWith(Constants.pingString) && event.msg.length <= Constants.pingString.length + System.lineSeparator().length) {
                    //Just a ping, we already recorded it. No more actions are needed.
                } else {
                    if (event.doNotInterpret){
                        val user = serverState.clientIDToUser(uid)
                        if (user != null) {
                            val rooms = serverState.getRoomsByUser(user)
                            rooms.forEach { room ->
                                messageFromUserToRoom(ChatHistory.Entry(event.msg, user, room, System.currentTimeMillis()))
                            }
                            return //Return instead of reprocessing the output from the server (because doNotInterpret == true)
                        }
                    } else {
                        serverState = serverState.processIncomingMessageFromClient(uid, event.msg)
                    }
                    /*

                    if (event.sender is ClientHandler)
                        serverState = serverState.processIncomingMessageFromClient(event.sender.uid, event.msg)
                    else if (event.sender is ServerConsole)
                        serverState = serverState.processIncomingMessageFromClient(event.sender.uid, event.msg)
                    else if (event.sender is TopChatter) {
                        val topChatter = serverState.getUserByUsername("TopChatter")
                        val mainRoom = serverState.getRoomByName(Constants.mainRoomName)

                        if (topChatter != null && mainRoom != null)
                            messageFromUserToRoom(ChatHistory.Entry(event.msg, topChatter, mainRoom, System.currentTimeMillis()))

                        return //Return instead of reprocessing the output from the server, as we didn't modify it
                    }*/

                }
            }

            //Execute the server output
            processOutputFromServer(serverState.currentOutput)

        }
    }

    @Synchronized override fun registerObserver(observer: Observer<ServerMessageEvent>) {
        clients = clients + Pair(observer, currentID)
        currentID += 1
    }

    override fun unregisterObserver(observer: Observer<ServerMessageEvent>) {
        val id = clients.direct(observer)
        if (id != null) {
            val pair = Pair(observer, id)
            synchronized(this) {
                clients = clients - pair
            }
            observer.update(event = ServerMessageEvent(ServerMessageEvent.Action.STOP, serverState))
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

    private fun timer(resolutionMillis: Long = 1000){

        var pingsLastCkeckedMillisAgo = 0L

        while (true) {
            synchronized(this) {
                val timestamp = System.currentTimeMillis()
                if (schedule.size > 0) {
                    val elapsed = schedule.filter { it.timestamp <= timestamp }
                    elapsed.forEach {
                        serverState = serverState.processIncomingMessageFromClient(
                                -1,
                                Constants.roomSelectionPrefix + it.room.name + " " + it.action,
                                userOverride = it.user)
                        processOutputFromServer(serverState.currentOutput)
                    }
                    schedule = schedule - elapsed
                }
            }

            Thread.sleep(resolutionMillis)
            pingsLastCkeckedMillisAgo += resolutionMillis

            if (pingsLastCkeckedMillisAgo >= Constants.pingTimeoutCheckEverySeconds * 1000){
                checkPings()
                pingsLastCkeckedMillisAgo = 0
            }
        }


    }

    private fun checkPings(){
        //Add clients that just joined to the lastPings table
        clients.codomainEntries.forEach {
            if (it != serverConsole.uid && !lastPings.containsKey(it)) lastPings.put(it, System.currentTimeMillis())
        }

        lastPings.entries.forEach{
            if (it.key != serverConsole.uid && System.currentTimeMillis() - it.value > Constants.pingTimeoutAfterSeconds * 1000){
                fetchServerMessageObserver(it.key){ observer ->
                    val user = serverState.clientIDToUser(it.key)
                    notifyObserver(observer, ServerMessageEvent(ServerMessageEvent.Action.TIMEOUT, serverState))
                    if (user != null) {
                        serverState.getRoomsByUser(user).forEach {
                            serviceMessageToRoom(it.name,"User ${user.username} timeout.")
                        }
                    }
                }
            }
        }

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