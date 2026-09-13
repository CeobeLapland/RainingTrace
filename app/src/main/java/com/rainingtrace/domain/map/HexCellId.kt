package com.rainingtrace.domain.map

/**
 * RT-DOM-001: 六边形单元的确定性标识。
 *
 * 使用 axial coordinates (q, r)，等价于 cube (x=q, z=r, y=-x-z)。
 * 字符串形式稳定可序列化，用于事件 payload / DB 存储。
 */
data class HexCellId(
    val axialQ: Int,
    val axialR: Int,
) {
    /** 稳定文本形式，用于 JSON 契约与持久化。 */
    fun toStableString(): String = "H:$axialQ:$axialR"

    companion object {
        fun fromStableString(text: String): HexCellId {
            val parts = text.split(':')
            require(parts.size == 3 && parts[0] == "H") { "invalid hex cell id: $text" }
            return HexCellId(
                axialQ = parts[1].toIntOrNull() ?: error("invalid hex cell id: $text"),
                axialR = parts[2].toIntOrNull() ?: error("invalid hex cell id: $text"),
            )
        }
    }
}
