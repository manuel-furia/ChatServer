sealed class ServerOutput {

    object None : ServerOutput()

    class ServiceMessageToClient(val msg: String, val clientID: Int): ServerOutput()

    class DropClient(val clientID: Int): ServerOutput()

    class BanClient(val clientID: Int, val duration: Int): ServerOutput()

    class LiftBan(val bannedIP: String): ServerOutput()

    class MessageFromUserToRoom(val message: ChatHistory.Entry): ServerOutput()

    class Ping(val clientID: Int): ServerOutput()

    companion object {

            fun usernameNotSet(clientID: Int) = ServiceMessageToClient(
                "User name not set. Use command :user to set it.\n",
                clientID)

            fun usernameNotSpecifiedWhenSetting(clientID: Int) = ServiceMessageToClient(
                "User name not set: no username specified..\n",
                clientID)

            fun userSetMessage(username: String, clientID: Int) = ServiceMessageToClient(
                    "User set to $username.\n",
                    clientID)

            fun userAlreadyExistsMessage(clientID: Int) = ServiceMessageToClient(
                    "Error: User already exists.\n",
                    clientID)

        fun adminAlreadyExistsMessage(clientID: Int) = ServiceMessageToClient(
                "Error: This admin account is already logged in.\n",
                clientID)

            fun userNameModifiedMessage(username: String, clientID: Int) = ServiceMessageToClient(
                    "To username you inserted contained invalid characters, so it was modified to $username.\n",
                    clientID)

            fun adminLoginFailedMessage(clientID: Int) = ServiceMessageToClient(
                    "Admin login failed.\nUse :admin name password to login as server admin.\n",
                    clientID)

            fun roomDoesNotExistsMessage(roomName: String, clientID: Int) = ServiceMessageToClient(
                    "Error: Room $roomName does not exists\n",
                    clientID)

            fun greetUserWithMessage(greeting: String, clientID: Int) =
                    if (greeting == "")
                        None
                    else ServiceMessageToClient(
                            greeting+"\n",
                            clientID)

            fun userCannotJoinMessage(clientID: Int) = ServiceMessageToClient(
                    "Error: User can not join the room because already joined or lacking permissions.\n",
                    clientID)

        fun userCannotAddressRoomMessage(room: String, clientID: Int) = ServiceMessageToClient(
                "Error: jou did not join the room ${room}. Use :room ${room}\n",
                clientID)

            fun youHaveBeenBannedMessage(clientID: Int, duration: Int) = ServiceMessageToClient (
                    "You have been banned from the server" + if (duration < 0) "." else " for $duration minutes.",
                    clientID
            )

        fun unknownCommand(clientID: Int, command: String) = ServiceMessageToClient (
                "Did not get it $command\n",
                clientID
        )

        fun serviceMessageTo(clientID: Int, message: String) = ServiceMessageToClient(message, clientID)


    }

}