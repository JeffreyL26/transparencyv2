package com.transparency.fxlens.data

import com.transparency.fxlens.data.db.ListItemEntity
import com.transparency.fxlens.data.db.ListsDao
import com.transparency.fxlens.data.db.TravelListEntity
import com.transparency.fxlens.domain.ListItem
import com.transparency.fxlens.domain.TravelList
import com.transparency.fxlens.domain.convert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ListsRepository(private val dao: ListsDao) {

    fun observeLists(): Flow<List<TravelList>> = dao.observeLists().map { rows ->
        rows.map { row ->
            TravelList(
                id = row.list.id,
                name = row.list.name,
                currency = row.list.currency,
                budget = row.list.budget,
                items = row.items.sortedBy { it.ts }.map { it.toDomain() },
            )
        }
    }

    /** Demo-Seed beim ersten Start, wenn keine Listen existieren (§6.1). */
    suspend fun seedIfEmpty(rates: Map<String, Double>) {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()

        suspend fun seedList(name: String, currency: String, budget: Double, createdAt: Long, items: List<Pair<Double, String>>) {
            val listId = uid()
            dao.insertList(TravelListEntity(listId, name, currency, budget, createdAt))
            items.forEachIndexed { i, (raw, from) ->
                dao.insertItem(
                    ListItemEntity(
                        id = uid(), listId = listId, raw = raw, fromCode = from,
                        value = convert(raw, from, currency, rates), ts = now + i,
                    )
                )
            }
        }

        // Reihenfolge wie im Prototyp: „USA Roadtrip" zuerst (createdAt DESC).
        seedList(
            "USA Roadtrip", "USD", 1500.0, now,
            listOf(12.9 to "EUR", 8.5 to "EUR", 34.0 to "EUR", 22.4 to "EUR"),
        )
        seedList(
            "Hongkong Reise", "EUR", 800.0, now - 1,
            listOf(188.0 to "HKD", 65.0 to "HKD", 240.0 to "HKD", 42.0 to "HKD"),
        )
    }

    /** Legt eine Liste an (optional mit erster Position) und liefert ihre id. */
    suspend fun createList(name: String, currency: String, budget: Double?, firstItem: ListItem? = null): String {
        val id = uid()
        dao.insertList(TravelListEntity(id, name, currency, budget, System.currentTimeMillis()))
        firstItem?.let { dao.insertItem(it.toEntity(id)) }
        return id
    }

    suspend fun updateList(id: String, name: String, budget: Double?) {
        val current = dao.getList(id) ?: return
        dao.updateList(current.copy(name = name, budget = budget))
    }

    suspend fun deleteList(id: String) = dao.deleteList(id)

    suspend fun addItem(listId: String, item: ListItem) = dao.insertItem(item.toEntity(listId))

    suspend fun updateItemLabel(itemId: String, label: String?) = dao.updateItemLabel(itemId, label)

    suspend fun deleteItem(itemId: String) = dao.deleteItem(itemId)

    private fun uid(): String = UUID.randomUUID().toString().take(8)

    private fun ListItemEntity.toDomain() = ListItem(id, raw, fromCode, value, ts, label)

    private fun ListItem.toEntity(listId: String) =
        ListItemEntity(id = id, listId = listId, raw = raw, fromCode = from, value = value, ts = ts, label = label)
}
