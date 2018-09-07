data class ChatServerState (
        val currentOutput: List<ServerOutput> = listOf(),
        val rooms: Set<ChatRoom> = setOf(ChatRoom(Constants.defaultRoomName)),
        val users: Set<ChatUser> = setOf(),
        val commands:Map<String, (CommandParameters) -> String> = mapOf(),
        val adminCredentials: Map<String, String> = Constants.defaultAdminCredentials,
        val messageHistory: ChatHistory = ChatHistory.empty,
        val bannedIPs: Set<String> = setOf(),
        private val usersAndIds: Bijection<ChatUser, Long> = BijectionMap()) {

    fun registerUser(userName: String, clientHandlerID: Long, permission: ChatUser.Level = ChatUser.Level.NORMAL): ChatServerState {
        return usernameValidateAndInsert(userName, clientHandlerID, permission).second
    }

    private fun usernameValidateAndInsert(userName: String, clientHandlerID: Long, permission: ChatUser.Level = ChatUser.Level.NORMAL): Pair<ChatUser?, ChatServerState>{
        val validUserName = Utils.produceValidUsername(userName)
        val user = ChatUser(validUserName, permission)
        val newUsersAndIDs = usersAndIds + (user to clientHandlerID)

        val userSetMessage = ServerOutput.userSetMessage(user.username, clientHandlerID)
        val userAlreadyExists = ServerOutput.userAlreadyExistsMessage(clientHandlerID)
        val userNameModified = ServerOutput.userNameModifiedMessage(user.username, clientHandlerID)

        val successActions = if (user.username != userName)
            listOf(userNameModified, userSetMessage)
        else if (user.level != ChatUser.Level.UNKNOWN)
            listOf(userSetMessage)
        else
            listOf()

        val failureActions = listOf(userAlreadyExists)

        if (users.any{it.username == user.username}){
            return Pair(null, this.appendOutput(failureActions).copy(users = users, usersAndIds = newUsersAndIDs))
        } else {
            return Pair(user, this.appendOutput(successActions).copy(users = users + user, usersAndIds = newUsersAndIDs)
                    .userJoinRoom(Constants.defaultRoomName, user.username))
        }
    }

    fun becomeAdmin(adminName: String, pass: String, fromUser: ChatUser) : ChatServerState{
        val validUserName = Utils.produceValidUsername(adminName)
        val admin = ChatUser(validUserName, ChatUser.Level.ADMIN)

        val clientID = usersAndIds.direct(fromUser)

        if (clientID != null) {

            if (users.contains(admin)){
                return appendOutput(ServerOutput.adminAlreadyExistsMessage(clientID))
            }

            if (adminCredentials.containsKey(admin.username) && adminCredentials[admin.username] == pass){
                val newRooms = rooms.map {
                    if (it.isUserInRoom(fromUser))
                        it.userLeave(fromUser).userJoin(admin)
                    else
                        it
                }.toSet()
                val message = ServerOutput.userSetMessage(admin.username, clientID)
                val newClientIDToUser = (usersAndIds - (fromUser to clientID)) + (admin to clientID)
                return this.appendOutput(message)
                        .updateRooms(newRooms)
                        .updateUsers((users - fromUser) + admin)
                        .copy(usersAndIds = newClientIDToUser)
            }

            val loginFailed = ServerOutput.adminLoginFailedMessage(clientID)
            return this.appendOutput(loginFailed)
        }

        return this
    }

    fun removeUser(userName: String, noDisconnect: Boolean = false): ChatServerState {

        val user = users.find {it.username == userName}

        if (user != null && user.username != Constants.serverConsoleUsername){
            //Find in which rooms the user belongs to and remove it from there
            val newRooms = rooms.map {it.userLeave(user)}.toSet()
            val clientID = userToClientID(user)
            val stateUpdated = this.updateUsers(users - user).updateRooms(newRooms)
            if (clientID != null && !noDisconnect)
                return stateUpdated.appendOutput(ServerOutput.DropClient(clientID))
            else
                return stateUpdated
        } else {
            return this
        }

    }

    fun changeUsername(userNameFrom: String, userNameTo: String): ChatServerState {
        val user = users.find {it.username == userNameFrom}

        if (user != null){
            val clientID = userToClientID(user)
            if (clientID != null){
                val newLevel = if (user.level == ChatUser.Level.UNKNOWN) ChatUser.Level.NORMAL else user.level
                val (newUser, newState) = usernameValidateAndInsert(userNameTo, clientID, newLevel)
                if (newState.users.size == users.size + 1 && newUser != null){
                    val newRooms = rooms.map {
                        if (it.isUserInRoom(user))
                            it.userLeave(user).userJoin(newUser)
                        else
                            it
                    }.toSet()
                    return newState.updateRooms(newRooms).removeUser(user.username, noDisconnect = true)
                } else {
                    return newState
                }
            }

             return this

        } else {
            return this
        }

    }

    fun addRoom(roomName: String): ChatServerState {
        val validRoomName = Utils.produceValidRoomName(roomName)
        return this.copy(rooms = rooms + ChatRoom(validRoomName))
    }

    fun removeRoom(roomName: String): ChatServerState {
        if (roomName != Constants.defaultRoomName) {
            val room = rooms.find { it.name == roomName }
            if (room != null)
                return this.copy(rooms = rooms - room)
            else
                return this
        }
        return this
    }

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
        val room = rooms.find{ it.name == roomName }
        val user = users.find { it.username == username }

        if (user == null) return this

        val clientID = usersAndIds.direct(user)

        if (clientID == null) return this

        val roomNotExists = ServerOutput.roomDoesNotExistsMessage(roomName, clientID)

        if (room != null) {

            val newRoom = room.userJoin(user)

            val updatedRooms = (rooms - room) + newRoom

            val greetUser = ServerOutput.greetUserWithMessage(room.greeting, clientID)

            val userCannotJoin = ServerOutput.userCannotJoinMessage(clientID)

            if (newRoom.users.size == room.users.size + 1){
                return this.appendOutput(greetUser).copy(rooms = updatedRooms)
            } else {
                return this.appendOutput(userCannotJoin)
            }

        } else {
            return this.appendOutput(listOf(roomNotExists))
        }

    }

    fun userLeaveRoom(roomName: String, username: String): ChatServerState{
        val room = rooms.find { it.name == roomName }
        val user = users.find { it.username == username }

        if (room != null && user != null) {
            val updatedRooms = (rooms - room) + room.userLeave(user)
            return this.copy(rooms = updatedRooms)
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
            .appendOutput(ServerOutput.LiftBan(ip))
            .appendOutput(ServerOutput.ServiceMessageToRoom("Unbanned $ip", Constants.defaultRoomName))

    fun banUserByName(username: String): ChatServerState {
        val user = getUserByUsername(username)
        if (user != null) {
            val id = userToClientID(user)
            if (id != null) {
                val banAction = ServerOutput.BanClient(id)
                val banMessage = ServerOutput.youHaveBeenBannedMessage(id)
                return this.appendOutput(banMessage).appendOutput(banAction)
            }
        }
        return this
    }

    fun processIncomingMessageFromClient(clientID: Long, message: String, userOverride: ChatUser? = null): ChatServerState {
        val user = userOverride ?: (usersAndIds.inverse(clientID) ?: ChatUser("", ChatUser.Level.UNKNOWN))
        val responseCliendID = usersAndIds.direct(user) ?: clientID

        val (room, content) = if (message.startsWith(Constants.roomSelectionPrefix)) {
            val roomName =  message.drop(Constants.roomSelectionPrefix.length).split(" ").getOrNull(0) ?: Constants.defaultRoomName
            val roomObj = rooms.find { it.name == roomName } ?: return appendOutput(ServerOutput.roomDoesNotExistsMessage(roomName, responseCliendID))
            val content = message.drop(Constants.roomSelectionPrefix.length + roomName.length + 1) //Drop the room name plus the space following it
            roomObj to content
        } else {
            val content = message
            val roomObj = rooms.find { it.name == Constants.defaultRoomName } ?: return this.serverErrorTo("Server error: Cannot find default room", clientID)
            roomObj to content
        }

        //Can the user address the specified room? (Only if they joined it, or if its the default server room)
        if (room.name != Constants.defaultRoomName && !room.isUserInRoom(user) && userOverride == null)
            return appendOutput(ServerOutput.userCannotAddressRoomMessage(room.name, responseCliendID))

        val result = Interpreter(content, this, user, room).result

        return result


    }

    fun getUsersInRoom(roomName: String): Set<ChatUser>{
        val room = rooms.find{ it.name == roomName }

        if (room != null){
            return room.filterJoinedUsers(users)
        } else {
            return setOf()
        }
    }

    fun getClientIDsInRoom(roomName: String): Set<Long> = getUsersInRoom(roomName).mapNotNull{userToClientID(it)}.toSet()

    fun serverErrorTo(msg: String, clientID: Long): ChatServerState{
        return this.copy(currentOutput = currentOutput + ServerOutput.ServiceMessageToClient(msg, clientID))
    }


    fun appendOutput(output: ServerOutput): ChatServerState {
        return this.copy(currentOutput = currentOutput + output)
    }

    fun appendOutput(outputs: List<ServerOutput>): ChatServerState {
        return this.copy(currentOutput = currentOutput + outputs)
    }

    fun appendMessageHistory(message: ChatHistory.Entry): ChatServerState {
        return this.copy(messageHistory = messageHistory.addEntry(message))
    }

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



}
