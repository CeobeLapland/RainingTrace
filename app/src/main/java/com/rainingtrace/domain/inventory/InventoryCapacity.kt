package com.rainingtrace.domain.inventory

/**
 * 背包软容量的填充状态（**只提示，不阻止**）。
 *
 * 两种"上限"分工要钉死，否则会互相打脸：
 * - [ResourceDefinition.stackLimit] 管**一种资源**的数量（堆叠上限）；
 * - 这里管背包里**种类数**的软上限；仓库不计入。
 */
data class InventoryFill(
    val usedKinds: Int,
    val softLimit: Int,
) {
    init {
        require(usedKinds >= 0) { "usedKinds must not be negative, got $usedKinds" }
        require(softLimit > 0) { "softLimit must be positive, got $softLimit" }
    }

    val isOver: Boolean get() = usedKinds >= softLimit
}

/** 背包软上限（种类数）。给得宽松：满了只是提示"建议存入仓库"，不挡采集。 */
const val BACKPACK_SOFT_KIND_LIMIT = 24

fun inventoryFill(
    ownedKinds: Int,
    softLimit: Int = BACKPACK_SOFT_KIND_LIMIT,
): InventoryFill = InventoryFill(usedKinds = ownedKinds.coerceAtLeast(0), softLimit = softLimit)