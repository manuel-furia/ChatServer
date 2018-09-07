object Constants {


    val defaultAdminCredentials = mapOf("admin" to "password")
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
    const val pingTimeoutAfterSeconds = 60
    const val serverMessagePrefix = ":-"
    const val pingString = ":PING:"
    const val defaultQueryMinutesAgo = 10

}