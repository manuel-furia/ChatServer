import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.*

internal class BijectionMapTest {

    //Do not modify these 3 bijection maps (A, B and C)
    private val testPairsA = listOf(4 to "four", 6 to "six", 10 to "ten", 5 to "five", 6 to "six")
    private val bijectionMapA = BijectionMap(testPairsA)
    private val testPairsB = listOf(-1 to "seven", 4 to "four", 6 to "six", 10 to "ten", 5 to "five", 6 to "six", 4 to "fourteen", 7 to "six", 7 to "seven2", 0 to "seven", 1 to "seven")
    private val bijectionMapB = BijectionMap(testPairsB)
    private val testPairsC = listOf(-1 to "seven", 4 to "four", 6 to "six", 10 to "ten", 5 to "five", 6 to "six", 4 to "fourteen", 7 to "six", 7 to "seven2", 0 to "seven", 1 to "seven", 5 to "fifteen")
    private val bijectionMapC = BijectionMap(testPairsC)

    @org.junit.jupiter.api.Test
    fun testConsistency() {
        Assertions.assertTrue(bijectionMapA.domainEntries.size == bijectionMapA.codomainEntries.size)
        Assertions.assertTrue(bijectionMapB.domainEntries.size == bijectionMapB.codomainEntries.size)

        val testMapA = mapOf(4 to "four", 6 to "six", 10 to "ten", 5 to "five", 6 to "six")
        val bA = BijectionMap(testMapA)
        Assertions.assertTrue(bA.domainEntries == bijectionMapA.domainEntries && bA.codomainEntries == bijectionMapA.codomainEntries)

        for (e in testMapA.entries){
            Assertions.assertEquals(bA.direct(e.key), bijectionMapA.direct(e.key))
            Assertions.assertEquals(bA.inverse(e.value), bijectionMapA.inverse(e.value))
        }
    }


    @org.junit.jupiter.api.Test
    fun getDomainEntries() {
        Assertions.assertArrayEquals(testPairsA.map {it.first}.distinct().sorted().toTypedArray(), bijectionMapA.domainEntries.sorted().toTypedArray())
    }

    @org.junit.jupiter.api.Test
    fun getCodomainEntries() {
        Assertions.assertArrayEquals(testPairsA.map {it.second}.distinct().sorted().toTypedArray(), bijectionMapA.codomainEntries.sorted().toTypedArray())
    }

    @org.junit.jupiter.api.Test
    fun direct() {
        Assertions.assertEquals("four", bijectionMapA.direct(4))
        Assertions.assertEquals("ten", bijectionMapA.direct(10))
        Assertions.assertEquals("six", bijectionMapA.direct(6))

        val b2 = bijectionMapA + (4 to "fourteen")
        Assertions.assertEquals("fourteen", b2.direct(4))

        val b3 = bijectionMapA + (7 to "six")
        Assertions.assertEquals("six", b3.direct(7))

        val b4 = bijectionMapA + (4 to "ten")
        Assertions.assertEquals("ten", b4.direct(4))

        Assertions.assertEquals("fourteen", bijectionMapB.direct(4))
        Assertions.assertEquals("seven2", bijectionMapB.direct(7))
        Assertions.assertEquals("seven", bijectionMapB.direct(1))

        for (d in testPairsB.map{it.first}){
            val i = bijectionMapA.direct(d)
            if (i != null)
                Assertions.assertEquals(d, bijectionMapA.inverse(i))
            else
                Assertions.assertEquals(false, bijectionMapA.domainEntries.contains(d))
        }
    }

