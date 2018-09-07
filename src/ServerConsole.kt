class ServerConsole(observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>): Observer<ServerMessageEvent>, Observable<ClientMessageEvent>, Runnable {

    private val observer: Observer<ClientMessageEvent> = observer

    val uid = 0L

    override fun run() {
        while (true) {
            try {
                val text = readLine() ?: ""

                notifyObservers(ClientMessageEvent(this, text))

            } catch (ex: Exception) {
                error("")
            }
        }
    }

    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.MESSAGE -> print(event.msg)
            ServerMessageEvent.Action.PING -> println(":- Somebody is pinging the server console. :D")
            ServerMessageEvent.Action.STOP -> {} //You can't stop the server console
            ServerMessageEvent.Action.TIMEOUT -> {} //The server console does not timeout
            ServerMessageEvent.Action.USER_CHANGE -> {}
            ServerMessageEvent.Action.USER_JOINED -> {}
            ServerMessageEvent.Action.KNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.UNKNOWN_USER_LEFT -> {}
            ServerMessageEvent.Action.ERROR -> println("Server Error: " + event.msg)
        }
    }

    override fun registerObserver(observer: Observer<ClientMessageEvent>) {
        //Do nothing. The observer is only the server specified in the constructor, and it is unchangeable
    }

    override fun unregisterObserver(observer: Observer<ClientMessageEvent>) {
        //Do nothing. The observer is only the server specified in the constructor, and it is unchangeable
    }

    override fun notifyObservers(event: ClientMessageEvent) {
        observer?.update(event)
    }


}