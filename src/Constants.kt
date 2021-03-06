//Author: Manuel Furia
//Student ID: 1706247

/* Constants.kt
 * Singleton containing constants used in the server
 */

object Constants {


    val defaultAdminCredentials = mapOf("admin" to "password")
    val pluginFolder = "plugins"
    const val serverConsoleUsername = "server"
    const val mainRoomName = "hall"
    const val defaultUsernamePrefix = "user_"
    const val unknownUsernamePrefix = "unknown_"
    const val defaultRoomPrefix = "room_"
    const val pvtRoomUsernameSeperator = '.'
    val commandPrefixes = listOf(":")
    const val roomSelectionPrefix = "@"
    const val maxUserNameLength = 10
    const val maxRoomNameLength = (maxUserNameLength * 2) + 1
    const val roomAlignmentPadding = 8
    const val maxNonAdminSchedule = 60
    const val pingTimeoutCheckEverySeconds = 10
    const val pingTimeoutAfterSeconds = 600
    const val serverMessagePrefix = ":-"
    const val serverParsableMessagePrefix = ":="
    const val pingString = ":PING:"
    const val defaultQueryMinutesAgo = 10

}