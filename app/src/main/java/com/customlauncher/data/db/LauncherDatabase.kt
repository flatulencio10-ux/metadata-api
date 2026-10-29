package com.customlauncher.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Generic HTTP response cache: widgets store raw JSON bodies by key and parse on read.
 * This keeps the schema stable no matter how many data-driven widgets are added.
 */
@Entity(tableName = "cached_response")
data class CachedResponse(
    @PrimaryKey val key: String,
    val body: String,
    val fetchedAt: Long,
)

@Dao
interface CacheDao {
    @Query("SELECT * FROM cached_response WHERE `key` = :key")
    suspend fun get(key: String): CachedResponse?

    @Query("SELECT * FROM cached_response WHERE `key` = :key")
    fun observe(key: String): Flow<CachedResponse?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CachedResponse)

    @Query("DELETE FROM cached_response WHERE fetchedAt < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(entities = [CachedResponse::class], version = 1, exportSchema = false)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        fun create(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
