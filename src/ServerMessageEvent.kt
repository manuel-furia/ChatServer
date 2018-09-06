data class ServerMessageEvent (val action: Action, val msg: String? = null){
    enum class Action {
        MESSAGE, PING, STOP, TIMEOUT, ERROR
    }
}