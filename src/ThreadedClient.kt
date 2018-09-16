//Author: Manuel Furia
//Student ID: 1706247

/**
 * Implements the Observer/Observable pattern for a generic client and
 * provides thread handling functionality (running and stopping when necessary)
 * Any client handler that wants to run on a separate thread can inherit this class to avoid code duplication.
 */
//NOTE: Examples of derived classes are TCPClientHandler, ServerConsole and TopChatter

abstract class ThreadedClient(val uid: Long, observer: Observer<ClientMessageEvent>, observable: Observable<ServerMessageEvent>): Observer<ServerMessageEvent>, Observable<ClientMessageEvent>, Runnable {

    var running: Boolean = false
        private set

    //The observable to which the client will register (in our case the observable is the server)
    //This observable will produce ServerMessageEvent object that will contain messages from the server
    protected val observable: Observable<ServerMessageEvent> = observable

    //The observers that will be notified when a client sends a ClientMessageEvent (the listening servers)
    protected var observers: Set<Observer<ClientMessageEvent>> = setOf(observer)
    private set

    init {
        observable.registerObserver(this)
    }

    override fun run(){
        running = true
    }

    open fun stop() {
        running = false
        observable.unregisterObserver(this)
        //Notify the server that we stopped
        notifyObservers(ClientMessageEvent(this, "", stopped = true))
    }

    override fun registerObserver(observer: Observer<ClientMessageEvent>) {
        this.observers = this.observers + observer
    }

    override fun unregisterObserver(observer: Observer<ClientMessageEvent>) {
        this.observers = this.observers - observer
    }

    override fun notifyObservers(event: ClientMessageEvent) {
        observers.forEach { it.update(event) }
    }
}