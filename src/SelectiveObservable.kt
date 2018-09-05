interface SelectiveObservable<T>: Observable<T> {

    fun notifyObserver(observer: Observer<T>, event: T)

}