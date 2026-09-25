package com.xzo.agent.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert
    suspend fun insert(c: ConversationEntity): Long

    @Update
    suspend fun update(c: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE conversations SET modelId = :modelId WHERE id = :id")
    suspend fun setModel(id: Long, modelId: String)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun observeAllOnce(): List<ConversationEntity>
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Update
    suspend fun update(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY id ASC")
    fun observeFor(cid: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid ORDER BY id ASC")
    suspend fun listFor(cid: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun clearConversation(cid: Long)

    @Query(
        "SELECT m.* FROM messages m WHERE m.content LIKE '%' || :q || '%' " +
            "ORDER BY m.createdAt DESC LIMIT 60"
    )
    suspend fun search(q: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun count(): Int

    @Query("SELECT SUM(promptTokens + completionTokens) FROM messages")
    suspend fun totalTokens(): Int?
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(m: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE key = :key LIMIT 1")
    suspend fun get(key: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE key LIKE '%' || :q || '%' OR value LIKE '%' || :q || '%' LIMIT 20")
    suspend fun search(q: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM memories")
    suspend fun clear()
}

@Dao
interface ArtifactDao {
    @Insert
    suspend fun insert(a: ArtifactEntity): Long

    @Query("SELECT * FROM artifacts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE conversationId = :cid ORDER BY createdAt DESC")
    fun observeFor(cid: Long): Flow<List<ArtifactEntity>>

    @Query("DELETE FROM artifacts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AutomationDao {
    @Insert
    suspend fun insert(a: AutomationEntity): Long

    @Update
    suspend fun update(a: AutomationEntity)

    @Query("SELECT * FROM automations ORDER BY hour, minute")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations ORDER BY hour, minute")
    suspend fun all(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun byId(id: Long): AutomationEntity?

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: Long)
}
