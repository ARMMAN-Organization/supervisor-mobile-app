package org.armman.supervisor.data.inventory

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local cache row mirroring the server `inventory_items` shape — repopulated on every
 * successful [InventoryApi.getInventoryItems] call, read as a fallback when offline. */
@Entity(tableName = "inventory_item_cache")
data class InventoryItemCacheEntity(
  @PrimaryKey val id: String,
  val itemCode: String,
  val itemName: String,
  val itemCategory: String,
  val unit: String,
  val status: String,
)
