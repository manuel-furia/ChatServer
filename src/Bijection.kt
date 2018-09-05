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
    fun map(function: (pair: Pair<DomainEntry, CodomainEntry>) -> Pair<DomainEntry, CodomainEntry>): Bijection<DomainEntry, CodomainEntry>
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

    val domainEntries: Set<DomainEntry>
    val codomainEntries: Set<CodomainEntry>
}