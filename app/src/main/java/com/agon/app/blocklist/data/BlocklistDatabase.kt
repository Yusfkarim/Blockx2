package com.agon.app.blocklist.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "rules")
data class RuleEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val label: String,
    val type: String,
    val listMode: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** Epoch millis when this rule expires; 0 means it never expires (permanent). */
    val blockUntil: Long = 0,
)

@Entity(tableName = "blocklist_settings")
data class BlocklistSettingsEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
)

@Entity(tableName = "rule_audit_logs")
data class RuleAuditEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val value: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "",
    val browserPackage: String = "",
)

@Entity(tableName = "ai_category_configs")
data class AiCategoryConfigEntity(
    @androidx.room.PrimaryKey val category: String,
    val enabled: Boolean = true,
    val threshold: Int = 72,
)

@Entity(tableName = "ai_block_events")
data class AiBlockEventEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val domain: String,
    val category: String,
    val confidence: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val browserPackage: String,
)

@Dao
interface BlocklistDao {
    @Query("SELECT * FROM rules") fun observeRules(): Flow<List<RuleEntity>>
    @Query("SELECT * FROM rules") suspend fun getRules(): List<RuleEntity>
    @Query("SELECT * FROM blocklist_settings WHERE id = 1") fun observeSettings(): Flow<BlocklistSettingsEntity?>
    @Query("SELECT * FROM blocklist_settings WHERE id = 1") suspend fun getSettings(): BlocklistSettingsEntity?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRules(items: List<RuleEntity>): List<Long>
    @Update suspend fun updateRule(item: RuleEntity)
    @Query("DELETE FROM rules WHERE id IN (:ids)") suspend fun deleteRules(ids: List<Long>)
    @Insert suspend fun insertAudit(item: RuleAuditEntity)
    @Insert suspend fun insertAiEvent(item: AiBlockEventEntity)
    @Query("SELECT * FROM ai_category_configs") fun observeAiConfigs(): Flow<List<AiCategoryConfigEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAiConfig(item: AiCategoryConfigEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(item: BlocklistSettingsEntity)
}

@Database(entities = [RuleEntity::class, BlocklistSettingsEntity::class, RuleAuditEntity::class, AiCategoryConfigEntity::class, AiBlockEventEntity::class], version = 4, exportSchema = true)
abstract class BlocklistDatabase : RoomDatabase() { abstract fun dao(): BlocklistDao }