    @org.junit.jupiter.api.Test
    fun inverse() {
        Assertions.assertEquals(4, bijectionMapA.inverse("four"))
        Assertions.assertEquals(10, bijectionMapA.inverse("ten"))
        Assertions.assertEquals(6, bijectionMapA.inverse("six"))

        val b2 = bijectionMapA + (4 to "fourteen")
        Assertions.assertEquals(4, b2.inverse("fourteen"))

        val b3 = bijectionMapA + (7 to "six")
        Assertions.assertEquals(7, b3.inverse("six"))

        val b4 = bijectionMapA + (4 to "ten")
        Assertions.assertEquals(4, b4.inverse("ten"))

        Assertions.assertEquals(4, bijectionMapB.inverse("fourteen"))
        Assertions.assertEquals(7, bijectionMapB.inverse("seven2"))
        Assertions.assertEquals(1, bijectionMapB.inverse("seven"))

        for (i in testPairsB.map{it.second}){
            val d = bijectionMapA.inverse(i)
            if (d != null)
                Assertions.assertEquals(i, bijectionMapA.direct(d))
            else
                Assertions.assertEquals(false, bijectionMapA.codomainEntries.contains(i))
        }
    }

    @org.junit.jupiter.api.Test
    fun plus() {
        val b1 = bijectionMapA + (1000 to "thousand")
        Assertions.assertEquals(1000, b1.inverse("thousand"))
        Assertions.assertEquals("thousand", b1.direct(1000))

        val b2 = bijectionMapA + (4 to "fourteen")
        Assertions.assertEquals("fourteen", b2.direct(4))

        val b3 = bijectionMapA + (7 to "six")
        Assertions.assertEquals("six", b3.direct(7))

        val b4 = bijectionMapA + (4 to "ten")
        Assertions.assertEquals("ten", b4.direct(4))

        val b5 = testPairsA.fold<Pair<Int, String>, Bijection<Int, String>>(BijectionMap<Int, String>()){ s, x -> s + x}
        val b6 = testPairsB.fold<Pair<Int, String>, Bijection<Int, String>>(BijectionMap<Int, String>()){ s, x -> s + x}

        Assertions.assertEquals(bijectionMapA.domainEntries.size, b5.domainEntries.size)

        for (d in b5.domainEntries){
            Assertions.assertTrue(bijectionMapA.direct(d) == b5.direct(d))
        }

        for (i in b5.codomainEntries){
            Assertions.assertTrue(bijectionMapA.inverse(i) == b5.inverse(i))
        }

        Assertions.assertEquals(bijectionMapB.domainEntries.size, b6.domainEntries.size)

        for (d in b6.domainEntries){
            Assertions.assertEquals(bijectionMapB.direct(d), b6.direct(d))
        }

        for (i in b6.codomainEntries){
            Assertions.assertTrue(bijectionMapB.inverse(i) == b6.inverse(i))
        }

    }

    @org.junit.jupiter.api.Test
    fun minus() {
        val b1 = (bijectionMapA + (1000 to "thousand")) - (1000 to "thousand")
        Assertions.assertEquals(null, b1.inverse("thousand"))
        Assertions.assertEquals(null, b1.direct(1000))

        val b2 = bijectionMapA - (4 to "four")
        Assertions.assertEquals(null, b2.direct(4))

        val b3 = bijectionMapA - (6 to "six")
        Assertions.assertEquals(null, b3.direct(6))

        val b4 = bijectionMapB - (7 to "seven2")
        Assertions.assertEquals(null, b4.direct(7))

        val b5 = (bijectionMapB + (1000 to "thousand")) - (1000 to "thousand")
        Assertions.assertEquals(null, b5.inverse("thousand"))
        Assertions.assertEquals(null, b5.direct(1000))
    }

    @org.junit.jupiter.api.Test
    fun removeByDomainElement() {
        val b1 = (bijectionMapB + (1000 to "thousand")).removeByDomainElement(1000)
        Assertions.assertEquals(null, b1.direct(1000))

        val b2 = bijectionMapB.removeByDomainElement(457438)
        Assertions.assertEquals(bijectionMapB.domainEntries.size, b2.domainEntries.size)
        Assertions.assertEquals(bijectionMapB.codomainEntries.size, b2.codomainEntries.size)

        val b3 = bijectionMapB.removeByDomainElement(7)
        Assertions.assertEquals(null, b3.direct(7))
        Assertions.assertEquals(null, b3.inverse("seven2"))
    }

