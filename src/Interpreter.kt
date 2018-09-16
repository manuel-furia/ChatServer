//Author: Manuel Furia
//Student ID: 1706247

/**
 * Inteprets a command or text message, dispatching to the appropriate handler and collecting the result as a server state
 */

class Interpreter(line: String, server: ChatServerState, user: ChatUser, room: ChatRoom, commands: Map<String, (CommandParameters) -> ChatServerState>) {

    /**
     * Contains the command execution resulting server state
     */
    val result: ChatServerState = {
        //Get the client of the user issuing the command
        val clientID = server.userToClientID(user)
        if (clientID != null) {
            //A user cannot start their lines with the server message prefix, check for those conditions
            val invalidMessage =
                    line.startsWith(Constants.serverMessagePrefix) ||
                            line.contains("\n" + Constants.serverMessagePrefix)

            if (invalidMessage) {
                //The command starts with a server message prefix, so it's invalid
                server.appendOutput(ServerOutput.serviceMessageTo(
                        clientID,
                        "You are not allowed to start your message with " + Constants.serverMessagePrefix)
                )
            } else if ( Constants.commandPrefixes.any { line.startsWith(it) } ){
                //If the line is valid and starts with a prefix that indicates a command, parse it
                val commandName = line.split(' ').first()
                val argumentLine = line.drop(commandName.length + 1)
                val parameters = CommandParameters(argumentLine, server, user, room, clientID)
                //Fetch the command handler (anonymous function)
                val command = commands[commandName]
                //If the command was found
                if (command != null) {
                    command.invoke(parameters) //Invoke it
                } else {
                    //The command was not found, append an error message to the server state
                    server.appendOutput(ServerOutput.unknownCommand(clientID, commandName))
                }
            }  else {
                //If instead it is a text message, call the textMessage command
                val parameters = CommandParameters(line, server, user, room, clientID)
                commands[Commands.textMessage]?.invoke(parameters) ?: server
            }
        } else { //ClientID = null, means nobody to execute the command for, so return the state unaltered
            server
        }

    }()

}