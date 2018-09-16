//Author: Manuel Furia
//Student ID: 1706247

/* ChatServerState.kt
 * Immutable class that represents the state of the server. Each method that could modify the state will produce
 * a new state instead. It keeps track of users, rooms and message history.
 * Each state has associated a currentOutput, representing the list of output action that the creation of that
 * state produced. The list of output actions will be executed by ChatServerListener.
 */

/**
 * Represents the state of a server (users, rooms, message history, admin credentials, current output, banned IP addresses).
 */
data class ChatServerState (
        //List of output actions (to be executed) tied to the creation of this server state
        val currentOutput: List<ServerOutput> = listOf(),
        val rooms: Set<ChatRoom> = setOf(ChatRoom(Constants.mainRoomName)),
        val users: Set<ChatUser> = setOf(),
        //Map of commands the users can issue. Each command has a name associated with an anonymous function containing the action.
        val commands:Map<String, (CommandParameters) -> ChatServerState> = mapOf(),
        //Map containing usernames and passwords of admin users
        val adminCredentials: Map<String, String> = Constants.defaultAdminCredentials,
        //Contains the history of all the messages received by the server
        val messageHistory: ChatHistory = ChatHistory.empty,
        //IP addresses that can not connect to the server
        val bannedIPs: Set<String> = setOf(),
        //Bijection map between users and their client id
        //(each user has a one and only one client id, each client id belongs to one and only one user)
        private val usersAndIds: Bijection<ChatUser, Long> = BijectionMap()) {

    /**
     * Set the list of commands available for the users
     */
    fun setCommands(commands:Map<String, (CommandParameters) -> ChatServerState>): ChatServerState {
        return this.copy(commands = commands)
    }

    fun registerUser(userName: String, clientHandlerID: Long, permission: ChatUser.Level = ChatUser.Level.NORMAL): ChatServerState {
        return usernameValidateAndInsert(userName, clientHandlerID, permission).second
    }

    /**
     * Check the validity an uniqueness of chosen username.
     * If it is valid and unique, create a user and add it to the user list. If it is not valid, adapt it to be valid.
     * If it is duplicate, report the failure.
     *
     * @return A pair containing the newly created user and the update server state containing the new user.
     */
    private fun usernameValidateAndInsert(userName: String, clientHandlerID: Long, permission: ChatUser.Level = ChatUser.Level.NORMAL): Pair<ChatUser?, ChatServerState>{
        //If the username is not valid, adapt it to be valid
        val validUserName = produceValidUsername(userName)
        //Create the new user
        val user = ChatUser(validUserName, permission)
        //Create the new users <-> id map
        val newUsersAndIDs = usersAndIds + (user to clientHandlerID)

        //Possible outputs to the client that issued the :user command
        val userSetMessage = ServerOutput.userSetMessage(user.username, clientHandlerID)
        val userAlreadyExists = ServerOutput.userAlreadyExistsMessage(clientHandlerID)
        val userNameModified = ServerOutput.userNameModifiedMessage(user.username, clientHandlerID)

        //Find out which outputs to show in case of success
        val successActions = if (user.username != userName)
            listOf(userNameModified, userSetMessage)
        else if (user.level != ChatUser.Level.UNKNOWN)
            listOf(userSetMessage)
        else
            listOf()

        //In case of failure (duplicate), we want to show the "user already exists" output to the client
        val failureActions = listOf(userAlreadyExists)

        //If there is a user with the same username...
        if (users.any{it.username == user.username}){
            //...show the failure message
            return Pair(null, this.appendOutput(failureActions))
        } else {
            //Create a server state containing the notification that a user has joined or changed name
            val userJoinedEvent = if (user.level > ChatUser.Level.UNKNOWN){
                this.appendOutput(ServerOutput.UserNameChangedNotification(user.username))
            } else {
                this.appendOutput(ServerOutput.UserJoinedNotification(user.username))
            }

            //Append all the action and modifications to the userJoinedEvent server state, and return it, together with
            //the newly created user
            return Pair(user, userJoinedEvent
                    .appendOutput(successActions) //Show the output for the successful action
                    .copy(users = users + user, usersAndIds = newUsersAndIDs) //Add the user and the client id to the state
                    .userJoinRoom(Constants.mainRoomName, user.username)) //Add the user to the main room of the server

        }
    }

    /**
     * Try to login as admin
     */
    fun becomeAdmin(adminName: String, pass: String, fromUser: ChatUser) : ChatServerState {
        //Validate the username of the admin
        val validUserName = produceValidUsername(adminName)
        //Create a user representing the admin
        val admin = ChatUser(validUserName, ChatUser.Level.ADMIN)
        //Fetch the client id of the user that is trying to login as admin
        val clientID = usersAndIds.direct(fromUser)
        //If the client exists
        if (clientID != null) {
            //Check if the admin with this username is already logged in
            if (users.contains(admin)){
                return appendOutput(ServerOutput.adminAlreadyExistsMessage(clientID))
            }
            //Check if username and password are correct
            if (adminCredentials.containsKey(admin.username) && adminCredentials[admin.username] == pass){
                //Transform the user to admin in all the rooms in which the user belongs
                val newRooms = rooms.map {
                    if (it.isUserInRoom(fromUser))
                        it.userLeave(fromUser).userJoin(admin)
                    else
                        it
                }.toSet()
                //The message showing that a new user is present in the server
                val message = ServerOutput.userSetMessage(admin.username, clientID)
                //Associate the clientID to the newly created admin user
                val newClientIDToUser = (usersAndIds - (fromUser to clientID)) + (admin to clientID)

                return this //Return the new server state
                        .appendOutput(message)
                        .updateRooms(newRooms)
                        .updateUsers((users - fromUser) + admin)
                        .copy(usersAndIds = newClientIDToUser)
            }
            //Output message reporting to the client that the login as admin failed
            val loginFailed = ServerOutput.adminLoginFailedMessage(clientID)
            return this.appendOutput(loginFailed)
        }

        return this
    }

    /**
     * Removes a user from the server
     * @param noDisconnect if true, do not order to disconnect the client when the user is removed, for example if a user is just changing name
     */
    fun removeUser(userName: String, noDisconnect: Boolean = false): ChatServerState {
        //Find the user to remove
        val user = users.find {it.username == userName}
        //If the user is found and it's not the server console (since we can't remove the server console)
        if (user != null && user.username != Constants.serverConsoleUsername){
            //Find in which rooms the user belongs to and remove it from there
            val newRooms = rooms.map {it.userLeave(user)}.toSet()
            val clientID = userToClientID(user)

            //Create the new server state with the user removed
            val stateUpdated = this
                    .updateUsers(users - user)
                    .updateRooms(newRooms)
                    .appendOutput(//Send the clients a different type of notification depending if the user was anonymous or known.
                        if (user.level == ChatUser.Level.UNKNOWN)
                            ServerOutput.UnknownUserLeftNotification(user.username)
                        else
                            ServerOutput.KnownUserLeftNotification(user.username)
                        )

            if (clientID != null && !noDisconnect)
                return stateUpdated.appendOutput(ServerOutput.DropClient(clientID)) //Order to drop the client if necessary
            else
                return stateUpdated
        } else {
            return this
        }

    }

    fun changeUsername(userNameFrom: String, userNameTo: String): ChatServerState {
        //Find the user by it's name
        val user = users.find {it.username == userNameFrom}

        if (user != null){ //If user found
            if (user.username == Constants.serverConsoleUsername) return this //The server console can not change username
            val clientID = userToClientID(user)
            if (clientID != null){ //If id of the user's client found
                //The upgraded user level (from UNKNOWN to NORMAL)
                val newLevel = if (user.level == ChatUser.Level.UNKNOWN) ChatUser.Level.NORMAL else user.level
                //The newly inserted user with the new state
                val (newUser, newState) = usernameValidateAndInsert(userNameTo, clientID, newLevel)
                //If a user has been added
                if (newState.users.size == users.size + 1 && newUser != null){
                    //Update the user in all the rooms they belong to
                    val newRooms = rooms.map {
                        if (it.isUserInRoom(user))
                            it.userLeave(user).userJoin(newUser)
                        else
                            it
                    }.toSet()
                    //Return state with the updated user added, and remove the old user (whitout disconnecting
                    //the client, as it is now connected to the updated user)
                    return newState.updateRooms(newRooms).removeUser(user.username, noDisconnect = true)
                } else { //No user added, newState has not been updated except for possible error messages
                    return newState
                }
            } else { //User does not have a client, return the state unchanged
                return this
            }
        } else { //User does not exists, return the state unchanged
            return this
        }

    }

    fun addRoom(roomName: String): ChatServerState {
        val validRoomName = produceValidRoomName(roomName)
        return this.copy(rooms = rooms + ChatRoom(validRoomName))
    }

    fun removeRoom(roomName: String): ChatServerState {
        if (roomName != Constants.mainRoomName) {
            val room = rooms.find { it.name == roomName }
            if (room != null)
                return this.copy(rooms = rooms - room)
            else
                return this
        }
        return this
    }

    /**
     * Sets the greeting message shown to a user when they join a room
     */
    fun setRoomTopic(roomName: String, topic: String): ChatServerState {
        val room = rooms.find { it.name == roomName }

        if (room != null)
            return this.updateRoom(room, room.setTopic(topic))
        else
            return this
    }

    fun setUserPermissionInRoom(roomName: String, username: String, permission: ChatRoom.UserPermissions): ChatServerState {
        val room = rooms.find { it.name == roomName }
        val user = getUserByUsername(username)

        if (room != null && user != null)
            return this.updateRoom(room, room.setPermissions(user, permission))
        else
            return this
    }

    fun userJoinRoom(roomName: String, username: String): ChatServerState {
        //Find the room and the user
        val room = rooms.find{ it.name == roomName }
        val user = users.find { it.username == username }
        if (user == null) return this //If the user does not exists, return the server state unaltered
        val clientID = usersAndIds.direct(user)
        if (clientID == null) return this //If the user does not have a client associated with, return the server state unaltered
        //The output to be shown if the room does not exists
        val roomNotExists = ServerOutput.roomDoesNotExistsMessage(roomName, clientID)

        if (room != null) { //If the room exists
            //Create the updated room (including the newly joined user)
            val newRoom = room.userJoin(user)
            //Create the updated list of room (removing the old version and adding the updated version of the room)
            val updatedRooms = (rooms - room) + newRoom
            //Welcome message to be shown when the user joins the room (the message is set via method setTopic)
            val greetUser = ServerOutput.greetUserWithMessage(room.greeting, clientID)
            //Message to show in case the user is not allowed to join the room (because of blacklist or whitelist)
            val userCannotJoin = ServerOutput.userCannotJoinMessage(clientID)
            //If the new room has been added
            if (newRoom.users.size == room.users.size + 1){
                return this.appendOutput(greetUser).copy(rooms = updatedRooms) //Return the updated server state
            } else {
                return this.appendOutput(userCannotJoin) //Return the state showing the failure message
            }

        } else {
            return this.appendOutput(roomNotExists) //The room does not exist
        }

    }

    /**
     * Remove a user from a room
     */
    fun userLeaveRoom(roomName: String, username: String): ChatServerState{
        //Find the user and the room
        val room = rooms.find { it.name == roomName }
        val user = users.find { it.username == username }

        if (room != null && user != null) { //If they exists
            //Update the room list by removing the old room and adding
            //a new version of the same room where the user is removed
            val updatedRooms = (rooms - room) + room.userLeave(user)
            return this.copy(rooms = updatedRooms) //Return a server state with the updated room list
        } else {
            return this
        }
    }

    fun getRoomsByUsername(username: String): Set<ChatRoom> = rooms.filter { it.isUsernameInRoom(username) }.toSet()

    fun getRoomsByUser(user: ChatUser): Set<ChatRoom> = rooms.filter { it.isUserInRoom(user) }.toSet()

    fun getRoomByName(name: String): ChatRoom? = rooms.find { it.name == name }

    fun getUserByUsername(username: String): ChatUser? = users.find { it.username == username }

    fun userToClientID(user: ChatUser): Long? = usersAndIds.direct(user)

    fun clientIDToUser(user: Long): ChatUser? = usersAndIds.inverse(user)

    fun addBannedIP(ip: String): ChatServerState = this.copy(bannedIPs = bannedIPs + ip)

    fun liftBan(ip: String): ChatServerState = this
            .appendOutput(ServerOutput.LiftBan(ip)) //Command the ChatServerListener to start accepting the ip again
            .appendOutput(ServerOutput.ServiceMessageToRoom("Unbanned $ip", Constants.mainRoomName))

    fun banUserByName(username: String): ChatServerState {
        val user = getUserByUsername(username)
        if (user != null) {
            val id = userToClientID(user)
            if (id != null) {
                val banAction = ServerOutput.BanClient(id) //Command the ChatServerListener to stop accepting the ip
                val banMessage = ServerOutput.youHaveBeenBannedMessage(id)
                return this.appendOutput(banMessage).appendOutput(banAction)
            }
        }
        return this
    }

    /**
     * Pass an incoming message to the command interpreter to execute commands or add messages to the chat history
     * @param clientID The client that issued the message
     * @param message The message as string
     * @param userOverride Replace the sender (usually determined by clientID) with this user. Useful when an action has
     *                     been issued by a user not connected to a client anymore (i.e. scheduled action),
     *                     so the user is known but not its clientID.
     */
    fun processIncomingMessageFromClient(clientID: Long, message: String, userOverride: ChatUser? = null): ChatServerState {
        //If we want override the clientID, use userOverride, otherwise fetch the user associated with the clientID
        val user = userOverride ?: (usersAndIds.inverse(clientID) ?: ChatUser("", ChatUser.Level.UNKNOWN))
        //Fetch again the client id (might be different than the specified one in case od userOverride != null)
        val responseClientID = usersAndIds.direct(user) ?: clientID
        //Find the room to which the message is addressed
        val (room, content) = if (message.startsWith(Constants.roomSelectionPrefix)) { //The room is speficied in the message
            val roomName =  message
                    .drop(Constants.roomSelectionPrefix.length)
                    .split(" ")
                    .getOrNull(0) ?: Constants.mainRoomName
            //Fetch the room object if found, otherwise return a new server state with an error message
            val roomObj = rooms.find { it.name == roomName } ?: return appendOutput(ServerOutput.roomDoesNotExistsMessage(roomName, responseClientID))
            val content = message.drop(Constants.roomSelectionPrefix.length + roomName.length + 1) //Drop the room name plus the space following it
            roomObj to content
        } else { //There is no room specified, assume the message goes to the main room
            val content = message
            //Fetch the room object for the server's main room if found, otherwise return a new server state with an internal error
            val roomObj = rooms.find { it.name == Constants.mainRoomName } ?: return this.serverErrorTo("Server error: Cannot find default room", clientID)
            roomObj to content
        }

        //Can the user address the specified room? (Only if they joined it, or if its the default server room)
        if (room.name != Constants.mainRoomName && !room.isUserInRoom(user) && userOverride == null)
            return appendOutput(ServerOutput.userCannotAddressRoomMessage(room.name, responseClientID))
        //Interpret the message and get the resulting server state
        val result = Interpreter(content, this, user, room, commands).result
        //Return the resulting server state
        return result


    }

    /**
     * Get the set of users that are members of a room
     */
    fun getUsersInRoom(roomName: String): Set<ChatUser>{
        val room = rooms.find{ it.name == roomName }

        if (room != null){
            return room.filterJoinedUsers(users)
        } else {
            return setOf()
        }
    }

    /**
     * Get the set of client ids associated with the users in a room
     */
    fun getClientIDsInRoom(roomName: String): Set<Long> = getUsersInRoom(roomName).mapNotNull{userToClientID(it)}.toSet()

    fun serverErrorTo(msg: String, clientID: Long): ChatServerState{
        return this.copy(currentOutput = currentOutput + ServerOutput.ServiceMessageToClient(msg, clientID))
    }

    /**
     * Append an output action to this server state
     */
    fun appendOutput(output: ServerOutput): ChatServerState {
        return this.copy(currentOutput = currentOutput + output)
    }

    fun appendOutput(outputs: List<ServerOutput>): ChatServerState {
        return this.copy(currentOutput = currentOutput + outputs)
    }

    /**
     * Appen a message to the message history
     */
    fun appendMessageToHistory(message: ChatHistory.Entry): ChatServerState {
        return this.copy(messageHistory = messageHistory.addEntry(message))
    }

    /**
     * Replace the current state output actions with a different list of them
     */
    fun updateOutput(output: List<ServerOutput>): ChatServerState {
        return this.copy(currentOutput = output)
    }

    fun updateRooms(rooms: Set<ChatRoom>): ChatServerState {
        return this.copy(rooms = rooms)
    }

    fun updateUsers(users: Set<ChatUser>): ChatServerState {
        return this.copy(users = users)
    }

    fun updateRoom(room: ChatRoom, newRoom: ChatRoom): ChatServerState {
        return this.copy(rooms = (rooms - room) + newRoom)
    }

    fun updateUser(user: ChatUser, newUser: ChatUser): ChatServerState {
        val newRooms = rooms.map {it.userLeave(user)}.toSet()

        return this.copy(users = (users - user) + newUser, rooms = newRooms)
    }

    /**
     * Produce a valid room name by removing invalid characters and cutting if too long
     */
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

    /**
     * Produce a valid user name by removing invalid characters and cutting if too long
     */
    fun produceValidUsername(username: String): String {
        val filtered = username.split(" ")[0].filter {it.isLetterOrDigit() || it == '_'}.take(Constants.maxRoomNameLength)

        if (filtered.length <= 0 || filtered[0].isDigit())
            return Constants.defaultUsernamePrefix + filtered
        else
            return filtered
    }



}