    @org.junit.jupiter.api.Test
    fun removeByCodomainElement() {
        val b1 = bijectionMapB.removeByCodomainElement("asd")
        Assertions.assertEquals(bijectionMapB.domainEntries.size, b1.domainEntries.size)
        Assertions.assertEquals(bijectionMapB.codomainEntries.size, b1.codomainEntries.size)

        val b2 = (bijectionMapB + (1000 to "thousand")).removeByCodomainElement("thousand")
        Assertions.assertEquals(null, b2.inverse("thousand"))

        val b3 = bijectionMapB.removeByCodomainElement("seven2")
        Assertions.assertEquals(null, b3.direct(7))
        Assertions.assertEquals(null, b3.inverse("seven2"))
    }

    @org.junit.jupiter.api.Test
    fun domainContains() {
        Assertions.assertEquals(false, bijectionMapB.domainContains(-1))
        Assertions.assertEquals(true, bijectionMapB.domainContains(10))
        Assertions.assertEquals(true, bijectionMapB.domainContains(1))
    }

    @org.junit.jupiter.api.Test
    fun codomainContains() {
        Assertions.assertEquals(false, bijectionMapC.codomainContains("six"))
        Assertions.assertEquals(false, bijectionMapC.codomainContains("five"))
        Assertions.assertEquals(true, bijectionMapC.codomainContains("seven2"))
        Assertions.assertEquals(true, bijectionMapC.codomainContains("fourteen"))
    }

    @org.junit.jupiter.api.Test
    fun find() {
        val f1 = bijectionMapA.find { p -> p.first == 6 && p.second == "six"}
        val f2 = bijectionMapA.find { p -> p.first == 45 && p.second == "six"}
        val f3 = bijectionMapA.find { p -> p.first == 6 && p.second == "hello"}
        Assertions.assertTrue(f1 != null && f1.first == 6 && f1.second == "six")
        Assertions.assertNull(f2)
        Assertions.assertNull(f3)
    }

    @org.junit.jupiter.api.Test
    fun filter() {
        val f1 = bijectionMapA.filter { p -> p.first == 6 && p.second == "six"}
        val f2 = bijectionMapA.filter { p -> p.first > 5 }
        val f3 = bijectionMapA.filter { true }
        val f4 = bijectionMapA.filter { false }

        Assertions.assertTrue(f1.domainContains(6) && f1.codomainContains("six") && f1.domainEntries.size == 1 && f1.codomainEntries.size == 1)
        Assertions.assertTrue(f2.domainContains(6) && f2.codomainContains("ten") && f2.domainEntries.size == 2 && f2.codomainEntries.size == 2)
        Assertions.assertTrue(f2.domainContains(10) && f2.codomainContains("six") && f2.domainEntries.size == 2 && f2.codomainEntries.size == 2)
        Assertions.assertTrue(f3.domainEntries == bijectionMapA.domainEntries && f3.codomainEntries == bijectionMapA.codomainEntries)
        Assertions.assertTrue(f4.domainEntries.size == 0 && f4.codomainEntries.size == 0)
    }

    @org.junit.jupiter.api.Test
    fun map() {
        val m1 = bijectionMapA.map {p -> p.first to p.second}
        Assertions.assertTrue(m1.domainEntries == bijectionMapA.domainEntries && m1.codomainEntries == bijectionMapA.codomainEntries)

        val m2 = bijectionMapA.map { p -> 0 to p.second }
        Assertions.assertTrue(m2.domainEntries == setOf(0) && m2.codomainEntries.size == 1)

        val m3 = bijectionMapA.map { p -> p.second to p.first }
        Assertions.assertTrue(m3.domainEntries == bijectionMapA.codomainEntries && m3.codomainEntries == bijectionMapA.domainEntries)
    }
}