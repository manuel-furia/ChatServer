import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.ZoneId


class Commands(pluginDirectory: File? = null, errorStream: PrintStream? = null) {

    companion object {
        const val textMessage = ""
        val emptyCommands: Map<String, (CommandParameters) -> ChatServerState> = mapOf()
    }

    val basicCommands: Map<String, (CommandParameters) -> ChatServerState> = mapOf (

            textMessage to {params ->

                val permissions = params.room.getPermissionsFor(params.user)

                if (params.user.level <= ChatUser.Level.UNKNOWN){
                    val output = ServerOutput.usernameNotSet(params.clientID)
                   params.server.copy(currentOutput = params.server.currentOutput + output)
                } else if (permissions >= ChatRoom.UserPermissions.VOICE){

                    val timestamp = System.currentTimeMillis()

                    val historyEntry = ChatHistory.Entry(params.argumentLine, params.user, params.room, timestamp)

                    val output = ServerOutput.MessageFromUserToRoom(historyEntry)

                    params.server.appendMessageHistory(historyEntry).appendOutput(output)

                } else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.VOICE, params.clientID))
                }
            },

            ":user" to {params ->

                val newUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                if (newUsername == ""){
                    params.server.appendOutput(ServerOutput.usernameNotSpecifiedWhenSetting(params.clientID))
                } else {

                    val targetUser = params.server.getUserByUsername(newUsername)

                    if (targetUser != null) {

                        params.server.appendOutput(ServerOutput.userAlreadyExistsMessage(params.clientID))

                    } else {

                        val rooms = params.server.getRoomsByUser(params.user)

                        val output = rooms.fold(listOf<ServerOutput>()) { out, room ->
                            out + ServerOutput.userInRoomChangedUsernameMessage(params.user.username, newUsername, room.name)
                        }

                        params.server.changeUsername(params.user.username, newUsername).appendOutput(output)
                    }
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

            ":schedule" to {params ->

                if (params.user.level < ChatUser.Level.NORMAL) {

                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))

                } else {

                    val timeAsString = params.argumentLine.trim().split(" ").getOrNull(0) ?: ""
                    val action = params.argumentLine.drop(timeAsString.length + 1)
                    val time = timeAsString.toIntOrNull()

                    if (time != null){
                        if (params.user.level >= ChatUser.Level.ADMIN || time <= Constants.maxNonAdminSchedule) {
                            params.server.appendOutput(ServerOutput.Schedule(System.currentTimeMillis() + time*1000, action, params.user, params.room))
                        } else {
                            params.server.appendOutput(ServerOutput.nonAdminCannotScheduleLaterThanMessage(params.clientID))
                        }



                    } else {
                        params.server.appendOutput(ServerOutput.unknownCommand(params.clientID, ":schedule " + params.argumentLine))
                    }
                }
            },

            ":execute" to {params ->

                if (params.user.level < ChatUser.Level.NORMAL) {

                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))

                } else {

                    params.server.processIncomingMessageFromClient(params.clientID, Constants.roomSelectionPrefix + params.room.name + " " + params.argumentLine)

                }
            },

            ":leave" to {params ->

                if (params.room.name == Constants.mainRoomName){
                    params.server.appendOutput(ServerOutput.youCantLeaveTheMainRoomMessage(params.clientID))
                } else {

                    val output = ServerOutput.userLeftRoomMessage(params.user.username, params.room.name)

                    params.server.userLeaveRoom(params.room.name, params.user.username).appendOutput(output)
                }
            },

            ":kick" to {params ->

                val permissions = params.room.getPermissionsFor(params.user)

                if (permissions >= ChatRoom.UserPermissions.MOD) {

                    val username = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                    val reason = params.argumentLine.trim().drop(username.length + 1)
                    val user = params.server.getUserByUsername(username)

                    if (user == null) {
                        params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                    } else {
                        val targetPermissions =  params.room.getPermissionsFor(user)
                        if (params.room.name == Constants.mainRoomName) {
                            params.server.appendOutput(ServerOutput.youCantKickUsersFromTheMainRoomMessage(params.clientID))
                        } else if (permissions >= targetPermissions) {
                            val clientID = params.server.userToClientID(user) ?: -1
                            val messageToRoom = ServerOutput.kickedFromRoom(user.username, params.room.name)
                            val messageToKicked = ServerOutput.youHaveBeenKickedFromRoom(clientID, params.room.name, reason)
                            params.server.userLeaveRoom(params.room.name, user.username)
                                    .appendOutput(listOf(messageToRoom, messageToKicked))
                        } else {
                            params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, targetPermissions, params.clientID))
                        }
                    }
                } else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.MOD, params.clientID))
                }
            },

            ":grant" to {params ->

                val permissions = params.room.getPermissionsFor(params.user)

                val newPermissionString = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                val username = params.argumentLine.trim().split(" ").getOrNull(1)?.trim() ?: ""
                val parsedPermissions: ChatRoom.UserPermissions? = try {
                    ChatRoom.UserPermissions.valueOf(newPermissionString.toUpperCase())
                } catch (ex: Exception){
                    null
                }

                if (permissions >= ChatRoom.UserPermissions.MOD && parsedPermissions != null && parsedPermissions <= permissions) {

                    val user = params.server.getUserByUsername(username)

                    if (user == null) {
                        params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                    } else {
                        val targetCurrentPermissions = params.room.getPermissionsFor(user)
                        if (targetCurrentPermissions < permissions || user.username == params.user.username) {
                            val messageToRoom = ServerOutput.roomPermissionGranted(parsedPermissions, user.username, params.room.name)
                            params.server
                                .updateRoom(params.room, params.room.setPermissions(user, parsedPermissions))
                                .appendOutput(messageToRoom)

                        } else {
                            params.server.appendOutput(ServerOutput.roomYouNeedHigherPermissionsThan(user.username, params.clientID))
                        }
                    }
                } else {
                params.server.appendOutput(ServerOutput.roomPermissionDenied(
                        permissions,
                        if (parsedPermissions == null)
                            ChatRoom.UserPermissions.MOD
                        else
                            parsedPermissions,
                        params.clientID))
                }
            },

            ":KICK" to {params ->

                val username = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                val user = params.server.getUserByUsername(username)

                if (params.user.level >= ChatUser.Level.ADMIN && user != null){
                    params.server.removeUser(user.username)

                } else {
                    params.server
                }
            },

            ":BAN" to {params ->
                val username = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""
                val user = params.server.getUserByUsername(username)

                if (user != null  && params.user.level >= ChatUser.Level.ADMIN) {
                    val clientID = params.server.userToClientID(user)

                    if (clientID != null) {
                        params.server.banUserByName(user.username)
                                .removeUser(user.username)
                    } else {
                        params.server
                    }
                } else {
                    params.server
                }
            },

            ":UNBAN" to {params ->
                val ipaddr = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                if (params.user.level >= ChatUser.Level.ADMIN)
                    params.server.liftBan(ipaddr)
                else
                    params.server

            },

            ":topic" to {params ->

                val topic = params.argumentLine.trim()

                val permissions = params.room.getPermissionsFor(params.user)

                if (permissions >= ChatRoom.UserPermissions.MOD) {
                    params.server.setRoomTopic(params.room.name, topic)
                } else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.MOD, params.clientID))
                }
            },

            ":quit" to { params ->
                params.server.removeUser(params.user.username)
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

            ":rooms" to { params ->

                val rooms = if (params.user.level >= ChatUser.Level.ADMIN) {
                    params.server.rooms
                } else {
                    //Show only rooms without whitelist to normal users
                    params.server.rooms.filter { it.whitelist.size == 0 }
                }


                val message = rooms.fold("Rooms: "){s, r -> s + '\n' + r.name}

                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message))
            },

            ":messages" to { params ->

                val (arg, formatAsData) = if (params.argumentLine.startsWith("data"))
                        Pair(params.argumentLine.drop("data".length+1).trim(), true)
                    else
                        Pair(params.argumentLine.trim(), false)

                val permissions = params.room.getPermissionsFor(params.user)

                val formatFunc = {s: String, msg: ChatHistory.Entry ->
                    s + (if (formatAsData) msg.toDataMessage() else msg.toExtendedTextMessage()) + "\n"
                }

                if (permissions >= ChatRoom.UserPermissions.READ) {

                    val messages = if (arg == "all" && params.user.level == ChatUser.Level.ADMIN) {
                        params.server.messageHistory.getAll()
                    } else {
                        params.server.messageHistory.query(room = params.room).getAll()
                    }

                    val message = messages.fold("Messages:\n", formatFunc)

                    params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))

                }else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.READ, params.clientID))
                }

            },

            ":query" to { params ->

                val (arg, formatAsData) = if (params.argumentLine.startsWith("data"))
                    Pair(params.argumentLine.drop("data".length+1).trim(), true)
                else
                    Pair(params.argumentLine.trim(), false)

                val permissions = params.room.getPermissionsFor(params.user)

                val formatFunc = {s: String, msg: ChatHistory.Entry ->
                    s + (if (formatAsData) msg.toDataMessage() else msg.toExtendedTextMessage()) + "\n"
                }

                val queryMap = (arg
                        .split(";")
                        .map {x -> x.split("=")}
                        .filter { x -> x.size == 2 }
                        .map {it[0] to it[1]}).toMap()

                if (permissions >= ChatRoom.UserPermissions.READ) {

                    val messages = if (params.user.level == ChatUser.Level.ADMIN) {
                        params.server.messageHistory
                    } else {
                        params.server.messageHistory.query(room = params.room)
                    }

                    val start = getTimestampIfPossible(queryMap.get("start"), true)?.toString()
                    val end = getTimestampIfPossible(queryMap.get("end"), false)?.toString()


                    val queryResult = messages.query(queryMap.get("text"), queryMap.get("user"), queryMap.get("room"), start, end)

                    val message = queryResult.getAll().fold("Query result:\n", formatFunc)

                    params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))

                }else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.READ, params.clientID))
                }

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


            },

            ":pvt" to {params ->

                val targetUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                val message = params.argumentLine.drop(targetUsername.length + 1)

                val user = params.server.getUserByUsername(targetUsername)

                if (user != null) {

                    val (serverWithPvtRoom, pvtRoom) = getPvtRoom(params.server, params.user, user)

                    if (pvtRoom != null) {

                        if (pvtRoom.isUserInRoom(params.user)) {

                            val messageEntry = ChatHistory.Entry(message, params.user, pvtRoom, System.currentTimeMillis())

                            serverWithPvtRoom.appendOutput(ServerOutput.MessageFromUserToRoom(messageEntry))

                        } else {
                            params.server.appendOutput(ServerOutput.userPvtDeniedMessage(params.clientID))
                        }

                    } else {
                        params.server.appendOutput(ServerOutput.userPvtFailedMessage(params.clientID))
                    }


                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }

            },

            ":block" to { params ->

                val targetUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                val user = params.server.getUserByUsername(targetUsername)

                if (user != null) {

                    val (serverWithPvtRoom, pvtRoom) = getPvtRoom(params.server, params.user, user)

                    if (pvtRoom != null) {

                        val pvtRoomWithBlackListed = pvtRoom.blacklistAdd(user).userLeave(user)
                        serverWithPvtRoom.updateRoom(pvtRoom, pvtRoomWithBlackListed)

                    } else {
                        params.server.appendOutput(ServerOutput.userPvtFailedMessage(params.clientID))
                    }


                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }

            },

            ":unblock" to { params ->

                val targetUsername = params.argumentLine.trim().split(" ").getOrNull(0)?.trim() ?: ""

                val user = params.server.getUserByUsername(targetUsername)

                if (user != null) {

                    val (serverWithPvtRoom, pvtRoom) = getPvtRoom(params.server, params.user, user)

                    if (pvtRoom != null) {

                        val pvtRoomWithBlackListed = pvtRoom.blacklistRemove(user)
                        serverWithPvtRoom.updateRoom(pvtRoom, pvtRoomWithBlackListed)

                    } else {
                        params.server.appendOutput(ServerOutput.userPvtFailedMessage(params.clientID))
                    }


                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }

            }


    )

    val plugins: Map<String, (CommandParameters) -> ChatServerState> =
        if (pluginDirectory != null && pluginDirectory.exists() && pluginDirectory.isDirectory) {
            PluginManager(pluginDirectory.absolutePath, basicCommands, errorStream).commands
        } else {
            errorStream?.println("Plugins Warning: Could not find specified plugin directory.")
            mapOf()
        }


    val allCommands = basicCommands + plugins

    private fun getTimestampIfPossible(time: String?, isStart: Boolean = false): Long? {

        if (time == null) return null

        val (timeString, dateString) = Pair (
                time.split(",").getOrNull(0) ?: "",
                time.split(",").getOrNull(1) ?: ""
        )

        val now = LocalDateTime.now()
        val defaultTime = if (isStart)
            now.minusMinutes(Constants.defaultQueryMinutesAgo.toLong())
        else
            now

        val (hh, mm) = Pair (
                timeString.split(":").getOrNull(0)?.toIntOrNull() ?: defaultTime.minute,
                timeString.split(":").getOrNull(1)?.toIntOrNull() ?: defaultTime.hour
        )

        val (day, month, year) = Triple (
                dateString.split(".", "/", "\\").getOrNull(0)?.toIntOrNull() ?: now.dayOfMonth,
                dateString.split(".", "/", "\\").getOrNull(1)?.toIntOrNull() ?: now.monthValue,
                dateString.split(".", "/", "\\").getOrNull(1)?.toIntOrNull() ?: now.year
        )

        try { //LocalDateTime can throw exceptions if the format is wrong
            return LocalDateTime.of(year, month, day, hh, mm)?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        } catch (ex: Exception){
            return null
        }

    }

    private fun getPvtRoom(server: ChatServerState, userA: ChatUser, userB: ChatUser): Pair<ChatServerState, ChatRoom?> {
        val maybePvtRoomName = listOf<String>(userA.username, userB.username)
                .sorted()
                .fold(""){s, u -> s + Constants.pvtRoomUsernameSeperator + u}
                .drop(1) //Drop the first dot

        val maybePvtRoom = server.getRoomByName(maybePvtRoomName)

        val serverWithPvtRoom = if (maybePvtRoom == null) {
            server.addRoom(maybePvtRoomName)

        } else {
            server
        }

        val pvtRoom = serverWithPvtRoom
                .getRoomByName(maybePvtRoomName)

        val updatedPvtRoom = pvtRoom
                ?.whitelistAdd(userA)
                ?.whitelistAdd(userB)
                ?.userJoin(userA)
                ?.userJoin(userB)
                ?.setPermissions(userB, ChatRoom.UserPermissions.ADMIN)
                ?.setPermissions(userA, ChatRoom.UserPermissions.ADMIN)


        val serverWithUpdatedPvtRoom = if (pvtRoom != null && updatedPvtRoom != null) {
            serverWithPvtRoom.updateRoom(pvtRoom, updatedPvtRoom)
        } else {
            serverWithPvtRoom
        }

        return Pair(serverWithUpdatedPvtRoom, updatedPvtRoom)
    }


}