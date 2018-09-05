sealed class ServerOutput {

    object None : ServerOutput()

    class ServiceMessageToClient(val msg: String, val clientID: Long): ServerOutput()

    class DropClient(val clientID: Long): ServerOutput()

    class BanClient(val clientID: Long, val duration: Int): ServerOutput()

    class LiftBan(val bannedIP: String): ServerOutput()

    class MessageFromUserToRoom(val message: ChatHistory.Entry): ServerOutput()

    class Ping(val clientID: Long): ServerOutput()

    companion object {

        fun permissionDenied(permissions: ChatUser.Level, requiredPermissions: ChatUser.Level, clientID: Long) = ServiceMessageToClient(
                "User level ${permissions.name} is not enough to issue the command. At least level ${requiredPermissions.name} is required."
                        + if (requiredPermissions == ChatUser.Level.NORMAL) " Did you set a username?" else ""
                        +"\n",
                clientID)

        fun usernameNotSet(clientID: Long) = ServiceMessageToClient(
                "User name not set. Use command :user to set it.\n",
                clientID)

        fun usernameNotSpecifiedWhenSetting(clientID: Long) = ServiceMessageToClient(
                "User name not set: no username specified..\n",
                clientID)

        fun userSetMessage(username: String, clientID: Long) = ServiceMessageToClient(
                    "User set to $username.\n",
                    clientID)

        fun userAlreadyExistsMessage(clientID: Long) = ServiceMessageToClient(
                    "Error: User already exists.\n",
                    clientID)

        fun adminAlreadyExistsMessage(clientID: Long) = ServiceMessageToClient(
                "Error: This admin account is already logged in.\n",
                clientID)

        fun userNameModifiedMessage(username: String, clientID: Long) = ServiceMessageToClient(
                    "To username you inserted contained invalid characters, so it was modified to $username.\n",
                    clientID)

        fun adminLoginFailedMessage(clientID: Long) = ServiceMessageToClient(
                    "Admin login failed.\nUse :admin name password to login as server admin.\n",
                    clientID)

        fun roomDoesNotExistsMessage(roomName: String, clientID: Long) = ServiceMessageToClient(
                    "Error: Room $roomName does not exists\n",
                    clientID)

        fun greetUserWithMessage(greeting: String, clientID: Long) =
                    if (greeting == "")
                        None
                    else ServiceMessageToClient(
                            greeting+"\n",
                            clientID)

        fun userCannotJoinMessage(clientID: Long) = ServiceMessageToClient(
                    "Error: User can not join the room because already joined or lacking permissions.\n",
                    clientID)

        fun userCannotAddressRoomMessage(room: String, clientID: Long) = ServiceMessageToClient(
                "Error: you did not join the room ${room}. Use :room ${room}\n",
                clientID)

        fun youHaveBeenBannedMessage(clientID: Long, duration: Int) = ServiceMessageToClient (
                    "You have been banned" + if (duration < 0) "." else " for $duration minutes.",
                    clientID
            )

        fun unknownCommand(clientID: Long, command: String) = ServiceMessageToClient (
                "Did not get it $command\n",
                clientID
        )

        fun serviceMessageTo(clientID: Long, message: String) = ServiceMessageToClient(message, clientID)


    }

}