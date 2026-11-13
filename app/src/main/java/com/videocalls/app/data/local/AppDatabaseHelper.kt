package com.videocalls.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.videocalls.app.VideoCallApp
import com.videocalls.app.data.model.ContactItem
import com.videocalls.app.data.model.Group
import com.videocalls.app.data.model.User
import com.videocalls.app.data.util.PhoneNumberHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Date

private const val DB_NAME = "videocalls_local.db"
private const val DB_VERSION = 1

class AppDatabaseHelper(context: Context = VideoCallApp.instance) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        @Volatile
        private var instance: AppDatabaseHelper? = null

        fun getInstance(context: Context = VideoCallApp.instance): AppDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: AppDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _contactsFlow = MutableStateFlow<List<ContactItem>>(emptyList())
    val contactsFlow: StateFlow<List<ContactItem>> = _contactsFlow.asStateFlow()

    private val _groupsFlow = MutableStateFlow<List<Group>>(emptyList())
    val groupsFlow: StateFlow<List<Group>> = _groupsFlow.asStateFlow()

    init {
        // Load initial cached data from SQLite on startup
        refreshContactsFromDb()
        refreshGroupsFromDb()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS contacts_cache (
                phone TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                raw_phone TEXT,
                is_registered INTEGER NOT NULL DEFAULT 0,
                uid TEXT,
                fcm_token TEXT,
                avatar_url TEXT,
                last_seen INTEGER,
                updated_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS groups_cache (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                created_by TEXT NOT NULL,
                member_uids TEXT NOT NULL,
                member_phones TEXT NOT NULL,
                active_call_id TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_contacts_registered ON contacts_cache(is_registered)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS contacts_cache")
        db.execSQL("DROP TABLE IF EXISTS groups_cache")
        onCreate(db)
    }

    // ----------------------------------------------------
    // Contacts Local Operations
    // ----------------------------------------------------

    fun getAllContacts(): List<ContactItem> {
        val list = mutableListOf<ContactItem>()
        val db = readableDatabase
        val cursor: Cursor = db.rawQuery(
            """
            SELECT phone, display_name, raw_phone, is_registered, uid, fcm_token, avatar_url, last_seen
            FROM contacts_cache
            ORDER BY is_registered DESC, display_name COLLATE NOCASE ASC
            """.trimIndent(),
            null
        )

        cursor.use { c ->
            val iPhone = c.getColumnIndexOrThrow("phone")
            val iName = c.getColumnIndexOrThrow("display_name")
            val iRaw = c.getColumnIndexOrThrow("raw_phone")
            val iReg = c.getColumnIndexOrThrow("is_registered")
            val iUid = c.getColumnIndexOrThrow("uid")
            val iFcm = c.getColumnIndexOrThrow("fcm_token")
            val iAvatar = c.getColumnIndexOrThrow("avatar_url")
            val iLastSeen = c.getColumnIndexOrThrow("last_seen")

            while (c.moveToNext()) {
                val phone = c.getString(iPhone) ?: ""
                val name = c.getString(iName) ?: "Unknown"
                val raw = c.getString(iRaw) ?: phone
                val isReg = c.getInt(iReg) == 1
                val uid = c.getString(iUid) ?: ""
                val fcm = c.getString(iFcm) ?: ""
                val avatar = c.getString(iAvatar) ?: ""
                val lastSeenMs = c.getLong(iLastSeen)

                val user = if (isReg && uid.isNotBlank()) {
                    User(
                        uid = uid,
                        phoneNumber = phone,
                        displayName = name,
                        fcmToken = fcm,
                        avatarUrl = avatar,
                        lastSeen = if (lastSeenMs > 0) Date(lastSeenMs) else null
                    )
                } else null

                list.add(
                    ContactItem(
                        contactId = phone,
                        displayName = name,
                        rawPhoneNumber = raw,
                        normalizedPhoneNumber = phone,
                        isRegistered = isReg,
                        registeredUser = user
                    )
                )
            }
        }
        return list
    }

    fun refreshContactsFromDb() {
        val contacts = getAllContacts()
        _contactsFlow.value = contacts
    }

    suspend fun saveDeviceContacts(contacts: List<ContactItem>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT INTO contacts_cache (phone, display_name, raw_phone, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(phone) DO UPDATE SET
                    display_name = excluded.display_name,
                    raw_phone = excluded.raw_phone,
                    updated_at = excluded.updated_at
                """.trimIndent()
            )
            val now = System.currentTimeMillis()
            for (c in contacts) {
                stmt.bindString(1, c.normalizedPhoneNumber)
                stmt.bindString(2, c.displayName)
                stmt.bindString(3, c.rawPhoneNumber)
                stmt.bindLong(4, now)
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshContactsFromDb()
    }

    suspend fun updateRegisteredUsers(users: List<User>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val stmt = db.compileStatement(
                """
                INSERT INTO contacts_cache (phone, display_name, raw_phone, is_registered, uid, fcm_token, avatar_url, last_seen, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?)
                ON CONFLICT(phone) DO UPDATE SET
                    is_registered = 1,
                    uid = excluded.uid,
                    fcm_token = excluded.fcm_token,
                    avatar_url = excluded.avatar_url,
                    last_seen = excluded.last_seen,
                    updated_at = excluded.updated_at
                """.trimIndent()
            )
            val now = System.currentTimeMillis()
            for (u in users) {
                val cleanPhone = PhoneNumberHelper.cleanIndianNumber(u.phoneNumber)
                if (cleanPhone.length == 10) {
                    stmt.bindString(1, cleanPhone)
                    stmt.bindString(2, u.displayName.ifBlank { "User ${cleanPhone.takeLast(4)}" })
                    stmt.bindString(3, cleanPhone)
                    stmt.bindString(4, u.uid)
                    stmt.bindString(5, u.fcmToken)
                    stmt.bindString(6, u.avatarUrl)
                    stmt.bindLong(7, u.lastSeen?.time ?: now)
                    stmt.bindLong(8, now)
                    stmt.executeInsert()
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshContactsFromDb()
    }

    // ----------------------------------------------------
    // Groups Local Operations
    // ----------------------------------------------------

    fun getAllGroups(): List<Group> {
        val list = mutableListOf<Group>()
        val db = readableDatabase
        val cursor: Cursor = db.rawQuery(
            """
            SELECT id, name, created_by, member_uids, member_phones, active_call_id, created_at
            FROM groups_cache
            ORDER BY created_at DESC
            """.trimIndent(),
            null
        )

        cursor.use { c ->
            val iId = c.getColumnIndexOrThrow("id")
            val iName = c.getColumnIndexOrThrow("name")
            val iCreatedBy = c.getColumnIndexOrThrow("created_by")
            val iMemberUids = c.getColumnIndexOrThrow("member_uids")
            val iMemberPhones = c.getColumnIndexOrThrow("member_phones")
            val iActiveCall = c.getColumnIndexOrThrow("active_call_id")
            val iCreatedAt = c.getColumnIndexOrThrow("created_at")

            while (c.moveToNext()) {
                val uids = (c.getString(iMemberUids) ?: "").split(",").filter { it.isNotBlank() }
                val phones = (c.getString(iMemberPhones) ?: "").split(",").filter { it.isNotBlank() }
                val createdAtMs = c.getLong(iCreatedAt)

                list.add(
                    Group(
                        id = c.getString(iId) ?: "",
                        name = c.getString(iName) ?: "Group",
                        createdBy = c.getString(iCreatedBy) ?: "",
                        memberUids = uids,
                        memberPhones = phones,
                        activeCallId = c.getString(iActiveCall),
                        createdAt = if (createdAtMs > 0) Date(createdAtMs) else null
                    )
                )
            }
        }
        return list
    }

    fun refreshGroupsFromDb() {
        val groups = getAllGroups()
        _groupsFlow.value = groups
    }

    fun deleteGroup(groupId: String) {
        val db = writableDatabase
        db.delete("groups_cache", "id = ?", arrayOf(groupId))
        refreshGroupsFromDb()
    }

    suspend fun saveGroups(groups: List<Group>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM groups_cache")
            val stmt = db.compileStatement(
                """
                INSERT INTO groups_cache (id, name, created_by, member_uids, member_phones, active_call_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )
            for (g in groups) {
                stmt.bindString(1, g.id)
                stmt.bindString(2, g.name)
                stmt.bindString(3, g.createdBy)
                stmt.bindString(4, g.memberUids.joinToString(","))
                stmt.bindString(5, g.memberPhones.joinToString(","))
                stmt.bindString(6, g.activeCallId ?: "")
                stmt.bindLong(7, g.createdAt?.time ?: System.currentTimeMillis())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshGroupsFromDb()
    }
}
