interface Bijection<Domain, Codomain> {

    fun direct(p: Domain): Codomain?
    fun inverse(q: Codomain): Domain?
    fun domainContains(p: Domain): Boolean
    fun codomainContains(q: Codomain): Boolean
    fun find(condition: (pair: Pair<Domain, Codomain>) -> Boolean): Pair<Domain, Codomain>?
    fun filter(condition: (pair: Pair<Domain, Codomain>) -> Boolean): Bijection<Domain, Codomain>
    fun map(function: (pair: Pair<Domain, Codomain>) -> Pair<Domain, Codomain>): Bijection<Domain, Codomain>
    operator fun plus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain>
    operator fun minus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain>
    fun removeByDomainElement(d: Domain): Bijection<Domain, Codomain>
    fun removeByCodomainElement(q: Codomain): Bijection<Domain, Codomain>
    val domainEntries: Set<Domain>
    val codomainEntries: Set<Codomain>
}