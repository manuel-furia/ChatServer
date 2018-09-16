//Author: Manuel Furia
//Student ID: 1706247

/* ChatUser.kt
 * Stores username and server wide level of a user.
 */

import java.util.*

data class ChatUser (val username: String, val level: Level) {

    /**
     * Authentication level in the server.
     * UNKNOWN -> Anonymous user that just joined
     * NORMAL -> User that set their username with the :user command
     * ADMIN -> User that logged in as server admin
     *          (automatically given admin permissions in every room,
     *          can see all the messages from any room, kick and ban from the server)
     */
    enum class Level {
        UNKNOWN, NORMAL, ADMIN
    }

    override fun hashCode(): Int {
        return Objects.hash(username)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ChatUser

        if (other.username != username) return false

        return true
    }


}