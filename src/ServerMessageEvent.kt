//Author: Manuel Furia
//Student ID: 1706247

/**
 * An event produced by the server, containing an action, the current server state and an optional message
 */
data class ServerMessageEvent (val action: Action, val serverState: ChatServerState, val msg: String? = null){
    enum class Action {
        MESSAGE, PING, USER_JOINED, UNKNOWN_USER_LEFT, KNOWN_USER_LEFT, USER_CHANGE, STOP, TIMEOUT, ERROR
    }
}