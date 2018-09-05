interface Observable<T> {

    fun registerObserver(observer: Observer<T>)
    fun unregisterObserver(observer: Observer<T>)
    fun notifyObservers(event: T)

}