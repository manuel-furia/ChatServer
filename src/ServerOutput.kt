//Author: Manuel Furia
//Student ID: 1706247

/* ServerOutput.kt
 * ServerOutput is an algebraic data type (ADT) that can represent a value of different type depending on the kind of
 * server output action required.
 * A list of ServerOutput will be present in the server state and executed at the end of its construction by ChatServerListener.
 * ServerOutput also contains a companion object with utility methods to create the appropriate ServerOutput,
 * with relevant text message output, for different type of events.
 */

sealed class ServerOutput {
    //No action
    object None : ServerOutput()
    //Service message from the server to a specific client.
    data class ServiceMessageToClient(val msg: String, val clientID: Long, val containingParsableInfo: Boolean = false): ServerOutput()
    //Service message from the server to all clients in a specific room
    data class ServiceMessageToRoom(val msg: String, val roomName: String): ServerOutput()
    //Service message from the server to everybody
    data class ServiceMessageToEverybody(val msg: String): ServerOutput()
    //Produced when a user joins the server
    data class UserJoinedNotification(val user: String): ServerOutput()
    //Produced when a user changes their name
    data class UserNameChangedNotification(val user: String): ServerOutput()
    //Produced when an anonymous user leaves the server
    data class KnownUserLeftNotification(val user: String): ServerOutput()
    //Produced when a named user leaves the server
    data class UnknownUserLeftNotification(val user: String): ServerOutput()
    //Drop the specified client
    data class DropClient(val clientID: Long): ServerOutput()
    //Ban the specified client's IP address
    data class BanClient(val clientID: Long): ServerOutput()
    //Unban a banned IP address
    data class LiftBan(val bannedIP: String): ServerOutput()
    //Execute an action (message or command) later (at a specific timestamp)
    data class Schedule(val timestamp: Long, val action: String, val user: ChatUser, val room: ChatRoom): ServerOutput()
    //Send a message from one user to a room
    data class MessageFromUserToRoom(val message: ChatHistory.Entry): ServerOutput()
    //Server pings a client
    data class Ping(val clientID: Long): ServerOutput()
    //User pings another user
    data class PingUser(val fromUser: ChatUser, val toUser: ChatUser): ServerOutput()
    //Disconnect all the clients, terminate all the threads and halt the server
    class Stop(): ServerOutput()

    companion object {

        fun roomYouNeedHigherPermissionsThan(username: String, clientID: Long) = ServiceMessageToClient(
                "You need higher permission than the target user $username to issue this command.",
                clientID)

        fun roomPermissionDenied(permissions: ChatRoom.UserPermissions, requiredPermissions: ChatRoom.UserPermissions, clientID: Long) = ServiceMessageToClient(
                "User permission ${permissions.name} is not enough to issue the command. At least ${requiredPermissions.name} is required.",
                clientID)

        fun permissionDenied(permissions: ChatUser.Level, requiredPermissions: ChatUser.Level, clientID: Long) = ServiceMessageToClient(
                "User level ${permissions.name} is not enough to issue the command. At least level ${requiredPermissions.name} is required."
                        + if (requiredPermissions == ChatUser.Level.NORMAL) " Did you set a username?" else "",
                clientID)

        fun roomPermissionGranted(permissions: ChatRoom.UserPermissions, username: String, roomName: String) = ServiceMessageToRoom(
                "Permissions ${permissions.name} granted to $username.",
                roomName)

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

        fun userPvtDeniedMessage(clientID: Long) = ServiceMessageToClient(
                "Error: You can't send pvt to this user.",
                clientID)

        fun userPvtFailedMessage(clientID: Long) = ServiceMessageToClient(
                "Error: There was a problem in creating the pvt room.",
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

        fun nonAdminCannotScheduleLaterThanMessage(clientID: Long) = ServiceMessageToClient(
                "Error: Only ADMIN users can schedule actions later than ${Constants.maxNonAdminSchedule} seconds from now.",
                clientID)

        fun userCannotAddressRoomMessage(room: String, clientID: Long) = ServiceMessageToClient(
                "Error: you did not join the room ${room}. Use :room ${room}",
                clientID)

        fun youHaveBeenBannedMessage(clientID: Long) = ServiceMessageToClient (
                    "You have been banned.",
                    clientID
            )

        fun kickedFromRoom(username: String, roomName: String) = ServiceMessageToRoom (
                "User $username has been kicked.",
                roomName
        )

        fun youHaveBeenKickedFromRoom(clientID: Long ,roomName: String, reason: String) = ServiceMessageToClient (
                "You have been kicked from room $roomName." +
                if (reason != "") " Reason: $reason." else "",
                clientID
        )

        fun unknownCommand(clientID: Long, command: String) = ServiceMessageToClient (
                "Did not get it $command",
                clientID
        )

        fun serviceMessageTo(clientID: Long, message: String, containingParsableInfo: Boolean = false) = ServiceMessageToClient(message, clientID, containingParsableInfo)


    }

}