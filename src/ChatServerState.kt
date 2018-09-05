data class ChatServerState (
        val currentOutput: List<ServerOutput> = listOf(),
        val rooms: Set<ChatRoom> = setOf(ChatRoom(Constants.defaultRoomName)),
        val users: Set<ChatUser> = setOf(),
        val commands:Map<String, (CommandParameters) -> String> = mapOf(),
        val adminCredentials: Map<String, String> = mapOf(Constants.defaultAdminUsername to Constants.defaultAdminPassword),
        val messageHistory: ChatHistory = ChatHistory.empty,
        val bannedIPs: Set<String> = setOf(),
        private val usersAndIds: Bijection<ChatUser, Int> = BijectionMap()) {

    //User that represents the server, and send messages on its behalf
    val serverUser = ChatUser(Constants.serverMessageUserName, ChatUser.Level.ADMIN)

    fun registerUser(userName: String, clientHandlerID: Int, permission: ChatUser.Level = ChatUser.Level.NORMAL): ChatServerState {
        return usernameValidateAndInsert(userName, clientHandlerID, permission).second
    }

    private fun usernameValidateAndInsert(userName: String, clientHandlerID: Int, permission: ChatUser.Level = ChatUser.Level.NORMAL): Pair<ChatUser?, ChatServerState>{
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

    fun removeUser(userName: String): ChatServerState {

        val user = users.find {it.username == userName}

        if (user != null){
            //Find in which rooms the user belongs to and remove it from there
            val newRooms = rooms.map {it.userLeave(user)}.toSet()

            return this.updateUsers(users - user).updateRooms(newRooms)
        } else {
            return this.copy(users = users)
        }

    }

    fun changeUsername(userNameFrom: String, userNameTo: String): ChatServerState {
        val user = users.find {it.username == userNameFrom}

        if (user != null){
            val clientID = userToClientID(user)
            if (clientID != null){
                val newLevel = if (user.level == ChatUser.Level.UNKNOWN) ChatUser.Level.NORMAL else user.level
                val (newUser, newState)= usernameValidateAndInsert(userNameTo, clientID, newLevel)
                if (newState.users.size == users.size + 1 && newUser != null){
                    val newRooms = rooms.map {
                        if (it.isUserInRoom(user))
                            it.userLeave(user).userJoin(newUser)
                        else
                            it
                    }.toSet()
                    return newState.updateRooms(newRooms).removeUser(user.username)
                } else {
                    return this
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
            return this.copy(rooms = (rooms - room) + room.setTopic(topic))
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

    fun getUserByUsername(username: String): ChatUser? = users.find { it.username == username }

    fun userToClientID(user: ChatUser): Int? = usersAndIds.direct(user)

    fun addBannedIP(ip: String): ChatServerState = this.copy(bannedIPs = bannedIPs + ip)

    fun liftBan(ip: String): ChatServerState = this.copy(bannedIPs = bannedIPs - ip)

    fun banUserByName(username: String, duration: Int = -1): ChatServerState {
        val user = getUserByUsername(username)
        if (user != null) {
            val id = userToClientID(user)
            if (id != null) {
                val banAction = ServerOutput.BanClient(id, duration)
                val banMessage = ServerOutput.youHaveBeenBannedMessage(id, duration)
                return this.appendOutput(banMessage).appendOutput(banAction)
            }
        }
        return this
    }

    fun processIncomingMessageFromClient(clientID: Int, message: String): ChatServerState {
        val user = usersAndIds.inverse(clientID) ?: ChatUser("", ChatUser.Level.UNKNOWN)

        val (room, content) = if (message.startsWith(Constants.roomSelectionPrefix)) {
            val roomName =  message.drop(Constants.roomSelectionPrefix.length).split(" ").getOrNull(0) ?: Constants.defaultRoomName
            val roomObj = rooms.find { it.name == roomName } ?: return appendOutput(ServerOutput.roomDoesNotExistsMessage(roomName, clientID))
            val content = message.drop(Constants.roomSelectionPrefix.length + roomName.length + 1) //Drop the room name plus the space following it
            roomObj to content
        } else {
            val content = message
            val roomObj = rooms.find { it.name == Constants.defaultRoomName } ?: return this.serverErrorTo("Server error: Cannot find default room\n", clientID)
            roomObj to content
        }

        //Can the user address the specified room? (Only if they joined it, or if its the default server room)
        if (room.name != Constants.defaultRoomName && !room.isUserInRoom(user))
            return appendOutput(ServerOutput.userCannotAddressRoomMessage(room.name, clientID))

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

    fun getClientIDsInRoom(roomName: String): Set<Int> = getUsersInRoom(roomName).mapNotNull{userToClientID(it)}.toSet()

    fun serverErrorTo(msg: String, clientID: Int): ChatServerState{
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



}
