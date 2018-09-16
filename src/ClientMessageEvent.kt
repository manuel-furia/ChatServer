//Author: Manuel Furia
//Student ID: 1706247

/*
 * ClientMessageEvent.kt
 * Represents messages sent from client to server
 */

/**
 * Represents messages sent from client to server
 * @param sender The sender of the mesage
 * @param msg The message sent
 * @param stopped Is the client that sent the message stopped or still running?
 */
data class ClientMessageEvent (val sender: Observer<ServerMessageEvent>, val msg: String, val stopped: Boolean = false)