package com.rainingtrace.domain.inventory

/**
 * 资源不只指自然物：观察记录、记忆碎片同样是"世界资源"（GDD §09）。
 * 类别与 GDD §09 的资源表一一对应：自然 / 知识 / 文化 / 记忆 / 异常 / 制造。
 */
enum class ResourceCategory {
    NATURE,
    KNOWLEDGE,
    CULTURE,
    MEMORY,
    ANOMALY,
    CRAFT,
}

enum class Rarity {
    COMMON,
    UNCOMMON,
    RARE,
    ANOMALY,
}

data class ResourceDefinition(
    val id: String,
    val name: String,
    val category: ResourceCategory,
    val rarity: Rarity,
    val tags: Set<String> = emptySet(),
    val description: String = "",
    /**
     * 单个堆叠（同一资源）的数量上限。
     *
     * **只用于展示**，不做硬性阻止：与"背包软容量"分工明确——
     * 这个管一种资源的数量，软容量管背包里的**种类数**（见 `InventoryCapacity`）。
     */
    val stackLimit: Int = DEFAULT_STACK_LIMIT,
) {
    init {
        require(id.isNotBlank()) { "resource id must not be blank" }
        require(name.isNotBlank()) { "resource name must not be blank" }
        require(stackLimit > 0) { "stackLimit must be positive, got $stackLimit" }
    }

    companion object {
        const val DEFAULT_STACK_LIMIT = 99
    }
}
