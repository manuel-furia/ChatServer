import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class ChatHistoryTest {

    val emptyHistory: ChatHistory = ChatHistory.empty


    @Test
    fun addEntry() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello2", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello3", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("hello4", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))
        val h = h1.addEntry(h2.getAll()[0]).addEntry(h3.getAll()[0]).addEntry(h4.getAll()[0])

        Assertions.assertEquals(4, h.getAll().size)
        Assertions.assertEquals(10000L, h.getAll()[0].timestamp)
        Assertions.assertEquals("a", h.getAll()[0].user.username)
        Assertions.assertEquals("hello", h.getAll()[0].message)
        Assertions.assertEquals("hello4", h.getAll()[3].message)

    }

    @Test
    fun join() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello2", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello3", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("hello4", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))

        val ha = h1.join(h2)
        val hb = h3.join(h4)

        val h = ha.join(hb)

        Assertions.assertEquals(4, h.getAll().size)
        Assertions.assertEquals(5000L, h.getAll()[0].timestamp)
        Assertions.assertEquals("b", h.getAll()[0].user.username)
        Assertions.assertEquals("hello2", h.getAll()[0].message)
        Assertions.assertEquals("hello4", h.getAll()[3].message)

    }

    @Test
    fun getAll() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello2", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello3", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("hello4", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))

        val ha = h1.join(h2)
        val hb = h3.join(h4)

        val h = ha.join(hb)

        Assertions.assertEquals(4, h.getAll().size)
        Assertions.assertEquals(5000L, h.getAll()[0].timestamp)
        Assertions.assertEquals("b", h.getAll()[0].user.username)
        Assertions.assertEquals("hello2", h.getAll()[0].message)
        Assertions.assertEquals("hello4", h.getAll()[3].message)
    }

    @Test
    fun queryText() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello world", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("B"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello world!", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello space", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("bye mountain", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))

        val ha = h1.join(h2)
        val hb = h3.join(h4)

        val h = ha.join(hb)

        Assertions.assertEquals(2, h.queryText("world").getAll().size)
        Assertions.assertEquals(3, h.queryText("hello").getAll().size)
        Assertions.assertEquals(1, h.queryText("bye").getAll().size)
        Assertions.assertEquals(1, h.queryText("mountain").getAll().size)
        Assertions.assertEquals(2, h.queryText("hello world").getAll().size)
        Assertions.assertEquals(1, h.queryText("hello world!").getAll().size)
        Assertions.assertEquals(4, h.queryText(" ").getAll().size)
        Assertions.assertEquals(4, h.queryText(null).getAll().size)

    }

    @Test
    fun query() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello world", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("B"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello world!", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello space", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("bye mountain", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))

        val ha = h1.join(h2)
        val hb = h3.join(h4)

        val h = ha.join(hb)

        Assertions.assertEquals(2, h.query(user = ChatUser("a", ChatUser.Level.NORMAL)).getAll().size)
        Assertions.assertEquals(2, h.query(text = "world").getAll().size)
        Assertions.assertEquals(1, h.query(text = "world", user = ChatUser("a", ChatUser.Level.NORMAL)).getAll().size)
        Assertions.assertEquals(3, h.query(text = "hello").getAll().size)
        Assertions.assertEquals(1, h.query(text = "bye mount").getAll().size)
        Assertions.assertEquals(1, h.query(text = "bye mountain").getAll().size)
        Assertions.assertEquals(1, h.query(user = ChatUser("a", ChatUser.Level.NORMAL), room=ChatRoom("A")).getAll().size)
        Assertions.assertEquals(0, h.query(text = "bye", room=ChatRoom("B")).getAll().size)
        Assertions.assertEquals(1, h.query(text = "hello world", room=ChatRoom("A")).getAll().size)
        Assertions.assertEquals(2, h.query(startTimestamp = 8000, endTimestamp = 20000).getAll().size)
        Assertions.assertEquals(3, h.query(startTimestamp = 8000).getAll().size)
        Assertions.assertEquals(2, h.query(endTimestamp = 10000).getAll().size)
    }

    @Test
    fun query1() {
        val h1 = emptyHistory.addEntry(ChatHistory.Entry("hello world", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("B"), 10000))
        val h2 = emptyHistory.addEntry(ChatHistory.Entry("hello world!", ChatUser("b", ChatUser.Level.NORMAL), ChatRoom("A"), 5000))
        val h3 = emptyHistory.addEntry(ChatHistory.Entry("hello space", ChatUser("c", ChatUser.Level.NORMAL), ChatRoom("A"), 11000))
        val h4 = emptyHistory.addEntry(ChatHistory.Entry("bye mountain", ChatUser("a", ChatUser.Level.NORMAL), ChatRoom("A"), 55000))

        val ha = h1.join(h2)
        val hb = h3.join(h4)

        val h = ha.join(hb)

        Assertions.assertEquals(2, h.query(null, ChatUser("a", ChatUser.Level.NORMAL), null, null, null).getAll().size)
        Assertions.assertEquals(1, h.query("world", "a", null, null, null).getAll().size)
        Assertions.assertEquals(1, h.query(null, "a", "A", null, null).getAll().size)
        Assertions.assertEquals(0, h.query("bye", null, "B", null, null).getAll().size)
        Assertions.assertEquals(1, h.query("hello world", null, "A", null, null).getAll().size)
        Assertions.assertEquals(2, h.query(null, null, null, "8000",  "20000").getAll().size)
        Assertions.assertEquals(3, h.query(null, null, null, "8000",  null).getAll().size)
        Assertions.assertEquals(2, h.query(null, null, null, null,  "10000").getAll().size)

    }
}