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
     * Last used ID for a client
     */
    private var currentID: Long = 1

    /**
     * Clients <-> ClientStatus bijection
     */
    private var clients: Bijection<Observer<ServerMessageEvent>, Long> = BijectionMap()

    /**
     * clientId to timestamp of last ping
     */
    private val lastPings: MutableMap<Long, Long> = mutableMapOf()

    private var schedule: List<ServerOutput.Schedule> = listOf()

    fun listen(){
        try {
            //Listen to the specified port
            val socket = ServerSocket(port)
            println("Server is running on port ${socket.localPort}")

            //Run a timer for scheduling
            thread { this.timer() }

            while (true) {
                //Wait for the next client that will connect, accepting it when it does
                val client = socket.accept()

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

    private fun serviceMessageToClient(clientID: Long, msg: String): Unit {
        val clientHandler = clients.inverse(clientID) //Get ClientHandler from its ID
        if (clientHandler != null)
            notifyObserver(
                    clientHandler,
                    ServerMessageEvent(
                            ServerMessageEvent.Action.MESSAGE,
                            serverMessageFormat(msg)
                    )
            )
    }

    private fun serviceMessageToRoom(roomName: String, msg: String): Unit {
        serverState.getClientIDsInRoom(roomName).forEach {
            serviceMessageToClient(it, Constants.roomSelectionPrefix + roomName + " " + msg)
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
                    val clientHandler = clients.inverse(output.clientID) //Get ClientHandler from its ID
                    if (clientHandler != null)
                        notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.STOP))}
                is ServerOutput.BanClient -> {}
                is ServerOutput.LiftBan -> {}
                is ServerOutput.ServiceMessageToClient -> serviceMessageToClient(output.clientID, output.msg)
                is ServerOutput.ServiceMessageToRoom -> serviceMessageToRoom(output.roomName, output.msg)
                is ServerOutput.ServiceMessageToEverybody -> {
                    notifyObservers(ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, serverMessageFormat(output.msg)))
                }
                is ServerOutput.MessageFromUserToRoom -> {
                    val roomName = output.message.room.name
                    //Send the messages only to clients of users that are members of the target room
                    serverState.getClientIDsInRoom(roomName).forEach {
                        val client = clients.inverse(it) //Get ClientHandler from its ID
                        if (client != null)
                            notifyObserver(
                                    client,
                                    ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, output.message.toFormattedMessage() + "\n")
                            )
                    }
                }
                is ServerOutput.Ping -> {
                    val clientHandler = clients.inverse(output.clientID) //Get ClientHandler from its ID
                    if (clientHandler != null)
                        notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.PING))
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
        //Handle messages from client
        synchronized (this) {
            if (event.msg.startsWith(Constants.pingString) && event.msg.length <= Constants.pingString.length + System.lineSeparator().length){
                lastPings.put(event.sender.uid, System.currentTimeMillis()) //Just received a ping
            } else {
                lastPings.put(event.sender.uid, System.currentTimeMillis()) //Consider a message as a ping
                serverState = serverState.processIncomingMessageFromClient(event.sender.uid, event.msg)
            }
        }

        //Execute the server output
        processOutputFromServer(serverState.currentOutput)
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
            observer.update(event = ServerMessageEvent(ServerMessageEvent.Action.STOP))
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
                        serverState = serverState.processIncomingMessageFromClient(-1, it.action, userOverride = it.user)
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
            if (!lastPings.containsKey(it)) lastPings.put(it, System.currentTimeMillis())
        }

        lastPings.entries.forEach{
            if (System.currentTimeMillis() - it.value > Constants.pingTimeoutAfterSeconds * 1000){
                val clientHandler = clients.inverse(it.key) //Get ClientHandler from its ID
                if (clientHandler != null){
                    val user = serverState.clientIDToUser(it.key)
                    notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.TIMEOUT))
                    if (user != null) {
                        serverState.getRoomsByUser(user).forEach {
                            serviceMessageToRoom(it.name,"User ${user.username} timeout.")
                        }
                    }
                }
            }
        }
    }


}