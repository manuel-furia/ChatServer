//Author: Manuel Furia
//Student ID: 1706247

/**
 * The main function of the program, creating and invoking the server
 */

fun main(args: Array<String>){

    //Arrange the parameters as map
    val params = args.toList().zipWithNext().filterIndexed { index, pair -> index % 2 == 0 }.toMap()

    val pluginsFolder = params.get("--plugins")
    val port = params.get("--port")?.toIntOrNull() ?: 61673
    val mode = params.get("--mode")
    val validMode = if (mode == "plain" || mode == "rich") mode else "rich"

    val listener = ChatServerListener(ChatServerState(), port, pluginsFolder, mode == "plain")

    listener.listen(topChatterBot = true)

    //NOTE: Because the readLine in the ServerConsole thread is blocking, the thread can not be terminated, so the
    //      program will keep on running after a :STOP command issued by a remote admin
    //      (but the other threads will all be shut down cleanly)
    //      System.exit(0) is a temporary fix that closes the hanging thread forcefully,
    //      but a better solution will be necessary in the future
    System.exit(0)
}