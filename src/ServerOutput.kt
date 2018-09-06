sealed class ServerOutput {

    object None : ServerOutput()

    class ServiceMessageToClient(val msg: String, val clientID: Long): ServerOutput()

    class ServiceMessageToRoom(val msg: String, val roomName: String): ServerOutput()

    class ServiceMessageToEverybody(val msg: String): ServerOutput()

    class DropClient(val clientID: Long): ServerOutput()

    class BanClient(val clientID: Long, val duration: Int): ServerOutput()

    class LiftBan(val bannedIP: String): ServerOutput()

    class MessageFromUserToRoom(val message: ChatHistory.Entry): ServerOutput()

    class Ping(val clientID: Long): ServerOutput()

    companion object {

        fun roomPermissionDenied(permissions: ChatRoom.UserPermissions, requiredPermissions: ChatRoom.UserPermissions, clientID: Long) = ServiceMessageToClient(
                "User permission ${permissions.name} is not enough to issue the command. At least ${requiredPermissions.name} is required.",
                clientID)

        fun permissionDenied(permissions: ChatUser.Level, requiredPermissions: ChatUser.Level, clientID: Long) = ServiceMessageToClient(
                "User level ${permissions.name} is not enough to issue the command. At least level ${requiredPermissions.name} is required."
                        + if (requiredPermissions == ChatUser.Level.NORMAL) " Did you set a username?" else "",
                clientID)

        fun usernameNotSet(clientID: Long) = ServiceMessageToClient(
                "User name not set. Use command :user to set it.",
                clientID)

        fun usernameNotSpecifiedWhenSetting(clientID: Long) = ServiceMessageToClient(
                "User name not set: no username specified.",
                clientID)

        fun userSetMessage(username: String, clientID: Long) = ServiceMessageToClient(
                    "User set to $username.",
                    clientID)

        fun userJoinedRoomMessage(username: String, roomName: String) = ServiceMessageToRoom(
                "User $username joined the room.",
                roomName)

        fun userLeftRoomMessage(username: String, roomName: String) = ServiceMessageToRoom(
                "User $username joined left the room.",
                roomName)

        fun userInRoomChangedUsernameMessage(username: String, newUsername: String, roomName: String) = ServiceMessageToRoom(
                "User $username is now called $newUsername.",
                roomName)

        fun userAlreadyExistsMessage(clientID: Long) = ServiceMessageToClient(
                    "Error: User already exists.",
                    clientID)

        fun youCantLeaveTheMainRoomMessage(clientID: Long) = ServiceMessageToClient(
                "Error: You can't leave the main room.",
                clientID)

        fun youCantKickUsersFromTheMainRoomMessage(clientID: Long) = ServiceMessageToClient(
                "Error: You can't leave the main room.",
                clientID)

        fun userDoesNotExistsMessage(clientID: Long) = ServiceMessageToClient(
                "Error: User does not exists.",
                clientID)

        fun adminAlreadyExistsMessage(clientID: Long) = ServiceMessageToClient(
                "Error: This admin account is already logged in.",
                clientID)

        fun userNameModifiedMessage(username: String, clientID: Long) = ServiceMessageToClient(
                    "To username you inserted contained invalid characters, so it was modified to $username.",
                    clientID)

        fun adminLoginFailedMessage(clientID: Long) = ServiceMessageToClient(
                    "Admin login failed.\nUse :admin name password to login as server admin.",
                    clientID)

        fun roomDoesNotExistsMessage(roomName: String, clientID: Long) = ServiceMessageToClient(
                    "Error: Room $roomName does not exists",
                    clientID)

        fun greetUserWithMessage(greeting: String, clientID: Long) =
                    if (greeting == "")
                        None
                    else ServiceMessageToClient(
                            greeting,
                            clientID)

        fun userCannotJoinMessage(clientID: Long) = ServiceMessageToClient(
                    "Error: User can not join the room because already joined or lacking permissions.",
                    clientID)

        fun userCannotAddressRoomMessage(room: String, clientID: Long) = ServiceMessageToClient(
                "Error: you did not join the room ${room}. Use :room ${room}",
                clientID)

        fun youHaveBeenBannedMessage(clientID: Long, duration: Int) = ServiceMessageToClient (
                    "You have been banned" + if (duration < 0) "." else " for $duration minutes.",
                    clientID
            )

        fun unknownCommand(clientID: Long, command: String) = ServiceMessageToClient (
                "Did not get it $command",
                clientID
        )

        fun serviceMessageTo(clientID: Long, message: String) = ServiceMessageToClient(message, clientID)


    }

}