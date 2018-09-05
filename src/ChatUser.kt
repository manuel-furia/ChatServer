import java.util.*

data class ChatUser (val username: String, val level: Level) {

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