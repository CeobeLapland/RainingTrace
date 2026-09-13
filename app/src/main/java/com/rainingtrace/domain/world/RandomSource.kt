package com.rainingtrace.domain.world

/**
 * 显式随机源（见 05_领域模型 §11）。
 *
 * 世界事件必须可复现：生产环境 seed 来自 server，测试固定 seed。
 */
interface RandomSource {
    fun nextInt(bound: Int): Int
}

/** 固定 seed 实现，测试与 Fake World 使用。 */
class SeededRandomSource(seed: Long) : RandomSource {
    private val random = java.util.Random(seed)

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, got $bound" }
        return random.nextInt(bound)
    }
}
