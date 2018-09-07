/**
 * Naive implementation of a bijection using Map
 * Not efficient for huge amount of elements
 */
class BijectionMap<Domain, Codomain> private constructor(pairs: Set<Pair<Domain, Codomain>>): Bijection<Domain, Codomain> {

    constructor(): this(setOf())

    constructor(list: List<Pair<Domain, Codomain>>): this(BijectionMap.distinctPairsUnsafe(list))

    constructor(map: Map<Domain, Codomain>): this(BijectionMap.distinctPairsUnsafe(map.entries.map { it.toPair() }))

    private constructor(pairs: Set<Pair<Domain, Codomain>>, pair: Pair<Domain,Codomain>): this(BijectionMap.distinctPairsSafeExceptLast(pairs, pair))

    private val pairs = BijectionMap.distinctPairsSafe(pairs)
    private val directMap = this.pairs.toMap()
    private val inverseMap = this.pairs.map {it.second to it.first}.toMap()

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
        return BijectionMap(BijectionMap.distinctPairsSafe(pairs), pair)
    }

    override operator fun minus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain> {
        return BijectionMap(BijectionMap.distinctPairsSafe(pairs - pair))
    }

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    override fun removeByDomainElement(d: Domain): Bijection<Domain, Codomain> {
        val pair = find { it.first == d }
        if (pair != null) {
            return BijectionMap(BijectionMap.distinctPairsSafe((pairs - pair)))
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
            return BijectionMap(BijectionMap.distinctPairsSafe(pairs - pair))
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
        val pairs = directMap.entries.map{it.toPair()}
        return pairs.find(condition)
    }

    override fun filter(condition: (pair: Pair<Domain, Codomain>) -> Boolean): Bijection<Domain, Codomain> {
        return BijectionMap(BijectionMap.distinctPairsSafe(pairs.filter(condition).toSet()))
    }

    override fun<T, K> map(function: (pair: Pair<Domain, Codomain>) -> Pair<T, K>): Bijection<T, K> {
        return BijectionMap(BijectionMap.distinctPairsUnsafe(pairs.map(function)))
    }





    companion object {
        private fun<Domain, Codomain> distinctPairsSafeExceptLast(oldPairs: Set<Pair<Domain, Codomain>>, last: Pair<Domain, Codomain>): Set<Pair<Domain, Codomain>> {
            val mutResult = mutableSetOf<Pair<Domain, Codomain>>()
            mutResult.addAll(oldPairs)
            mutResult.removeAll {it.first == last.first || it.second == last.second}
            mutResult.add(last)
            return mutResult
        }

        private fun<Domain, Codomain> distinctPairsUnsafe(pairs: List<Pair<Domain, Codomain>>): Set<Pair<Domain, Codomain>> {
            val mutResult = mutableSetOf<Pair<Domain, Codomain>>()
            for (pair in pairs) {
                mutResult.removeAll {it.first == pair.first || it.second == pair.second}
                mutResult.add(pair)
            }
            return mutResult
        }

        private fun<Domain, Codomain> distinctPairsSafe(pairs: Set<Pair<Domain, Codomain>>): Set<Pair<Domain, Codomain>> {
            return pairs
        }
    }

}
