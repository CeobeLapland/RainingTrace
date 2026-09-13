package com.rainingtrace.domain.inventory

/**
 * RT-DOM-004: 资源定义。
 *
 * 资源不只指自然物：观察记录、记忆碎片同样是"世界资源"（GDD §09）。
 */
enum class ResourceCategory {
    NATURE,
    KNOWLEDGE,
    MEMORY,
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
) {
    init {
        require(id.isNotBlank()) { "resource id must not be blank" }
        require(name.isNotBlank()) { "resource name must not be blank" }
    }
}
