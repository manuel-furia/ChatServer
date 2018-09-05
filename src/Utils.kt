object Utils {

    fun produceValidRoomName(name: String): String {
        val filtered = name.split(" ")[0].filter {it.isLetterOrDigit() || it == '_'}

        if (filtered.length <= 0 || filtered[0].isDigit() || filtered == Constants.defaultRoomName)
            return Constants.defaultRoomPrefix + filtered
        else
            return filtered
    }

    fun produceValidUsername(username: String): String {
        val filtered = username.split(" ")[0].filter {it.isLetterOrDigit() || it == '_'}

        if (filtered.length <= 0 || filtered[0].isDigit() || filtered == Constants.serverMessageUserName)
            return Constants.defaultUsernamePrefix + filtered
        else
            return filtered
    }
}