//Author: Manuel Furia
//Student ID: 1706247

/* Commands.kt
 * Contains the commands available to the users and admins, stored in a map of name -> anonymous function elements.
 * The anonymous function that represents the command takes a CommandParameters object, containing a server state,
 * information on the user that issued the command and the room it was destined for. The anonymous function returns an
 * updated server state.
 * To add an additional command, insert an addicional element in the basicCommands map.
 * The allCommands map includes also commands loaded at runtime from plugins.
 */

import java.io.File
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.ZoneId


class Commands(pluginDirectory: File? = null, errorStream: PrintStream? = null) {

    companion object {
        //Command to sent a text message
        //An empty string will cause any message that does not start with a command prefix to be matched as text message
        const val textMessage = ""
    }

    /**
     * The basic commands on the server, excluding the ones dynamically loaded via plugins
     */
    val basicCommands: Map<String, (CommandParameters) -> ChatServerState> = mapOf (
            //The user issued a plain text message
            textMessage to {params ->
                //Get the user permissions in the room the message is destined for
                val permissions = params.room.getPermissionsFor(params.user)
                //If the server wide user level is UNKNOWN, the user did not issue the :user command,
                //so they can not write regardless of their room permissions
                if (params.user.level <= ChatUser.Level.UNKNOWN){
                    val output = ServerOutput.usernameNotSet(params.clientID) //A "username not set" error message
                   params.server.copy(currentOutput = params.server.currentOutput + output)
                } else if (permissions >= ChatRoom.UserPermissions.VOICE){ //The user can write text messages
                    //Get the timestamp for the message
                    val timestamp = System.currentTimeMillis()
                    //Create a message history entry
                    val historyEntry = ChatHistory.Entry(params.argumentLine, params.user, params.room, timestamp)
                    //Create the message output event
                    val output = ServerOutput.MessageFromUserToRoom(historyEntry)
                    //Append the message and the output to the server, and return the new server
                    params.server
                            .appendMessageToHistory(historyEntry)
                            .appendOutput(output)

                } else { //The user does not have permission to write in the room
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.VOICE, params.clientID))
                }
            },
            //Change username
            ":user" to {params ->
                //Fetch the new username from the argument line
                val newUsername = getArgumentByIndex(params.argumentLine, 0)

                if (newUsername == "") {
                    //If the argument was not provided return a server state with an error message appended to the output
                    params.server.appendOutput(ServerOutput.usernameNotSpecifiedWhenSetting(params.clientID))
                } else if (params.user.username == Constants.serverConsoleUsername) {
                    //The server console can not change username, return the server unaltered
                    params.server
                } else {
                    //Try to get a user from the new username provided
                    val targetUser = params.server.getUserByUsername(newUsername)
                    if (targetUser != null) { //If we can get a users, it means the username is not unique
                        params.server.appendOutput(ServerOutput.userAlreadyExistsMessage(params.clientID))
                    } else { //The username is free
                        //Get all the rooms in which the old user is present
                        val rooms = params.server.getRoomsByUser(params.user)
                        //Send each room a notification message that the user changed their username
                        val output = rooms.fold(listOf<ServerOutput>()) { out, room ->
                            out + ServerOutput.userInRoomChangedUsernameMessage(params.user.username, newUsername, room.name)
                        }
                        //Return the updated server state
                        params.server
                                .changeUsername(params.user.username, newUsername)
                                .appendOutput(output)
                    }
                }
            },
            //Show a list of users
            // :users            -> Shows all the users in the target room
            // :users detals     -> Shows all the users in the target room, with their permissions
            // :users all        -> Shows all the users in all rooms, with their permissions (admin only)
            ":users" to { params ->
                //Retrieve the parameter for the command
                val arg =  getArgumentByIndex(params.argumentLine, 0)
                //Build the response text according to the parameter received
                val message = if (arg == "all" && params.user.level == ChatUser.Level.ADMIN){
                    //Take all the users from the server
                    params.server.users
                            .sortedBy { it.username.toLowerCase() } //Order them alphabetically
                            .fold("Users:\n") { s, user -> //Fold into a message showing information for each user
                                //Make also a sub-fold to list all the rooms the user belongs to, with permissions
                                val rooms = params.server.rooms
                                        .filter { it.isUserInRoom(user) }
                                        .fold(""){ z, room ->
                                            z + room.name + "(" + room.permissions.getOrDefault(user, room.defaultPermission).name + ") "
                                        }
                                //Construct a line containing username and rooms info
                                s + user.username + " " + user.level.name + " in " + rooms + "\n"
                            }
                } else if (arg == "details") {//Show all the users in the target room, with permissions
                    //Take all the users from the target room
                    params.room.users
                            .sortedBy { it.username.toLowerCase() } //Sort them
                            .fold("Users:\n") { s, user -> //Fold into a message showing information for each user
                                s + user.username + " " + user.level.name +
                                        "(" + params.room.permissions.getOrDefault(user, params.room.defaultPermission) + ")\n"
                            }
                } else { //Show all the users in the target room
                    //Take all the users from the target room
                    params.room.users
                            .sortedBy { it.username.toLowerCase() } //Sort them alphabetically
                            .fold("Users:\n") { s, user -> s + user.username + "\n" } //Add them to the output message
                }
                //Create a new server state with appended an output representing a service message to the user that
                //requested the user list
                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))
            },
            //:messages          -> Shows all the messages posted in the room
            //:messages all      -> Shows all the messages in the server (admin only)
            //:messages data     -> Shows all the messages posted in the room, in easily parsable format
            //:messages data all -> Shows all the messages in the server (admin only), in easily parsable format
            ":messages" to { params ->
                //Parse the command by removing "data" if necessary, obtaining the argument and a flag
                //stating if "data" was present
                val (arg, formatAsData) = if (params.argumentLine.startsWith("data"))
                    Pair(params.argumentLine.drop("data".length+1).trim(), true)
                else
                    Pair(params.argumentLine.trim(), false)
                //Get the permissions of the user issuing the command
                val permissions = params.room.getPermissionsFor(params.user)
                //Function to format the message into a string, taking the requested format into account
                val formatFunc = { s: String, msg: ChatHistory.Entry ->
                    s + (if (formatAsData) msg.toDataMessage() else msg.toExtendedTextMessage()) + "\n"
                }
                //If the user can read in the room or it is the server admin
                if (permissions >= ChatRoom.UserPermissions.READ || params.user.level == ChatUser.Level.ADMIN) {
                    //Get the requested messages
                    val messages = if (arg == "all" && params.user.level >= ChatUser.Level.ADMIN) {
                        params.server.messageHistory.getAll()
                    } else {
                        params.server.messageHistory.query(room = params.room).getAll()
                    }
                    //Fold the messages into a string by applying the format function to each one of them
                    val message = messages.fold("Messages:\n", formatFunc)
                    //Return a new server state with output specifying to send the message list as service message
                    //to the client that requested it
                    params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message.trim('\n')))
                }else { //No permissions
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.READ, params.clientID))
                }
            },
            //User leave the server
            ":quit" to { params ->
                params.server.removeUser(params.user.username)
            },
            //Try to login as admin by providing admin username and password
            ":admin" to {params ->

                val adminUsername = getArgumentByIndex(params.argumentLine, 0)
                val adminPassword = getArgumentByIndex(params.argumentLine, 1)

                params.server.becomeAdmin(adminUsername, adminPassword, params.user)
            },
            //Create a new room or join an existing one
            ":room" to {params ->
                if (params.user.level < ChatUser.Level.NORMAL) {
                    //An anonymous user can not create a room
                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))
                } else {
                    val roomName = getArgumentByIndex(params.argumentLine, 0)
                    val isUserAlreadyInside = params.server.getUsersInRoom(roomName).contains(params.user)
                    //Decide the message to show to the room (none if the user is already inside)
                    val message = if (isUserAlreadyInside)
                        ServerOutput.None
                    else
                        ServerOutput.userJoinedRoomMessage(params.user.username, roomName)

                    //Return the updated server state
                    params.server
                            .addRoom(roomName)
                            .userJoinRoom(roomName, params.user.username)
                            .appendOutput(message)
                }
            },
            //Leave the room this command is destined for
            ":leave" to {params ->
                if (params.room.name == Constants.mainRoomName){
                    params.server.appendOutput(ServerOutput.youCantLeaveTheMainRoomMessage(params.clientID))
                } else {
                    val output = ServerOutput.userLeftRoomMessage(params.user.username, params.room.name)
                    params.server.userLeaveRoom(params.room.name, params.user.username).appendOutput(output)
                }
            },
            //:kick username reason      Kick a user out of a room
            ":kick" to {params ->
                val permissions = params.room.getPermissionsFor(params.user)
                //Only room moderators or admins can kick
                if (permissions >= ChatRoom.UserPermissions.MOD) {
                    val username = getArgumentByIndex(params.argumentLine, 0)
                    val reason = params.argumentLine.trim().drop(username.length + 1)
                    val user = params.server.getUserByUsername(username)

                    if (user == null) {
                        params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                    } else {
                        val targetPermissions =  params.room.getPermissionsFor(user)
                        if (params.room.name == Constants.mainRoomName) { //Users can not be kicked out from the main room
                            params.server.appendOutput(ServerOutput.youCantKickUsersFromTheMainRoomMessage(params.clientID))
                        } else if (permissions >= targetPermissions) { //A moderator cannot kick an admin
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
            //Modify the permissions of a user
            ":grant" to {params ->
                val issuerPermissions = params.room.getPermissionsFor(params.user)
                val newPermissionString = getArgumentByIndex(params.argumentLine, 0)
                val targetUsername = getArgumentByIndex(params.argumentLine, 1)
                val parsedPermissions: ChatRoom.UserPermissions? = try {
                    ChatRoom.UserPermissions.valueOf(newPermissionString.toUpperCase())
                } catch (ex: Exception){
                    null
                }
                if (issuerPermissions >= ChatRoom.UserPermissions.MOD && parsedPermissions != null && parsedPermissions <= issuerPermissions) {
                    val targetUser = params.server.getUserByUsername(targetUsername)
                    if (targetUser == null) {
                        params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                    } else {
                        val targetUserCurrentPermissions = params.room.getPermissionsFor(targetUser)
                        if (targetUserCurrentPermissions < issuerPermissions || targetUser.username == params.user.username) {
                            val messageToRoom = ServerOutput.roomPermissionGranted(parsedPermissions, targetUser.username, params.room.name)
                            params.server
                                .updateRoom(params.room, params.room.setPermissions(targetUser, parsedPermissions))
                                .appendOutput(messageToRoom)

                        } else {
                            params.server.appendOutput(ServerOutput.roomYouNeedHigherPermissionsThan(targetUser.username, params.clientID))
                        }
                    }
                } else {
                params.server.appendOutput(ServerOutput.roomPermissionDenied(
                        issuerPermissions,
                        if (parsedPermissions == null)
                            ChatRoom.UserPermissions.MOD
                        else
                            parsedPermissions,
                        params.clientID))
                }
            },
            //Kick a user out of the server
            ":KICK" to {params ->
                val username = getArgumentByIndex(params.argumentLine, 0)
                val user = params.server.getUserByUsername(username)
                if (params.user.level >= ChatUser.Level.ADMIN && user != null){
                    params.server.removeUser(user.username)

                } else {
                    params.server
                }
            },
            //Ban a user's ip address from the server
            ":BAN" to {params ->
                val username = getArgumentByIndex(params.argumentLine, 0)
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
            //Lift an ip address ban
            ":UNBAN" to {params ->
                val ipaddr = getArgumentByIndex(params.argumentLine, 0)
                if (params.user.level >= ChatUser.Level.ADMIN)
                    params.server.liftBan(ipaddr)
                else
                    params.server

            },
            //Set the greeting message shown to a user when they join a room
            ":topic" to {params ->
                val topic = params.argumentLine.trim()
                val permissions = params.room.getPermissionsFor(params.user)
                if (permissions >= ChatRoom.UserPermissions.MOD) {
                    params.server.setRoomTopic(params.room.name, topic)
                } else {
                    params.server.appendOutput(ServerOutput.roomPermissionDenied(permissions, ChatRoom.UserPermissions.MOD, params.clientID))
                }
            },
            //List all the non private rooms on the server (or all the rooms if the issuer is an admin)
            ":rooms" to { params ->
                val rooms = if (params.user.level >= ChatUser.Level.ADMIN) {
                    params.server.rooms
                } else {
                    //Show only rooms without whitelist to normal users
                    params.server.rooms.filter { it.whitelist.size == 0 }
                }
                val message = rooms
                        .sortedBy { it.name.toLowerCase() }
                        .fold("Rooms: "){s, r -> s + '\n' + r.name}
                params.server.appendOutput(ServerOutput.serviceMessageTo(params.clientID, message))
            },
            //:query                            -> Shows all the messages (in the server if admin, in the room if normal user)
            //:query room=room_name             -> Shows only the messages of a room
            //:query user=username              -> Shows only the messages of a user
            //:query start=15:30                -> Shows only the messages from 15:30 of today
            //:query start=15:30,15.09.2018     -> Shows only the messages from 15:30 of the specified date
            //:query end=15:30                  -> Shows only the messages until 15:30 of today
            //:query end=15:30,15.09.2018       -> Shows only the messages until 15:30 of the specified date
            //:query start=timestamp            -> Shows only the messages from the specified timestamp
            //:query end=timestamp              -> Shows only the messages until the specified timestamp
            //:query text=hello world           -> Shows only the messages that contains somewhere the text "hello world"
            //All the filters above can be combined with semicolons, for example:
            //:query user=mario;text=hello world;start=15:30,15.09.2018;end=16:00
            ":query" to { params ->
                //Parse the command
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
                    } else { //Normal users can not see outside their rooms
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
            //:whitelist add user    -> Add the user to the whitelist; Allow only the whitelisted users to join the room
            //:whitelist remove user -> Remove the user from the whitelist
            //:whitelist clear       -> Remove all the users from the whitelist, rendering the room public again
            ":whitelist" to {params ->
                val permissions = params.room.getPermissionsFor(params.user)
                val command = getArgumentByIndex(params.argumentLine, 0)
                val argument = getArgumentByIndex(params.argumentLine, 1)
                if (command != "clear") {
                    val userName = if (argument == "") command else argument
                    val targetUser = params.server.getUserByUsername(userName)
                    if (targetUser != null) {
                        if (permissions >= ChatRoom.UserPermissions.ADMIN && params.user.level > ChatUser.Level.UNKNOWN) {

                            val newRoom = if (argument == "" || command == "add")
                                params.room.whitelistAdd(targetUser)
                            else if (command == "remove")
                                params.room.whitelistRemove(targetUser)
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
            //:blacklist add user    -> Add the user to the blacklist (user not allowed to join the room)
            //:blacklist remove user -> Remove the user from the blacklist
            //:blacklist clear       -> Remove all the users from the blacklist
            ":blacklist" to {params ->
                val permissions = params.room.getPermissionsFor(params.user)
                val command = getArgumentByIndex(params.argumentLine, 0)
                val argument = getArgumentByIndex(params.argumentLine, 1)
                if (command != "clear") {
                    val userName = if (argument == "") command else argument
                    val targetUser = params.server.getUserByUsername(userName)
                    if (targetUser != null) {
                        if (permissions >= ChatRoom.UserPermissions.ADMIN && params.user.level > ChatUser.Level.UNKNOWN) {

                            val newRoom = if (argument == "" || command == "add")
                                params.room.blacklistAdd(targetUser)
                            else if (command == "remove")
                                params.room.blacklistRemove(targetUser)
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
            //:pvt user message  -> Sends a private message to a user
            //It creates a new unique room (if it does not exist yet) for the pair of user exchanging private messages,
            //with only the two of them whitelisted (so nobody else can join)
            //A user can block pvt from another user with the :block command
            ":pvt" to {params ->
                val targetUsername = getArgumentByIndex(params.argumentLine, 0)
                val message = params.argumentLine.drop(targetUsername.length + 1)
                val targetUser = params.server.getUserByUsername(targetUsername)
                if (targetUser != null) {
                    //Create (if necessary) and get the pvt room
                    val (serverWithPvtRoom, pvtRoom) = getPvtRoom(params.server, params.user, targetUser)
                    if (pvtRoom != null) {
                        if (pvtRoom.isUserInRoom(params.user)) {
                            val messageEntry = ChatHistory.Entry(message, params.user, pvtRoom, System.currentTimeMillis())
                            serverWithPvtRoom.appendOutput(ServerOutput.MessageFromUserToRoom(messageEntry))
                        } else { //The user has been blocked by the target user
                            params.server.appendOutput(ServerOutput.userPvtDeniedMessage(params.clientID))
                        }

                    } else {
                        params.server.appendOutput(ServerOutput.userPvtFailedMessage(params.clientID))
                    }
                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }

            },
            //:block user
            //Block a user from sending pvt to you
            ":block" to { params ->
                val targetUsername = getArgumentByIndex(params.argumentLine, 0)
                val user = params.server.getUserByUsername(targetUsername)
                if (user != null) {
                    val (serverWithPvtRoom, pvtRoom) = getPvtRoom(params.server, params.user, user)
                    if (pvtRoom != null) {
                        //Kick the user from the pvt room and kick them, so they will not be able to send pvt to the pvt target user
                        val pvtRoomWithBlackListed = pvtRoom.blacklistAdd(user).userLeave(user)
                        serverWithPvtRoom.updateRoom(pvtRoom, pvtRoomWithBlackListed)

                    } else {
                        params.server.appendOutput(ServerOutput.userPvtFailedMessage(params.clientID))
                    }


                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }

            },
            //:unblock user
            //Allow again a user to send pvt to you
            ":unblock" to { params ->
                val targetUsername = getArgumentByIndex(params.argumentLine, 0)
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
            },
            //:ping user -> Send a ping to a user
            ":ping" to {params ->
                val targetUsername = getArgumentByIndex(params.argumentLine, 0)
                val fromUser = params.server.getUserByUsername(targetUsername)
                if (fromUser != null && params.room.canUserWrite(fromUser)) {
                    params.server.appendOutput(ServerOutput.PingUser(params.user, fromUser))
                } else {
                    params.server.appendOutput(ServerOutput.userDoesNotExistsMessage(params.clientID))
                }
            },
            //Disconnect all the clients and halt the server (admin only)
            ":STOP" to {params ->
                if (params.user.level >= ChatUser.Level.ADMIN){
                    params.server.appendOutput(ServerOutput.Stop())
                } else {
                    params.server
                }
            },
            //Set a command to be executed, or a message to be displayed, later in time
            ":schedule" to {params ->
                if (params.user.level < ChatUser.Level.NORMAL) {
                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))
                } else {
                    val timeAsString = params.argumentLine.trim().split(" ").getOrNull(0) ?: ""
                    val action = params.argumentLine.drop(timeAsString.length + 1)
                    val time = timeAsString.toIntOrNull()
                    if (time != null){
                        //Users that are not admins have a limitation on the maximum delay they can set
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
            //Treat the parameter as a command or message of its own to be executed immediately (equivalent to :schedule 0 text)
            ":execute" to {params ->
                if (params.user.level < ChatUser.Level.NORMAL) {
                    params.server.appendOutput(ServerOutput.permissionDenied(params.user.level, ChatUser.Level.NORMAL, params.clientID))
                } else {
                    params.server.processIncomingMessageFromClient(params.clientID, Constants.roomSelectionPrefix + params.room.name + " " + params.argumentLine)
                }
            }
    )

    //Map containing all the commands loaded dynamically from plugins
    val plugins: Map<String, (CommandParameters) -> ChatServerState> =
        if (pluginDirectory != null && pluginDirectory.exists() && pluginDirectory.isDirectory) {
            PluginManager(pluginDirectory.absolutePath, basicCommands, errorStream).commands
        } else {
            errorStream?.println("Plugins Warning: Could not find specified plugin directory.")
            mapOf() //There was an error, no command was loaded
        }

    //The commands are the union of the basic commands plus plugins
    val allCommands = basicCommands + plugins

    /**
     * Parse the argument line of a command, returning the i-th parameret of the command
     */
    private fun getArgumentByIndex(argumentLine: String, i: Int): String {
        return argumentLine.trim().split(" ").getOrNull(i)?.trim() ?: ""
    }

    /**
     * Parse a text timestamp, a time or a time and date to a long timestamp.
     * Format example: 13:45,21.11.2018
     */
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

    /**
     * Get a pvt room for two users, creating it if it does not exists
     * @return A pair containing updated server state (containing the room if created) and the newly created room (if created, null otherwise)
     */
    private fun getPvtRoom(server: ChatServerState, userA: ChatUser, userB: ChatUser): Pair<ChatServerState, ChatRoom?> {
        //Create a unique room name of the time "user1.user2"
        val maybePvtRoomName = listOf<String>(userA.username, userB.username)
                .sorted()
                .fold(""){s, u -> s + Constants.pvtRoomUsernameSeperator + u}
                .drop(1) //Drop the first dot
        //try to get the room if it exists
        val maybePvtRoom = server.getRoomByName(maybePvtRoomName)
        //get an updated server, depending on the room existing or nor
        val serverWithPvtRoom = if (maybePvtRoom == null) {
            server.addRoom(maybePvtRoomName)
        } else {
            server
        }
        //Get the already existing or newly created private room
        val pvtRoom = serverWithPvtRoom
                .getRoomByName(maybePvtRoomName)
        //If the pvt room was newly created, initialize it by joining the users, whitelisting them and setting permissions
        val updatedPvtRoom = pvtRoom
                ?.whitelistAdd(userA)
                ?.whitelistAdd(userB)
                ?.userJoin(userA)
                ?.userJoin(userB)
                ?.setPermissions(userB, ChatRoom.UserPermissions.ADMIN)
                ?.setPermissions(userA, ChatRoom.UserPermissions.ADMIN)
        //If the room was just created, add the room to the state, otherwise use the previous state
        val serverWithUpdatedPvtRoom = if (pvtRoom != null && updatedPvtRoom != null) {
            serverWithPvtRoom.updateRoom(pvtRoom, updatedPvtRoom)
        } else {
            serverWithPvtRoom
        }
        //Return the updated server state and the pvt room
        return Pair(serverWithUpdatedPvtRoom, updatedPvtRoom)
    }


}