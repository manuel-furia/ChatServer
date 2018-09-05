import java.sql.Timestamp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class ChatHistory private constructor(history: List<Entry>) {

    companion object {
        val empty = ChatHistory(listOf())
    }

    data class Entry(val message: String, val user: ChatUser, val room: ChatRoom, val timestamp: Long){
        fun toTextMessage(): String {
            val date = Date(timestamp)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")
            val dateString = dateFormat.format(date)
            val timeString = timeFormat.format(date)

            assert(dateString != null)

            if (room.name == Constants.defaultRoomName)
                return message + " from " + user.username
            else
                return message + " from " + user.username + " to " + room.name
        }

        fun toExtendedTextMessage(): String {
            val date = Date(timestamp)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd")
            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")
            val dateString = dateFormat.format(date)
            val timeString = timeFormat.format(date)

            assert(dateString != null)

            if (room.name == Constants.defaultRoomName)
                return message + " from " + user.username + " at " + dateString + "T" + timeString
            else
                return message + " from " + user.username + " at " + dateString + "T" + timeString + " to " + room.name
        }
    }

    private val history: List<Entry> = history

    fun addEntry(entry: Entry): ChatHistory = ChatHistory(history + listOf(entry))

    fun join(thatHistory: ChatHistory): ChatHistory = ChatHistory((history + thatHistory.history).sortedBy { it.timestamp })

    fun getAll(): List<Entry> = history

    fun queryText(text: String?): List<Entry> = history.filter {text == null || it.message.contains(text)}

    fun query(text: String? = null, user: ChatUser? = null, room: ChatRoom? = null): List<Entry> {

        val filteredByText = queryText(text)
        val filteredByUser = filteredByText.filter {user == null || it.user == user}
        val filteredByRoom = filteredByUser.filter {room == null || it.room == room}

        return filteredByRoom
    }

    fun query(text: String?, username: String?, roomName: String?): List<Entry> {

        val filteredByText = queryText(text)
        val filteredByUser = filteredByText.filter {username == null || it.user.username == username}
        val filteredByRoom = filteredByUser.filter {roomName == null || it.room.name == roomName}

        return filteredByRoom
    }

}