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
) {
    init {
        require(id.isNotBlank()) { "resource id must not be blank" }
        require(name.isNotBlank()) { "resource name must not be blank" }
    }
}
