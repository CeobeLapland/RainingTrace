package com.rainingtrace.domain.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /**
     * 六边形的 6 个顶点（pointy-top，逆时针，首尾不重复）。
     * 用于地图 overlay 渲染。
     */
    fun cellPolygon(cell: HexCellId): List<WorldCoordinate> {
        val cx = cellSizeMeters * SQRT3 * (cell.axialQ + cell.axialR / 2.0)
        val cy = cellSizeMeters * 1.5 * cell.axialR
        return (0 until 6).map { i ->
            val angleDeg = 60.0 * i - 30.0 // pointy-top
            val rad = Math.toRadians(angleDeg)
            toCoordinate(
                cx + cellSizeMeters * cos(rad),
                cy + cellSizeMeters * sin(rad),
            )
        }
    }

    /**
     * 以某真实坐标为圆心、米制半径内的格子：判定**圆与六边形相交**
     * （圆心到六边形多边形的最短距离 ≤ 半径），而不是格中心距离——
     * 否则在 40m 格上 60m 视野会跨不过任何邻格。
     *
     * 战争迷雾用：视野是现实距离，格子只是表现。
     */
    fun cellsWithinMeters(
        coordinate: WorldCoordinate,
        radiusMeters: Double,
    ): List<HexCellId> {
        require(radiusMeters >= 0.0) { "radiusMeters must be >= 0, got $radiusMeters" }
        val centerCell = cellOf(coordinate)
        if (radiusMeters == 0.0) return listOf(centerCell)
        val (px, py) = toMeters(coordinate)
        // 候选环上界：外接圆直径 + 半径，按行距 1.5*size 换算，再多取一环保险。
        val ringBound = ceil((radiusMeters + 2.0 * cellSizeMeters) / (1.5 * cellSizeMeters)).toInt() + 1
        return cellsWithinRadius(centerCell, ringBound).filter { cell ->
            cell == centerCell || distancePointToCell(px, py, cell) <= radiusMeters
        }
    }

    /** 点（米制局部坐标）到某格六边形多边形的最短距离；点在格内为 0。 */
    private fun distancePointToCell(px: Double, py: Double, cell: HexCellId): Double {
        val verts = cellVerticesMeters(cell)
        if (isInsideConvexPolygon(px, py, verts)) return 0.0
        var best = Double.MAX_VALUE
        for (i in verts.indices) {
            val a = verts[i]
            val b = verts[(i + 1) % verts.size]
            best = minOf(best, distancePointToSegment(px, py, a.first, a.second, b.first, b.second))
        }
        return best
    }

    private fun cellVerticesMeters(cell: HexCellId): List<Pair<Double, Double>> {
        val cx = cellSizeMeters * SQRT3 * (cell.axialQ + cell.axialR / 2.0)
        val cy = cellSizeMeters * 1.5 * cell.axialR
        return (0 until 6).map { i ->
            val angleDeg = 60.0 * i - 30.0 // pointy-top
            val rad = Math.toRadians(angleDeg)
            (cx + cellSizeMeters * cos(rad)) to (cy + cellSizeMeters * sin(rad))
        }
    }

    /** 顶点按 CCW 给出：点在每条边左侧即位于凸多边形内部。 */
    private fun isInsideConvexPolygon(
        px: Double,
        py: Double,
        verts: List<Pair<Double, Double>>,
    ): Boolean = verts.indices.all { i ->
        val a = verts[i]
        val b = verts[(i + 1) % verts.size]
        (b.first - a.first) * (py - a.second) - (b.second - a.second) * (px - a.first) >= 0.0
    }

    private fun distancePointToSegment(
        px: Double,
        py: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
    ): Double {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val closestX = ax + t * dx
        val closestY = ay + t * dy
        return hypot(px - closestX, py - closestY)
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
