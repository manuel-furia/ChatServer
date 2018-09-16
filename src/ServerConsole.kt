import java.io.*

//Author: Manuel Furia
//Student ID: 1706247

/*
 * Always present client that operates from the terminal of the server application, with admin privileges.
 * Can see the messages of all users in all rooms (:messages all).
 */

/**
 * Always present client that operates from the terminal of the server application, with admin privileges.
 * @param uid Unique ID of the client
 * @param input BufferedReader from which to read the user input
 * @param output PrintStream to which the messages and other output will be written
 * @param observer The server that will observe this client
 * @param observable The server that this client will observe
 */

class ServerConsole(
        uid: Long,
        val input: BufferedReader, //
        val output: PrintStream,
        observer: Observer<ClientMessageEvent>,
        observable: Observable<ServerMessageEvent>)
    : ThreadedClient(uid, observer, observable), Runnable {


    override fun run() {
        super.run()
        while (running) {
            try {
                val text = input.readLine() ?: ""
                //Notify the listening servers of the text written on this server's terminal
                notifyObservers(ClientMessageEvent(this, text))
            } catch (ex: Exception) {
                error("")
            }
        }
    }

    /**
     * Received a message from the server
     */
    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.MESSAGE -> output.print(event.msg)
            ServerMessageEvent.Action.PING -> {
                output.println(":- ${event.msg?.replace(" from ", "") ?: "Somebody"} is pinging the server console. :D")
            }
            ServerMessageEvent.Action.STOP -> {stop()}
            ServerMessageEvent.Action.TIMEOUT -> {} //The server console does not timeout
            ServerMessageEvent.Action.USER_CHANGE -> {} //Nothing to do if the user list changes
            ServerMessageEvent.Action.USER_JOINED -> {}
            ServerMessageEvent.Action.KNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.UNKNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.ERROR -> output.println("Server Error: " + event.msg)
        }
    }


}