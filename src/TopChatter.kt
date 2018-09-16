//Author: Manuel Furia
//Student ID: 1706247

/*
 * A chat bot that shows the amount of messages written by the users who leave.
 * Also show the top 4 chatters when somebody's message in the main room contains "!topchatter"
 */

class TopChatter(uid: Long, observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>, val username: String):
        ThreadedClient(uid, observer, observable), Runnable {

    //List of output from TopChatter (TopChatter must notify the server on a different thread, to not cause a notification loop)
    private var outMessageList: List<String> = listOf()
    //When we last pinged the server, to not get kicked out by timeout
    private var lastPinged = 0L


    override fun run() {
        super.run()
        while (running) {
            try {
                if (outMessageList.size != 0){
                    //Notify the server of the TopChatter output
                    outMessageList.forEach { notifyObservers(ClientMessageEvent(this, it)) }

                    synchronized(this){
                        outMessageList = listOf()
                    }
                }

                if (System.currentTimeMillis() - lastPinged > 1000L * Constants.pingTimeoutAfterSeconds / 3){
                    //Ping the servers every few seconds, to not get timed out
                    observers.forEach { it.update(ClientMessageEvent(this, Constants.pingString))}
                    lastPinged = System.currentTimeMillis()
                }

                Thread.sleep(100L)

            } catch (ex: Exception) {
                error("")
            }
        }
    }

    override fun update(event: ServerMessageEvent) {
        when (event.action){
            ServerMessageEvent.Action.MESSAGE -> {if ((event.msg ?: "").contains("!topchatter")) topChattersMessage(event.serverState)}
            ServerMessageEvent.Action.KNOWN_USER_LEFT -> {userListUpdated(event.serverState, UpdateType.LEAVE, user = event.msg ?: "")}
            ServerMessageEvent.Action.USER_CHANGE -> {userListUpdated(event.serverState, UpdateType.JOIN, user = event.msg ?: "")}
            ServerMessageEvent.Action.STOP -> {stop()}
            else -> {}
        }
    }

    enum class UpdateType{JOIN, LEAVE}

    private fun topChattersMessage(state: ChatServerState){
        //Extract the 4 top chatters from the message list
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
        //Count the amount of messages that the user that left or joined has writte
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

        //Remove the automatic top 4 chatters printing when a user joins or leave, because it's extremely annoying
        //To show the top 4 users, just write "!topchatter"
        //topChattersMessage(state)
    }

}