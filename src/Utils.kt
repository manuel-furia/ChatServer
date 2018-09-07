object Utils {

    fun produceValidRoomName(name: String): String {
        val filtered = name
                .split(" ")[0]
                .filter {it.isLetterOrDigit() || it == '_' || it == Constants.pvtRoomUsernameSeperator}
                .take(Constants.maxUserNameLength)

        if (filtered.length <= 0 || filtered[0].isDigit() || filtered == Constants.mainRoomName)
            return Constants.defaultRoomPrefix + filtered
        else
            return filtered
    }

    fun produceValidUsername(username: String): String {
        val filtered = username.split(" ")[0].filter {it.isLetterOrDigit() || it == '_'}.take(Constants.maxRoomNameLength)

        if (filtered.length <= 0 || filtered[0].isDigit())
            return Constants.defaultUsernamePrefix + filtered
        else
            return filtered
    }
}