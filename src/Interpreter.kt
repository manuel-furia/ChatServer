class Interpreter(line: String, server: ChatServerState, user: ChatUser, room: ChatRoom) {

    val result: ChatServerState = {

        val clientID = server.userToClientID(user)

        if (clientID != null) {

            //Check if the line starts with a prefix to indicate a command
            if ( Constants.commandPrefixes.any { line.startsWith(it) } ){

                //A user cannot start their lines with the server message prefix
                val invalidMessage = line.startsWith(Constants.serverMessagePrefix) ||
                        line.contains("\n" + Constants.serverMessagePrefix)

                if (invalidMessage) {
                    server.appendOutput(ServerOutput.serviceMessageTo(
                            clientID,
                            "You are not allowed to start your message with " + Constants.serverMessagePrefix)
                    )
                } else {

                    val commandName = line.split(' ').first()
                    val argumentLine = line.drop(commandName.length + 1)
                    val parameters = CommandParameters(argumentLine, server, user, room, clientID)

                    val command = Commands.allCommands[commandName]

                    if (command != null) {
                        command.invoke(parameters)
                    } else {
                        server.appendOutput(ServerOutput.unknownCommand(clientID, commandName))
                    }
                }

            } else { //If instead it is a text message, call the textMessage command

                val parameters = CommandParameters(line, server, user, room, clientID)
                Commands.allCommands[Commands.textMessage]?.invoke(parameters) ?: server
            }
        } else { //ClientID = null, means nobody to execute the command for, so return the state unaltered
            server
        }

    }()

}