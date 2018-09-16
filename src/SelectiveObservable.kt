//Author: Manuel Furia
//Student ID: 1706247

/**
 * Extension of the Observer Pattern allowing the observable to notify also one single observer, instead of notifying
 * necessarily all of them
 */

interface SelectiveObservable<T>: Observable<T> {

    fun notifyObserver(observer: Observer<T>, event: T)

}