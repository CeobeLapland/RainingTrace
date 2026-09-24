package com.rainingtrace.domain.inventory

/**
 * 仓库：家的存储。
 *
 * 对上层就是"另一个 [InventoryRepository]"——同形、同一个真相类型（[InventoryState]）；
 * 额外提供一个**跨两表的事务写入**，供搬运使用。
 *
 * 为什么不复用 `inventory_items` 加一列 `container`：既有的 `replaceAll`
 * 是全表删除再写入，写背包时会把仓库行一起删掉。分表则一行既有代码都不用碰。
 */
interface WarehouseRepository : InventoryRepository {
    /**
     * 一次事务里同时写背包与仓库：搬运要么全成、要么全不成。
     * （两次顺序 `saveState` 会在中途崩溃时丢东西或凭空多出来。）
     */
    suspend fun replaceBoth(inventory: InventoryState, warehouse: InventoryState)
}