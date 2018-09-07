data class ServerMessageEvent (val action: Action, val serverState: ChatServerState, val msg: String? = null){
    enum class Action {
        MESSAGE, PING, USER_JOINED, UNKNOWN_USER_LEFT, KNOWN_USER_LEFT, USER_CHANGE, STOP, TIMEOUT, ERROR
    }
}