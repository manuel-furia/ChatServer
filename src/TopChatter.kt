class TopChatter(observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>, val username: String): Observer<ServerMessageEvent>, Observable<ClientMessageEvent>, Runnable {
    private val observer: Observer<ClientMessageEvent> = observer

    val uid = 0L

    private var outMessageList: List<String> = listOf()
    private var lastPinged = 0L

    override fun run() {
        while (true) {
            try {
                if (outMessageList.size != 0){

                    outMessageList.forEach { notifyObservers(ClientMessageEvent(this, it)) }

                    synchronized(this){
                        outMessageList = listOf()
                    }
                }

                if (System.currentTimeMillis() - lastPinged > 1000L * Constants.pingTimeoutAfterSeconds / 3){
                    //Ping the server every few seconds, to not get timed out
                    observer.update(ClientMessageEvent(this, Constants.pingString))
                    lastPinged = System.currentTimeMillis()
                }

                Thread.sleep(500L)

            } catch (ex: Exception) {
                error("")
            }
        }
    }

    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.KNOWN_USER_LEFT -> {userListUpdated(event.serverState, UpdateType.LEAVE, user = event.msg ?: "")}
            ServerMessageEvent.Action.USER_CHANGE -> {userListUpdated(event.serverState, UpdateType.JOIN, user = event.msg ?: "")}
            else -> {}
        }
    }

    override fun registerObserver(observer: Observer<ClientMessageEvent>) {
        //Do nothing. The observer is only the server specified in the constructor, and it is unchangeable
    }

    override fun unregisterObserver(observer: Observer<ClientMessageEvent>) {
        //Do nothing. The observer is only the server specified in the constructor, and it is unchangeable
    }

    override fun notifyObservers(event: ClientMessageEvent) {
        observer.update(event)
    }

    enum class UpdateType{JOIN, LEAVE}

    private fun topChattersMessage(state: ChatServerState, type: UpdateType, user: String){
        val topChatters = state.users.filter { it.username != this.username}
                .map {user -> user.username to state.messageHistory.getAll().count { it.user.username == user.username }}
                .sortedByDescending { x -> x.second }
                .take(4)


        val message = topChatters.fold(listOf("Top 4 chatter users:")){s, entry ->
            s + " -- ${entry.first} has ${entry.second} messages"
        }

        outMessageList += message

    }

    private fun userListUpdated(state: ChatServerState, type: UpdateType, user: String){
        val messageCount = state.messageHistory
                .query(null, user, null, null, null)
                .getAll()
                .size

        val actionString = if (type==UpdateType.LEAVE)
            "User $user left the server. They wrote $messageCount messages."
        else
            "User $user is now on the server."

        synchronized(this) {
            outMessageList += actionString
        }

        topChattersMessage(state, type, user)
    }

}