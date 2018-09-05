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

                params.server.changeUsername(params.user.username, newUsername)
            },

            ":admin" to {params ->

                val adminUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                val adminPassword = params.argumentLine.trim().split(" ").getOrNull(1)?.trim() ?: ""

                params.server.becomeAdmin(adminUsername, adminPassword, params.user)
            },

            ":room" to {params ->

                val roomName = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                params.server.addRoom(roomName).userJoinRoom(roomName, params.user.username)
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
                    params.server.users.fold("Users:\n") { s, user ->
                        val rooms = params.server.rooms.filter { it.isUserInRoom(user) } .fold(""){z, room ->
                            z + room.name + " "
                        }
                        s + user.username + " " + user.level.name + " in " + rooms + "\n"
                    }
                } else if (arg == "details") {
                    params.room.users.fold("Users:\n") { s, user -> s + user.username + " " + user.level.name + "\n" }
                } else {
                    params.room.users.fold("Users:\n") { s, user -> s + user.username + "\n" }
                }


                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message))
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


                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message))
            }



    )

    val allCommands = basicCommands


}