import java.util.*

/**
 * Represents a room in the server. The messages in the room are visible only to users that joined it.
 */
data class ChatRoom (val name: String,
                val greeting: String = "", //Message to be shown when a user logs in
                val users: Set<ChatUser> = setOf(),
                val whitelist: Set<ChatUser> = setOf(), //If not empty, only listed users can join the room
                val blacklist: Set<ChatUser> = setOf(), //Listed users can not join the room
                val permissions: Map<ChatUser, UserPermissions> = mapOf(),
                val defaultPermission: UserPermissions = UserPermissions.VOICE) {

    enum class UserPermissions {
        NONE, READ, VOICE, MOD, ADMIN
    }

    /**
     * Take a list uf users and return only the ones of those that are in the room
     */
    fun filterJoinedUsers(userList: Set<ChatUser>): Set<ChatUser> {
        return userList.intersect(users)
    }

    /**
     * Return true if a user is in the room, false otherwise
     */
    fun isUserInRoom(user: ChatUser): Boolean = users.contains(user)

    /**
     * Returns a room with the specified greeting message to show when a user joins
     */
    fun setTopic(topic: String): ChatRoom {
        return this.copy(greeting = topic)
    }

    /**
     * Returns a room with added the specified user, if possible, otherwise returns the old room
     */
    fun userJoin(user: ChatUser): ChatRoom {
        //Check if whitelisted or not blacklisted
        if ((whitelist.size == 0 || whitelist.contains(user)) && !blacklist.contains(user)) {

            //Check if the user is server admin. If he is, make it also room admin
            val permission = if (users.size == 0 || user.level == ChatUser.Level.ADMIN)
                UserPermissions.ADMIN
            else
                defaultPermission

            return this.copy(name, users = users + user, permissions = permissions + (user to permission))
        } else {
            return this
        }
    }

    /**
     * Remove the user from the room
     */
    fun userLeave(user: ChatUser): ChatRoom {
        return this.copy(name, users = users - user)
    }

    fun setPermissions(user: ChatUser, permission: UserPermissions): ChatRoom {
        return this.copy(name, permissions = permissions + Pair(user, permission))
    }

    /**
     * A user granting permissions to another user with lower permissions
     */
    fun grantPermissionsFrom(userFrom: ChatUser, userTo: ChatUser, permission: UserPermissions): ChatRoom {
        val userFromPermission = permissions.getOrDefault(userFrom, defaultPermission)
        val userToPermission = permissions.getOrDefault(userFrom, defaultPermission)

        if (userFromPermission >= userToPermission) {
            return this.copy(name, permissions = permissions + Pair(userTo, permission))
        } else {
            return this
        }
    }

    //The following methods manipulate the whitelist and blacklist lists.

    fun blacklistAdd(user: ChatUser): ChatRoom = this.copy(name, blacklist = blacklist + user)
    fun whitelistAdd(user: ChatUser): ChatRoom = this.copy(name, whitelist = whitelist + user)
    fun blacklistRemove(user: ChatUser): ChatRoom = this.copy(name, blacklist = blacklist - user)
    fun whitelistRemove(user: ChatUser): ChatRoom = this.copy(name, whitelist = whitelist - user)
    fun blacklistClear(user: ChatUser): ChatRoom = this.copy(name, blacklist = setOf())
    fun whitelistClear(user: ChatUser): ChatRoom = this.copy(name, blacklist = setOf())


    fun canUserRead(user: ChatUser) : Boolean{
        return permissions.getOrDefault(user, defaultPermission) >= UserPermissions.READ
    }

    fun canUserWrite(user: ChatUser) : Boolean{
        return permissions.getOrDefault(user, defaultPermission) >= UserPermissions.VOICE
    }

    fun canUserKick(user: ChatUser) : Boolean{
        return permissions.getOrDefault(user, defaultPermission) >= UserPermissions.MOD
    }

    fun canUserBan(user: ChatUser) : Boolean{
        return permissions.getOrDefault(user, defaultPermission) >= UserPermissions.ADMIN
    }

    override fun hashCode(): Int {
        return Objects.hash(name)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ChatRoom

        if (other.name != name) return false

        return true
    }

}