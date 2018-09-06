import com.sun.xml.internal.bind.v2.runtime.reflect.opt.Const
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
    var currentID: Long = 1

    /**
     * Clients <-> ID bijection
     */
    var clients: Bijection<Observer<ServerMessageEvent>, Long> = BijectionMap()

    fun listen(){
        try {
            //Listen to the specified port
            val socket = ServerSocket(port)
            println("Server is running on port ${socket.localPort}")

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
                is ServerOutput.ServiceMessageToRoom -> {
                    serverState.getClientIDsInRoom(output.roomName).forEach {
                        serviceMessageToClient(it, Constants.roomSelectionPrefix + output.roomName + " " + output.msg)
                    }
                }
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
                is ServerOutput.None -> {} //No action
            }
        }

        //As the current output has been handled, create a new state with no output
        serverState = serverState.updateOutput(listOf())
    }

    @Synchronized override fun update(event: ClientMessageEvent) {
        //Handle messages from client
        serverState = serverState.processIncomingMessageFromClient(event.sender.uid, event.msg)

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

}