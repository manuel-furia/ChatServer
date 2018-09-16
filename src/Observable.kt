//Author: Manuel Furia
//Student ID: 1706247

/**
 * Produces events of type T for observers (see Observer Pattern)
 */

interface Observable<T> {

    fun registerObserver(observer: Observer<T>)
    fun unregisterObserver(observer: Observer<T>)
    fun notifyObservers(event: T)

}