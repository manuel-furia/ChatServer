//Author: Manuel Furia
//Student ID: 1706247

/* Bijection.kt
 *
 * Collection that represents a map in which both keys and values are unique.
 * For each key there is only one value, and for each value there is only one key.
 * The elements of the keys are called domain elements, the elements of the value codomain elements.
 * It is possible to retrieve an element of the codomain given an element of the domain, or an
 * element of the domain given an element of the codomain.
 */

/**
 * Represents a bijection between generic elements
 */
interface Bijection<DomainEntry, CodomainEntry> {

    /**
     * Apply direct map (get an element of the codomain from an element of the domain)
     */
    fun direct(p: DomainEntry): CodomainEntry?

    /**
     * Apply inverse map (get an element of the domain from an element of the codomain)
     */
    fun inverse(q: CodomainEntry): DomainEntry?

    fun domainContains(p: DomainEntry): Boolean
    fun codomainContains(q: CodomainEntry): Boolean
    fun find(condition: (pair: Pair<DomainEntry, CodomainEntry>) -> Boolean): Pair<DomainEntry, CodomainEntry>?
    fun filter(condition: (pair: Pair<DomainEntry, CodomainEntry>) -> Boolean): Bijection<DomainEntry, CodomainEntry>
    fun<T, K> map(function: (pair: Pair<DomainEntry, CodomainEntry>) -> Pair<T, K>): Bijection<T, K>
    operator fun plus(pair: Pair<DomainEntry, CodomainEntry>): Bijection<DomainEntry, CodomainEntry>
    operator fun minus(pair: Pair<DomainEntry, CodomainEntry>): Bijection<DomainEntry, CodomainEntry>

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    fun removeByDomainElement(d: DomainEntry): Bijection<DomainEntry, CodomainEntry>

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    fun removeByCodomainElement(q: CodomainEntry): Bijection<DomainEntry, CodomainEntry>

    fun size(): Int

    val domainEntries: Set<DomainEntry>
    val codomainEntries: Set<CodomainEntry>
}