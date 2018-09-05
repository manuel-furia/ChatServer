import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*

class ClientHandler(val uid: Long, tcpClient: Socket, observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>): Observer<ServerMessageEvent>, Observable<ClientMessageEvent>, Runnable {
    private val client: Socket = tcpClient
    private val observable: Observable<ServerMessageEvent> = observable
    private val reader: Scanner = Scanner(tcpClient.getInputStream())
    private val writer: OutputStream = tcpClient.getOutputStream()

    private var observer: Observer<ClientMessageEvent>? = observer

    var running: Boolean = false
    private set

    init {
        observable.registerObserver(this)
    }

    override fun run() {
        running = true

        while (running) {
            try {
                val text = reader.nextLine()

                notifyObservers(ClientMessageEvent(this, text))

            } catch (ex: Exception) {
                error("")
            }
        }
    }

    override fun registerObserver(observer: Observer<ClientMessageEvent>) {
        this.observer = observer
    }

    override fun unregisterObserver(observer: Observer<ClientMessageEvent>) {
        this.observer = null
    }

    override fun notifyObservers(event: ClientMessageEvent) {
        observer?.update(event)
    }


    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.MESSAGE -> if (event.msg != null) send(event.msg)
            ServerMessageEvent.Action.PING -> send("TODO: Implement ping")
            ServerMessageEvent.Action.STOP -> stop()
            else -> serverError(event.msg ?: "")
        }
    }

    private fun send(message: String) {
        writer.write(message.toByteArray(Charset.defaultCharset()))
    }

    private fun stop() {
        running = false
        observable.unregisterObserver(this)
        client.close()
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