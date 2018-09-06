object Commands {

    const val textMessage = ""

    val basicCommands: Map<String, (CommandParameters) -> ChatServerState> = mapOf (

            textMessage to {params ->

                if (params.user.level <= ChatUser.Level.UNKNOWN){
                    val output = ServerOutput.usernameNotSet(params.clientID)
                   params.server.copy(currentOutput = params.server.currentOutput + output)
                } else {

                    val timestamp = System.currentTimeMillis()

                    val historyEntry = ChatHistory.Entry(params.argumentLine, params.user, params.room, timestamp)

                    val newHistory = params.server.messageHistory.addEntry(historyEntry)

                    val output = ServerOutput.MessageFromUserToRoom(historyEntry)

                    params.server.copy(currentOutput = listOf(output), messageHistory = newHistory)
                }
            },

            ":user" to {params ->

                val newUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                if (newUsername == ""){
                    params.server.appendOutput(ServerOutput.usernameNotSpecifiedWhenSetting(params.clientID))
                } else {

                    val rooms = params.server.getRoomsByUser(params.user)

                    val output = rooms.fold(listOf<ServerOutput>()) { out, room ->
                        out + ServerOutput.userInRoomChangedUsernameMessage(params.user.username, newUsername, room.name)
                    }

                    params.server.changeUsername(params.user.username, newUsername).appendOutput(output)
                }
            },

            ":admin" to {params ->

                val adminUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                val adminPassword = params.argumentLine.trim().split(" ").getOrNull(1)?.trim() ?: ""

                params.server.becomeAdmin(adminUsername, adminPassword, params.user)
            },

            ":room" to {params ->

                if (params.user.level < ChatUser.Level.NORMAL) {

                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))

                } else {

                    val roomName = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                    val isUserAlreadyInside = params.server.getUsersInRoom(roomName).contains(params.user)
                    val message = if (isUserAlreadyInside)
                        ServerOutput.None
                    else
                        ServerOutput.userJoinedRoomMessage(params.user.username, roomName)

                    params.server.addRoom(roomName).userJoinRoom(roomName, params.user.username).appendOutput(message)
                }
            },

            ":leave" to {params ->

                if (params.room.name == Constants.defaultRoomName){
                    params.server.appendOutput(ServerOutput.youCantLeaveTheMainRoomMessage(params.clientID))
                } else {

                    val output = ServerOutput.userLeftRoomMessage(params.user.username, params.room.name)

                    params.server.userLeaveRoom(params.room.name, params.user.username).appendOutput(output)
                }
            },

            ":kick" to {params ->

                if (params.room.name == Constants.defaultRoomName){
                    params.server.appendOutput(ServerOutput.youCantKickUsersFromTheMainRoomMessage(params.clientID))
                } else {
                    params.server.userLeaveRoom(params.room.name, params.user.username)
                }
            },

            ":KICK" to {params ->

                if (params.room.name == Constants.defaultRoomName){
                    params.server.appendOutput(ServerOutput.youCantLeaveTheMainRoomMessage(params.clientID))
                } else {
                    params.server.userLeaveRoom(params.room.name, params.user.username)
                }
            },

            ":topic" to {params ->

                val topic = params.argumentLine.trim()

                params.server.setRoomTopic(params.room.name, topic)
            },

            ":quit" to { params ->

                params.server.removeUser(params.user.username).appendOutput(ServerOutput.DropClient(params.clientID))
            },

            ":users" to { params ->

                val arg = params.argumentLine.trim()

                val message = if (arg == "all" && params.user.level == ChatUser.Level.ADMIN){
                    params.server.users.sortedBy { it.username }.fold("Users:\n") { s, user ->
                        val rooms = params.server.rooms.filter { it.isUserInRoom(user) } .fold(""){z, room ->
                            z + room.name + "(" + room.permissions.getOrDefault(user, room.defaultPermission).name + ") "
                        }
                        s + user.username + " " + user.level.name + " in " + rooms + "\n"
                    }
                } else if (arg == "details") {
                    params.room.users.sortedBy { it.username }.fold("Users:\n") { s, user ->
                        s + user.username + " " + user.level.name +
                                "(" + params.room.permissions.getOrDefault(user, params.room.defaultPermission) + ")\n"
                    }
                } else {
                    params.room.users.sortedBy { it.username }.fold("Users:\n") { s, user -> s + user.username + "\n" }
                }


                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))
            },

            ":messages" to { params ->

                val arg = params.argumentLine.trim()

                val message = if (arg == "all" && params.user.level == ChatUser.Level.ADMIN){
                    params.server.messageHistory.getAll().fold("Messages:\n") { s, msg ->
                        s + msg.toExtendedTextMessage() + "\n"
                    }
                } else if (arg == "details") {
                    params.server.messageHistory.query(room = params.room).fold("Messages:\n") {
                        s, msg -> s + msg.toExtendedTextMessage() + "\n"
                    }
                } else {
                    params.server.messageHistory.query(room = params.room).fold("Messages:\n") {
                        s, msg -> s + msg.toExtendedTextMessage() + "\n"
                    }
                }


                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))
            },


            ":whitelist" to {params ->

                val permissions = params.room.getPermissionsFor(params.user)

                val command = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                val argument = params.argumentLine.trim().split(" ").getOrNull(1)?.trim() ?: ""

                if (command != "clear") {

                    val userName = if (argument == "") command else argument

                    val user = params.server.getUserByUsername(userName)

                    if (user != null) {
                        if (permissions >= ChatRoom.UserPermissions.ADMIN && params.user.level > ChatUser.Level.UNKNOWN) {
                            val newRoom = if (argument == "" || command == "add")
                                params.room.whitelistAdd(user)
                            else if (command == "remove")
                                params.room.whitelistRemove(user)
                            else null

                            if (newRoom != null)
                                params.server.updateRoom(params.room, newRoom)
                            else
                                params.server.appendOutput(ServerOutput.unknownCommand(params.clientID, ":whitelist " + command))
                        } else {

                            params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.ADMIN, params.clientID))
                        }
                    } else {
                        params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                    }
                } else { //command == "clear"
                    params.server.updateRoom(params.room, params.room.whitelistClear())
                }

            },


            ":blacklist" to {params ->

            val permissions = params.room.getPermissionsFor(params.user)

            val command = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

            val argument = params.argumentLine.trim().split(" ").getOrNull(1)?.trim() ?: ""

            if (command != "clear") {

                val userName = if (argument == "") command else argument

                val user = params.server.getUserByUsername(userName)

                if (user != null) {
                    if (permissions >= ChatRoom.UserPermissions.ADMIN && params.user.level > ChatUser.Level.UNKNOWN) {
                        val newRoom = if (argument == "" || command == "add")
                            params.room.blacklistAdd(user)
                        else if (command == "remove")
                            params.room.blacklistRemove(user)
                        else null

                        if (newRoom != null)
                            params.server.updateRoom(params.room, newRoom)
                        else
                            params.server.appendOutput(ServerOutput.unknownCommand(params.clientID, ":blacklist " + command))
                    } else {

                        params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.ADMIN, params.clientID))
                    }
                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }
            } else { //command == "clear"
                params.server.updateRoom(params.room, params.room.blacklistClear())
            }

    }


    )

    val allCommands = basicCommands


}