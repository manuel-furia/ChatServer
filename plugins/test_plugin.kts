":testp" to {params ->
    params.server.appendOutput(
        ServerOutput.ServiceMessageToEverybody("This is a test plugin!")
    )
}