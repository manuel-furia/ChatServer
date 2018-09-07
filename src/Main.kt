fun main(args: Array<String>){

    val listener = ChatServerListener(ChatServerState())
    listener.listen(topChatterBot = true)
}