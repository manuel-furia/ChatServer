//Author: Manuel Furia
//Student ID: 1706247

/* ChatHistory.kt
 * Stores the history of the messages in a server, offers means to make queries based on text, username, room name and time
 * and allows to get the message data in various text formats.
 * Contains classes ChatHistory and ChatHistory.Entry
 */

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
        fun toFormattedMessage(noTime: Boolean = false): String {
            val date = Date(timestamp)
            val timeFormat = SimpleDateFormat("HH:mm")
            val timeString = timeFormat.format(date)
            val roomString = if (room.name == Constants.mainRoomName) "" else ("@" + room.name + " ")

            return  roomString + (if (noTime) "" else "[$timeString] ") + (user.username + ": ") + message
        }

        /**
         * Returns the complete message data for client parsing
         */
        fun toDataMessage(): String {
            return Constants.roomSelectionPrefix + room.name + "$" + timestamp + "+" + user.username + " " + message
        }

        /**
         * Returns the message without information on the time it was received, as string
         */
        fun toTextMessage(): String {
            if (room.name == Constants.mainRoomName)
                return message + " from " + user.username
            else
                return message + " from " + user.username + " to " + room.name
        }

        /**
         * Returns all the information on the message, as string
         */
        fun toExtendedTextMessage(): String {
            val date = Date(timestamp)
            val dateFormat = SimpleDateFormat("dd-MM-yyyy")
            val timeFormat = SimpleDateFormat("HH:mm:ss.SSS")
            val dateString = dateFormat.format(date)
            val timeString = timeFormat.format(date)

            assert(dateString != null)

            return Constants.roomSelectionPrefix +
                    room.name.padEnd(Constants.roomAlignmentPadding, ' ') +
                    " [$dateString $timeString] " +
                    (user.username + ": ") +
                    message
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
    fun queryText(text: String?): ChatHistory = ChatHistory(history.filter {text == null || it.message.contains(text)})

    /**
     * Get all the entries of which message contains the specified text, user and room. If a parameter is null, the
     * filtering with respect to that parameter is going to be ignored.
     */
    fun query(text: String? = null, user: ChatUser? = null, room: ChatRoom? = null, startTimestamp: Long? = null, endTimestamp: Long? = null): ChatHistory {

        val filteredByText = queryText(text).getAll()
        val filteredByUser = filteredByText.filter {user == null || it.user == user}
        val filteredByRoom = filteredByUser.filter {room == null || it.room == room}
        val filteredByStartTimestamp = filteredByRoom.filter {startTimestamp == null ||  it.timestamp >= startTimestamp }
        val filteredByEndTimestamp = filteredByStartTimestamp.filter {endTimestamp == null ||  it.timestamp <= endTimestamp }

        return ChatHistory(filteredByEndTimestamp)
    }

    /**
     * Get all the entries of which message contains the specified text, username and room name (as strings).
     * If a parameter is null, the filtering with respect to that parameter is going to be ignored.
     */
    fun query(text: String?, username: String?, roomName: String?, startTimestamp: String?, endTimestamp: String?): ChatHistory {

        val filteredByText = queryText(text).getAll()
        val filteredByUser = filteredByText.filter {username == null || it.user.username == username}
        val filteredByRoom = filteredByUser.filter {roomName == null || it.room.name == roomName}
        val filteredByStartTimestamp = filteredByRoom.filter {
            val start = startTimestamp?.toLongOrNull()
            start == null || it.timestamp >= start
        }
        val filteredByEndTimestamp = filteredByStartTimestamp.filter {
            val end = endTimestamp?.toLongOrNull()
            end == null || it.timestamp <= end
        }

        return ChatHistory(filteredByEndTimestamp)
    }

}