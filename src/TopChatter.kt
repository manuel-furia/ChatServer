class TopChatter(observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>): Observer<ServerMessageEvent>, Observable<ClientMessageEvent>, Runnable {
    private val observer: Observer<ClientMessageEvent> = observer

    val uid = 0L

    override fun run() {
        while (true) {
            try {
                //Ping the server every few seconds, to not get timed out
                observer.update(ClientMessageEvent(this, Constants.pingString, true))
                Thread.sleep(1000L * Constants.pingTimeoutAfterSeconds / 3)
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
        val topChatters = state.users
                .map {user -> user.username to state.messageHistory.getAll().count { it.user.username == user.username }}
                .sortedByDescending { x -> x.second }
                .take(4)


        val message = topChatters.fold(listOf("Top 4 chatter users:")){s, entry ->
            s + " -- ${entry.first} has ${entry.second} messages"
        }

        message.forEach { notifyObservers(ClientMessageEvent(this, it, true)) }

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

        notifyObservers(ClientMessageEvent(this, actionString, true))

        topChattersMessage(state, type, user)
    }

}