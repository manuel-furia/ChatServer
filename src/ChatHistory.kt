import java.sql.Timestamp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Store the history of the messages in a server
 */
class ChatHistory private constructor(history: List<Entry>) {

    companion object {
        /**
         * Represents an empty chat history
         */
        val empty = ChatHistory(listOf())
    }

    /**
     * A single entry of the chat history, containing information about the message, user, room and timestamp
     */
    data class Entry(val message: String, val user: ChatUser, val room: ChatRoom, val timestamp: Long){

        /**
         * Returns the message without information on the time it was received, as string
         */
        fun toTextMessage(): String {
            if (room.name == Constants.defaultRoomName)
                return message + " from " + user.username
            else
                return message + " from " + user.username + " to " + room.name
        }

        /**
         * Returns all the information on the message, as string
         */
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

    /**
     * Join two separate histories. The output is ordered by timestamp.
     */
    fun join(thatHistory: ChatHistory): ChatHistory = ChatHistory((history + thatHistory.history).sortedBy { it.timestamp })

    /**
     * Get a list of all the entries in the history
     */
    fun getAll(): List<Entry> = history

    /**
     * Get all the entries of which message contains the specified text. If text = null, get all the entries.
     */
    fun queryText(text: String?): List<Entry> = history.filter {text == null || it.message.contains(text)}

    /**
     * Get all the entries of which message contains the specified text, user and room. If a parameter is null, the
     * filtering with respect to that parameter is going to be ignored.
     */
    fun query(text: String? = null, user: ChatUser? = null, room: ChatRoom? = null): List<Entry> {

        val filteredByText = queryText(text)
        val filteredByUser = filteredByText.filter {user == null || it.user == user}
        val filteredByRoom = filteredByUser.filter {room == null || it.room == room}

        return filteredByRoom
    }

    /**
     * Get all the entries of which message contains the specified text, username and room name (as strings).
     * If a parameter is null, the filtering with respect to that parameter is going to be ignored.
     */
    fun query(text: String?, username: String?, roomName: String?): List<Entry> {

        val filteredByText = queryText(text)
        val filteredByUser = filteredByText.filter {username == null || it.user.username == username}
        val filteredByRoom = filteredByUser.filter {roomName == null || it.room.name == roomName}

        return filteredByRoom
    }

}