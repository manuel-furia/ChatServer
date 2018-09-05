import java.util.*

data class ChatRoom (val name: String,
                val greeting: String = "",
                val users: Set<ChatUser> = setOf(),
                val whitelist: Set<ChatUser> = setOf(),
                val blacklist: Set<ChatUser> = setOf(),
                val permissions: Map<ChatUser, UserPermissions> = mapOf(),
                val defaultPermission: UserPermissions = UserPermissions.VOICE) {

    enum class UserPermissions {
        NONE, READ, VOICE, MOD, ADMIN
    }

    fun filterJoinedUsers(userList: Set<ChatUser>): Set<ChatUser> {
        return userList.intersect(users)
    }

    fun isUserInRoom(user: ChatUser): Boolean = users.contains(user)

    fun setTopic(topic: String): ChatRoom {
        return this.copy(greeting = topic)
    }

    fun userJoin(user: ChatUser): ChatRoom {
        if ((whitelist.size == 0 || whitelist.contains(user)) && !blacklist.contains(user)) {

            val permission = if (users.size == 0 || user.level == ChatUser.Level.ADMIN)
                UserPermissions.ADMIN
            else
                defaultPermission

            return ChatRoom(name, users = users + user, permissions = permissions + (user to permission))
        } else {
            return this
        }
    }

    fun userLeave(user: ChatUser): ChatRoom {
        return ChatRoom(name, users = users - user)
    }

    fun setPermissions(user: ChatUser, permission: UserPermissions): ChatRoom {
        return ChatRoom(name, permissions = permissions + Pair(user, permission))
    }

    fun grantPermissionsFrom(userFrom: ChatUser, userTo: ChatUser, permission: UserPermissions): ChatRoom {
        val userFromPermission = permissions.getOrDefault(userFrom, defaultPermission)
        val userToPermission = permissions.getOrDefault(userFrom, defaultPermission)

        if (userFromPermission >= userToPermission) {
            return ChatRoom(name, permissions = permissions + Pair(userTo, permission))
        } else {
            return this
        }
    }

    fun blacklistAdd(user: ChatUser): ChatRoom = ChatRoom(name, blacklist = blacklist + user)
    fun whitelistAdd(user: ChatUser): ChatRoom = ChatRoom(name, whitelist = whitelist + user)
    fun blacklistRemove(user: ChatUser): ChatRoom = ChatRoom(name, blacklist = blacklist - user)
    fun whitelistRemove(user: ChatUser): ChatRoom = ChatRoom(name, whitelist = whitelist - user)
    fun blacklistClear(user: ChatUser): ChatRoom = ChatRoom(name, blacklist = setOf())
    fun whitelistClear(user: ChatUser): ChatRoom = ChatRoom(name, blacklist = setOf())

    fun blacklistAddFrom(userFrom: ChatUser, user: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, blacklist = blacklist + user)
        else
            return this
    }

    fun blacklistRemoveFrom(userFrom: ChatUser, user: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, blacklist = blacklist - user)
        else
            return this
    }

    fun whitelistAddFrom(userFrom: ChatUser, user: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, blacklist = whitelist + user)
        else
            return this
    }

    fun whitelistRemoveFrom(userFrom: ChatUser, user: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, blacklist = whitelist - user)
        else
            return this
    }

    fun blacklistClearFrom(userFrom: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, blacklist = setOf())
        else
            return this
    }

    fun whitelistClearFrom(userFrom: ChatUser): ChatRoom {
        if (canUserBan(userFrom))
            return ChatRoom(name, whitelist = setOf())
        else
            return this
    }

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