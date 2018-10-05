//Author: Manuel Furia
//Student ID: 1706247

/* TCPClientHandler.kt
 * Handle tcp communication with a tcp client, notifying the server of new messages from the client and sending
 * server messages to the client.
 * The class inherits from ThreadedClient, which implements the Observer/Observable pattern for a generic client and
 * provides thread handling functionality (running and stopping when necessary)
 */

import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*

/**
 * TCP client connected to the server.
 * @param uid Unique ID of the client
 * @param tcpClient Socket representing the TCP connection between server and client
 * @param observer The server that will observe this client handler
 * @param observable The server that this client handler will observe
 */
class TCPClientHandler(uid: Long, tcpClient: Socket, observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>)
    : ThreadedClient(uid, observer, observable), Runnable {

    //Handles the client TCP connection
    private val client: Socket = tcpClient
    //Reader and writer for the TCP connection
    private val reader: Scanner = Scanner(tcpClient.getInputStream())
    private val writer: OutputStream = tcpClient.getOutputStream()

    val hostAddress = tcpClient.inetAddress.hostAddress

    override fun run() {
        super.run() //Tell the superclass (ThreadedClient) that we are running

        while (running) {
            try {
                val text = reader.nextLine()

                notifyObservers(ClientMessageEvent(this, text))

            } catch (ex: Exception) {
                error("")
            }
        }
    }

    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.MESSAGE -> if (event.msg != null) send(event.msg)
            ServerMessageEvent.Action.PING -> send(Constants.pingString + event.msg + "\n")
            ServerMessageEvent.Action.STOP -> stop()
            ServerMessageEvent.Action.TIMEOUT -> stop()
            ServerMessageEvent.Action.USER_JOINED -> {}
            ServerMessageEvent.Action.KNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.UNKNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.USER_CHANGE -> {}
            ServerMessageEvent.Action.ERROR -> serverError(event.msg ?: "")
        }
    }

    private fun send(message: String) {
        writer.write(message.toByteArray(Charset.defaultCharset()))
    }

    override fun stop() {
        client.close()
        super.stop() //Stop the thread
    }

    private fun error(msg: String) {
        println("${client.inetAddress.hostAddress} connection dropped. $msg")
        stop()
    }

    private fun serverError(msg: String){
        send(msg)
        stop()
    }

}