package com.rainingtrace.domain.craft

/** 配方的一项输入（要从背包里扣掉的资源与数量）。 */
data class RecipeInput(
    val resourceId: String,
    val amount: Int,
) {
    init {
        require(resourceId.isNotBlank()) { "input resourceId must not be blank" }
        require(amount > 0) { "input amount must be positive, got $amount" }
    }
}

/** 配方的产出（做好之后发放的资源与数量）。 */
data class RecipeOutput(
    val resourceId: String,
    val amount: Int,
) {
    init {
        require(resourceId.isNotBlank()) { "output resourceId must not be blank" }
        require(amount > 0) { "output amount must be positive, got $amount" }
    }
}

/**
 * 配方：确定性的"输入 → 输出"（GDD §11 加工）。
 *
 * 与 `ResourceYieldRule` **刻意保持两套**：后者是"世界条件 → 单向产出"
 * （无消耗、绑地点、有冷却）；配方无地点、无冷却，输入必须真的从背包扣掉。
 * 强行统一会让两者的语义都变形。
 */
data class Recipe(
    val id: String,
    val inputs: List<RecipeInput>,
    val output: RecipeOutput,
) {
    init {
        require(id.isNotBlank()) { "recipe id must not be blank" }
        require(inputs.isNotEmpty()) { "recipe $id needs at least one input" }
        // 同一资源出现两次是内容笔误（应该合并数量），挡在这里而不是悄悄算两次。
        require(inputs.distinctBy { it.resourceId }.size == inputs.size) {
            "recipe $id has duplicate input resource"
        }
    }
}

/** 配方目录：运行时由 `ContentRecipeCatalog` 读内容索引；这个实现留给测试。 */
interface RecipeCatalog {
    fun all(): List<Recipe>

    fun byId(id: String): Recipe?
}

/**
 * 内存实现：配方全部来自构造参数。
 *
 * 内容本体住在 `assets/content/recipes.json`（+ 私有目录覆盖层）。
 */
class InMemoryRecipeCatalog(
    recipes: List<Recipe>,
) : RecipeCatalog {

    private val byId: Map<String, Recipe> = recipes.associateBy { it.id }

    override fun all(): List<Recipe> = byId.values.sortedBy { it.id }

    override fun byId(id: String): Recipe? = byId[id]
}