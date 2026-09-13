package com.rainingtrace.domain.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * 六边形网格（P0：单一固定精度）。
 *
 * 布局：pointy-top axial coordinates（q, r），等价 cube (x=q, z=r, y=-x-z)。
 * 投影：以 [origin] 为切点的等距圆柱近似（校园尺度误差可忽略），
 *       把 WGS84 经纬度映射为局部米制平面，再按 [cellSizeMeters]（外接圆半径）离散化。
 *
 * 纯 Kotlin、无 Android 依赖；同一坐标永远得到同一 cell（确定性）。
 */
class HexGrid(
    val origin: WorldCoordinate,
    val cellSizeMeters: Double,
) {
    init {
        require(cellSizeMeters > 0.0) { "cellSizeMeters must be positive" }
    }

    private val originLatRad = Math.toRadians(origin.latDegrees)

    /** WGS84 → 局部米制平面（x 向东，y 向北）。 */
    fun toMeters(coordinate: WorldCoordinate): Pair<Double, Double> {
        val dLat = Math.toRadians(coordinate.latDegrees - origin.latDegrees)
        val dLng = Math.toRadians(coordinate.lngDegrees - origin.lngDegrees)
        val x = EARTH_RADIUS_METERS * cos(originLatRad) * dLng
        val y = EARTH_RADIUS_METERS * dLat
        return x to y
    }

    /** 局部米制平面 → WGS84。 */
    fun toCoordinate(xMeters: Double, yMeters: Double): WorldCoordinate {
        val dLat = yMeters / EARTH_RADIUS_METERS
        val dLng = xMeters / (EARTH_RADIUS_METERS * cos(originLatRad))
        return WorldCoordinate(
            latDegrees = origin.latDegrees + Math.toDegrees(dLat),
            lngDegrees = origin.lngDegrees + Math.toDegrees(dLng),
        )
    }

    /** 坐标 → 所在六边形单元。 */
    fun cellOf(coordinate: WorldCoordinate): HexCellId {
        val (x, y) = toMeters(coordinate)
        // pointy-top pixel→axial
        val qFloat = (SQRT3 / 3.0 * x - 1.0 / 3.0 * y) / cellSizeMeters
        val rFloat = (2.0 / 3.0 * y) / cellSizeMeters
        val (q, r) = axialRound(qFloat, rFloat)
        return HexCellId(q, r)
    }

    /** 六边形中心 → WGS84。 */
    fun cellCenter(cell: HexCellId): WorldCoordinate {
        val x = cellSizeMeters * SQRT3 * (cell.axialQ + cell.axialR / 2.0)
        val y = cellSizeMeters * 1.5 * cell.axialR
        return toCoordinate(x, y)
    }

    /** 相邻 6 格。 */
    fun neighbors(cell: HexCellId): List<HexCellId> =
        AXIAL_DIRECTIONS.map { (dq, dr) -> HexCellId(cell.axialQ + dq, cell.axialR + dr) }

    /** cube 距离（格数）。 */
    fun distance(a: HexCellId, b: HexCellId): Int {
        val dx = a.axialQ - b.axialQ
        val dz = a.axialR - b.axialR
        val dy = -dx - dz
        return maxOf(abs(dx), abs(dy), abs(dz))
    }

    /**
     * 以 [center] 为圆心、切比雪夫（hex）半径 [radius] 内的所有单元，
     * 按 (q, r) 稳定排序；radius=0 时仅返回中心格。
     */
    fun cellsWithinRadius(center: HexCellId, radius: Int): List<HexCellId> {
        require(radius >= 0) { "radius must be >= 0, got $radius" }
        val result = ArrayList<HexCellId>((radius + 1) * (radius + 1) * 3)
        for (dq in -radius..radius) {
            val rMin = maxOf(-radius, -dq - radius)
            val rMax = minOf(radius, -dq + radius)
            for (dr in rMin..rMax) {
                result.add(HexCellId(center.axialQ + dq, center.axialR + dr))
            }
        }
        return result.sortedWith(compareBy({ it.axialQ }, { it.axialR }))
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_008.883485042
        private const val SQRT3 = 1.732_050_807_568_877_2

        // axial 六方向：E, NE, NW, W, SE, SW
        private val AXIAL_DIRECTIONS = listOf(
            1 to 0, 1 to -1, 0 to -1,
            -1 to 0, -1 to 1, 0 to 1,
        )

        /** 分数 axial → 最近整数六边形（cube rounding）。 */
        private fun axialRound(qf: Double, rf: Double): Pair<Int, Int> {
            val xf = qf
            val zf = rf
            val yf = -xf - zf

            var x = xf.roundToInt()
            var y = yf.roundToInt()
            var z = zf.roundToInt()

            val xResid = abs(x - xf)
            val yResid = abs(y - yf)
            val zResid = abs(z - zf)

            if (xResid > yResid && xResid > zResid) {
                x = -y - z
            } else if (yResid > zResid) {
                y = -x - z
            } else {
                z = -x - y
            }
            return x to z
        }
    }
}
