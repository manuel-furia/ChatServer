
import sun.net.www.ParseUtil.toURI
import java.io.File

fun main(args: Array<String>){

    //Arrange the parameters as map
    val params = args.toList().zipWithNext().filterIndexed { index, pair -> index % 2 == 0 }.toMap()

    val pluginsFolder = params.get("--plugins")
    val port = params.get("--port")?.toIntOrNull() ?: 61673

    val listener = ChatServerListener(ChatServerState(), port, pluginsFolder)

    listener.listen(topChatterBot = true)
}