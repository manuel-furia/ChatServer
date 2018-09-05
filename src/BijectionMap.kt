/**
 * Naive implementation of a bijection using Map
 * Not efficient for inner loops
 */
class BijectionMap<Domain, Codomain>(private val pairs: Set<Pair<Domain, Codomain>>): Bijection<Domain, Codomain> {

    constructor(): this(setOf())

    constructor(map: Map<Domain, Codomain>): this(map.entries.map{it.toPair()}.toSet())

    private val directMap = pairs.toMap()
    private val inverseMap = pairs.map {it.second to it.first}.toMap()

    override val domainEntries: Set<Domain>
        get() = directMap.keys
    override val codomainEntries: Set<Codomain>
        get() = inverseMap.keys


    /**
     * Apply direct map (get an element of the codomain from an element of the domain)
     */
    override fun direct(p: Domain): Codomain? {
        return directMap.get(p)
    }

    /**
     * Apply inverse map (get an element of the domain from an element of the codomain)
     */
    override fun inverse(q: Codomain): Domain? {
        return inverseMap.get(q)
    }

    override operator fun plus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain> {
        return BijectionMap(pairs + pair)
    }

    override operator fun minus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain> {
        return BijectionMap(pairs - pair)
    }

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    override fun removeByDomainElement(d: Domain): Bijection<Domain, Codomain> {
        val pair = find { it.first == d }
        if (pair != null) {
            return BijectionMap(pairs - pair)
        } else {
            return this
        }
    }

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    override fun removeByCodomainElement(d: Codomain): Bijection<Domain, Codomain> {
        val pair = find { it.second == d }
        if (pair != null) {
            return BijectionMap(pairs - pair)
        } else {
            return this
        }
    }

    override fun domainContains(p: Domain): Boolean {
        return directMap.containsKey(p)
    }

    override fun codomainContains(q: Codomain): Boolean {
        return inverseMap.containsKey(q)
    }

    override fun find(condition: (pair: Pair<Domain, Codomain>) -> Boolean): Pair<Domain, Codomain>? {
        val pairs = directMap.entries.map{it.toPair()}.toSet()
        return pairs.find(condition)
    }

    override fun filter(condition: (pair: Pair<Domain, Codomain>) -> Boolean): Bijection<Domain, Codomain> {
        return BijectionMap(pairs.filter(condition).toSet())
    }

    override fun map(function: (pair: Pair<Domain, Codomain>) -> Pair<Domain, Codomain>): Bijection<Domain, Codomain> {
        return BijectionMap(pairs.map(function).toSet())
    }


}
