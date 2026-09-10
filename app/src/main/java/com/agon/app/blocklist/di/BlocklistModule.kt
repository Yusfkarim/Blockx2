package com.agon.app.blocklist.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agon.app.blocklist.data.BlocklistDao
import com.agon.app.blocklist.data.BlocklistDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BlocklistModule {
    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rule_audit_logs ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE rule_audit_logs ADD COLUMN browserPackage TEXT NOT NULL DEFAULT ''")
        }
    }
    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS ai_category_configs (category TEXT NOT NULL, enabled INTEGER NOT NULL, threshold INTEGER NOT NULL, PRIMARY KEY(category))")
            db.execSQL("CREATE TABLE IF NOT EXISTS ai_block_events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT NOT NULL, domain TEXT NOT NULL, category TEXT NOT NULL, confidence INTEGER NOT NULL, timestamp INTEGER NOT NULL, browserPackage TEXT NOT NULL)")
        }
    }
    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-rule expiry: 0 means the rule never expires (existing rules stay permanent).
            db.execSQL("ALTER TABLE rules ADD COLUMN blockUntil INTEGER NOT NULL DEFAULT 0")
        }
    }
    @Provides @Singleton fun database(@ApplicationContext context: Context): BlocklistDatabase = Room.databaseBuilder(context, BlocklistDatabase::class.java, "blocklist.db").addMigrations(migration1To2, migration2To3, migration3To4).build()
    @Provides fun dao(database: BlocklistDatabase): BlocklistDao = database.dao()
}
