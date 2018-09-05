import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation
import java.net.ServerSocket
import kotlin.concurrent.thread


class ChatServerListener (serverState: ChatServerState, val port: Int = 61673) : Observer<ClientMessageEvent>, SelectiveObservable<ServerMessageEvent> {

    var serverState: ChatServerState = serverState
    private set

    var currentID: Int = 1

    var clients: Bijection<Observer<ServerMessageEvent>, Int> = BijectionMap()

    fun listen(){
        try {
            val socket = ServerSocket(port)
            println("Server is running on port ${socket.localPort}")

            while (true) {
                val client = socket.accept()
                println("Client connected: ${client.inetAddress.hostAddress}")
                val clientHandler = ClientHandler(currentID, client, this, this)

                //Register the new unknown user
                serverState = serverState.registerUser(
                        Constants.unknownUsernamePrefix + clientHandler.uid,
                        clientHandler.uid,
                        ChatUser.Level.UNKNOWN)

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

    private fun processOutputFromServer(currentOutput: List<ServerOutput>){
        //Execute the server output
        val outputs = serverState.currentOutput


        outputs.forEach { output ->

            when (output){
                is ServerOutput.DropClient -> {
                    val clientHandler = clients.inverse(output.clientID)
                    if (clientHandler != null)
                        notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.STOP))}
                is ServerOutput.BanClient -> {}
                is ServerOutput.LiftBan -> {}
                is ServerOutput.ServiceMessageToClient -> {
                    val clientHandler = clients.inverse(output.clientID)
                    if (clientHandler != null)
                        notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, output.msg))
                }
                is ServerOutput.MessageFromUserToRoom -> {
                    val roomName = output.message.room.name
                    serverState.getClientIDsInRoom(roomName).forEach {
                        val client = clients.inverse(it)
                        if (client != null)
                            notifyObserver(
                                    client,
                                    ServerMessageEvent(ServerMessageEvent.Action.MESSAGE, output.message.toTextMessage() + "\n")
                            )
                    }
                }
                is ServerOutput.Ping -> {
                    val clientHandler = clients.inverse(output.clientID)
                    if (clientHandler != null)
                        notifyObserver(clientHandler, ServerMessageEvent(ServerMessageEvent.Action.PING))
                }
                is ServerOutput.None -> {}
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