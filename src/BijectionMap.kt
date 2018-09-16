//Author: Manuel Furia
//Student ID: 1706247

/* BijectionMap.kt
 * Naive implementation of a bijection (see the interface Bijection) using Map
 * It is useful to represent a 1 to 1 relationship (for example, user <-> clientid)
 * In case there are repetition in one of the elements in the domain or codomain, the element
 * added later will override the preceding one
 */

/**
 * Bijection implemented using two Map objects
 */
class BijectionMap<Domain, Codomain> private constructor(pairs: Set<Pair<Domain, Codomain>>): Bijection<Domain, Codomain> {

    /**
     * Create an empty bijection
     */
    constructor(): this(setOf())

    /**
     * Create a bijection from a list of pairs
     */
    constructor(list: List<Pair<Domain, Codomain>>): this(BijectionMap.distinctPairsUnsafe(list))

    /**
     * Create a bijection from a map
     */
    constructor(map: Map<Domain, Codomain>): this(BijectionMap.distinctPairsUnsafe(map.entries.map { it.toPair() }))

    /**
     * Create a bijection from a set of pairs
     */
    private constructor(pairs: Set<Pair<Domain, Codomain>>, pair: Pair<Domain,Codomain>): this(BijectionMap.distinctPairsSafeExceptLast(pairs, pair))

    /**
     * Create a bijection from a bijection and an additional domain <-> codomain pair
     */
    private constructor(bijection: BijectionMap<Domain, Codomain>, pair: Pair<Domain,Codomain>): this(BijectionMap.distinctPairsSafe(bijection.pairs), pair)

    //The pairs that represent the bijection. The distinctPairsUnsafe, distinctPairsSafeExceptLast and
    //distinctPairsSafe will guarantee that each element of the domain in unique and each element of the
    //codomain is unique.
    private val pairs = BijectionMap.distinctPairsSafe(pairs)

    //Create maps out of the pairs that represent the bijection, so access to domain element given codomain element or
    //access to codomain element given domain element can be fast.
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
        //This constructor call will create a new bijection formed by this bijection with the new pair added to it
        return BijectionMap(this, pair)
    }

    override operator fun minus(pair: Pair<Domain, Codomain>): Bijection<Domain, Codomain> {
        //If we remove an element from the set of pairs defining an already well formed bijection,
        //we know that the result is safe without need to check for duplicates in domain or codomain
        return BijectionMap(BijectionMap.distinctPairsSafe(pairs - pair))
    }

    /**
     * Return a new bijection missing the pair that has as domain element the specified one
     */
    override fun removeByDomainElement(d: Domain): Bijection<Domain, Codomain> {
        val pair = find { it.first == d }
        if (pair != null) {
            //If we remove an element from the set of pairs defining an already well formed bijection,
            //we know that the result is safe without need to check for duplicates in domain or codomain
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
            //If we remove an element from the set of pairs defining an already well formed bijection,
            //we know that the result is safe without need to check for duplicates in domain or codomain
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
        //If we filter out elements from the set of pairs defining an already well formed bijection,
        //we know that the result is safe without need to check for duplicates in domain or codomain
        return BijectionMap(BijectionMap.distinctPairsSafe(pairs.filter(condition).toSet()))
    }

    override fun<T, K> map(function: (pair: Pair<Domain, Codomain>) -> Pair<T, K>): Bijection<T, K> {
        //When we map over a bijection, we can not know if the resulting pairs are going to
        //have unique values for domain and codomain, so we need to call distinctPairsUnsafe to
        //check for duplicates and eliminate them
        return BijectionMap(BijectionMap.distinctPairsUnsafe(pairs.map(function)))
    }

    override fun size(): Int {
        return directMap.size
    }



    companion object {
        /**
         * All the pairs we are trying to use to create the bijection do not contain duplicates for either domain
         * or codomain elements, except the last one
         */
        private fun<Domain, Codomain> distinctPairsSafeExceptLast(oldPairs: Set<Pair<Domain, Codomain>>, last: Pair<Domain, Codomain>): Set<Pair<Domain, Codomain>> {
            val mutResult = mutableSetOf<Pair<Domain, Codomain>>()
            mutResult.addAll(oldPairs)
            //We remove all the possible duplicates of the last element, for domain and codomain
            mutResult.removeAll {it.first == last.first || it.second == last.second}
            mutResult.add(last)
            return mutResult
        }

        /**
         * All the pairs we are trying to use to create the bijection could contain duplicates for either domain
         * or codomain elements
         */
        private fun<Domain, Codomain> distinctPairsUnsafe(pairs: List<Pair<Domain, Codomain>>): Set<Pair<Domain, Codomain>> {
            val mutResult = mutableSetOf<Pair<Domain, Codomain>>()
            //For each pair...
            for (pair in pairs) {
                //Remove all the duplicates of that pair from the previously added pairs set
                mutResult.removeAll {it.first == pair.first || it.second == pair.second}
                //Then add the pair
                mutResult.add(pair)
            }
            return mutResult
        }

        /**
         * All the pairs we are trying to use do not contain duplicates
         */
        private fun<Domain, Codomain> distinctPairsSafe(pairs: Set<Pair<Domain, Codomain>>): Set<Pair<Domain, Codomain>> {
            //As there are no duplicates for either domain, codomain or both, we don't need to modify the pair set
            return pairs
        }
    }

}
