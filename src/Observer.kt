//Author: Manuel Furia
//Student ID: 1706247

/**
 * Receives notification of events of type T by an Observable<T> (see Observer Pattern)
 */

interface Observer<T> {

    fun update(event: T)

}