//Author: Manuel Furia
//Student ID: 1706247

/* CommandParameters.kt
 * Represents parameters that are passed to a server command handler (an anonymous function) when executed.
 * It contains the argument line of the command (the rest of the line, excluding the command), the user that
 * issued it, the room it is destined for and the clientID of the client of the user that issued it
 */
data class CommandParameters(val argumentLine: String, val server: ChatServerState, val user: ChatUser, val room: ChatRoom, val clientID: Long)
    