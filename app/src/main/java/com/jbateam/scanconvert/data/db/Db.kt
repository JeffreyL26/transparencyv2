package com.jbateam.scanconvert.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Datenmodell (Handoff §7):
 * TravelList { id, name, currency, budget?, items } / ListItem { id, raw, from, value, ts }.
 * `value` ist zum Scan-Zeitpunkt fixiert und wird nicht neu umgerechnet.
 */
@Entity(tableName = "lists")
data class TravelListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val budget: Double?,
    /** Neuere Listen zuerst (Prototyp stellt neue Listen voran). */
    val createdAt: Long,
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = TravelListEntity::class,
        parentColumns = ["id"],
        childColumns = ["listId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("listId")],
)
data class ListItemEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val raw: Double,
    val fromCode: String,
    val value: Double,
    val ts: Long,
    /** Optionaler Name der Position; null = unbenannt. */
    val label: String? = null,
)

data class ListWithItems(
    @Embedded val list: TravelListEntity,
    @Relation(parentColumn = "id", entityColumn = "listId")
    val items: List<ListItemEntity>,
)

@Dao
interface ListsDao {

    @Transaction
    @Query("SELECT * FROM lists ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ListWithItems>>

    @Query("SELECT COUNT(*) FROM lists")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: TravelListEntity)

    @Update
    suspend fun updateList(list: TravelListEntity)

    @Query("SELECT * FROM lists WHERE id = :id")
    suspend fun getList(id: String): TravelListEntity?

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ListItemEntity)

    @Query("UPDATE items SET label = :label WHERE id = :id")
    suspend fun updateItemLabel(id: String, label: String?)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: String)
}

@Database(entities = [TravelListEntity::class, ListItemEntity::class], version = 2, exportSchema = false)
abstract class FxDatabase : RoomDatabase() {
    abstract fun listsDao(): ListsDao
}

/** v1 → v2: optionale Spalte `label` für benannte Positionen (§3). */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE items ADD COLUMN label TEXT")
    }
}
