package org.armman.supervisor.data.inventory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface InventoryItemCacheDao {
  @Query("SELECT * FROM inventory_item_cache ORDER BY itemName ASC")
  suspend fun getAll(): List<InventoryItemCacheEntity>

  @Query("DELETE FROM inventory_item_cache")
  suspend fun deleteAll()

  @Insert
  suspend fun insertAll(items: List<InventoryItemCacheEntity>)

  @Transaction
  suspend fun replaceAll(items: List<InventoryItemCacheEntity>) {
    deleteAll()
    if (items.isNotEmpty()) insertAll(items)
  }
}
