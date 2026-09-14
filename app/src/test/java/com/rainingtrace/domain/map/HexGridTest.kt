package com.rainingtrace.domain.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexGridTest {

    // 北理工良乡校区北湖附近（配置参考值）
    private val lakeOrigin = WorldCoordinate(latDegrees = 39.7310, lngDegrees = 116.1711)
    private val grid = HexGrid(origin = lakeOrigin, cellSizeMeters = 80.0)

    @Test
    fun `cellOf is deterministic`() {
        val coord = WorldCoordinate(39.7312, 116.1715)
        assertEquals(grid.cellOf(coord), grid.cellOf(coord))
    }

    @Test
    fun `nearby coordinates map to same or adjacent cells`() {
        val a = WorldCoordinate(39.7310, 116.1711)
        val b = WorldCoordinate(39.73101, 116.17111) // ~1.5m 偏移
        val ca = grid.cellOf(a)
        val cb = grid.cellOf(b)
        assertTrue(ca == cb || grid.distance(ca, cb) <= 1)
    }

    @Test
    fun `distant coordinates map to different cells`() {
        val a = WorldCoordinate(39.7310, 116.1711)
        val b = WorldCoordinate(39.7350, 116.1711) // ~445m 北
        assertNotEquals(grid.cellOf(a), grid.cellOf(b))
    }

    @Test
    fun `cell center round-trips to same cell`() {
        val coord = WorldCoordinate(39.7318, 116.1699)
        val cell = grid.cellOf(coord)
        val center = grid.cellCenter(cell)
        assertEquals(cell, grid.cellOf(center))
    }

    @Test
    fun `origin maps to cell 0 0`() {
        assertEquals(HexCellId(0, 0), grid.cellOf(lakeOrigin))
    }

    @Test
    fun `neighbors return six distinct cells at distance one`() {
        val cell = HexCellId(3, -2)
        val neighbors = grid.neighbors(cell)
        assertEquals(6, neighbors.size)
        assertEquals(6, neighbors.distinct().size)
        neighbors.forEach { assertEquals(1, grid.distance(cell, it)) }
    }

    @Test
    fun `distance uses hex metric`() {
        assertEquals(0, grid.distance(HexCellId(0, 0), HexCellId(0, 0)))
        assertEquals(1, grid.distance(HexCellId(0, 0), HexCellId(1, 0)))
        assertEquals(2, grid.distance(HexCellId(0, 0), HexCellId(1, 1)))
        assertEquals(3, grid.distance(HexCellId(0, 0), HexCellId(-1, 3)))
    }

    @Test
    fun `cells within radius zero returns only center`() {
        val cells = grid.cellsWithinRadius(HexCellId(5, -3), 0)
        assertEquals(listOf(HexCellId(5, -3)), cells)
    }

    @Test
    fun `cells within radius one returns seven unique cells`() {
        val center = HexCellId(2, 2)
        val cells = grid.cellsWithinRadius(center, 1)
        assertEquals(7, cells.size)
        assertEquals(7, cells.distinct().size)
        assertTrue(center in cells)
        assertTrue(cells.all { grid.distance(center, it) <= 1 })
    }

    @Test
    fun `cells within radius five has hexagon number`() {
        val radius = 5
        val cells = grid.cellsWithinRadius(HexCellId(0, 0), radius)
        // 六边形数：3r(r+1)+1
        assertEquals(3 * radius * (radius + 1) + 1, cells.size)
        assertEquals(cells.size, cells.distinct().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative radius rejected`() {
        grid.cellsWithinRadius(HexCellId(0, 0), -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonpositive cell size rejected`() {
        HexGrid(origin = lakeOrigin, cellSizeMeters = 0.0)
    }

    @Test
    fun `cell polygon has six distinct vertices around center`() {
        val cell = HexCellId(2, -1)
        val polygon = grid.cellPolygon(cell)
        val center = grid.cellCenter(cell)
        assertEquals(6, polygon.size)
        assertEquals(6, polygon.distinct().size)
        // 顶点到中心距离 ≈ cellSize（80m），允许等距圆柱近似误差
        polygon.forEach { vertex ->
            val d = center.distanceMetersTo(vertex)
            assertTrue("vertex distance $d", d in 79.0..81.0)
        }
    }

    @Test
    fun `stable string round-trips`() {
        val cell = HexCellId(-12, 8)
        assertEquals(cell, HexCellId.fromStableString(cell.toStableString()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid stable string rejected`() {
        HexCellId.fromStableString("R3C-12-08")
    }

    @Test
    fun `cells within zero meters only contains the point cell`() {
        val g = HexGrid(origin = lakeOrigin, cellSizeMeters = 40.0)
        val cells = g.cellsWithinMeters(lakeOrigin, 0.0)
        assertEquals(listOf(HexCellId(0, 0)), cells)
    }

    @Test
    fun `visit radius contains center cell but not the 60m north cell on 40m grid`() {
        val g = HexGrid(origin = lakeOrigin, cellSizeMeters = 40.0)
        val cells = g.cellsWithinMeters(lakeOrigin, 30.0)
        assertTrue(HexCellId(0, 0) in cells)
        assertTrue(HexCellId(0, -1) !in cells) // 中心在正北 60m
    }

    @Test
    fun `sight radius intersects all six neighbors on 40m grid`() {
        // 邻格中心 √3*40 ≈ 69.3m，但格间缝隙只有 34.6m；60m 圆与六个邻格都相交，
        // 而两环格最近点 80m，不相交。
        val g = HexGrid(origin = lakeOrigin, cellSizeMeters = 40.0)
        val cells = g.cellsWithinMeters(lakeOrigin, 60.0)
        listOf(
            HexCellId(1, 0), HexCellId(1, -1), HexCellId(0, -1),
            HexCellId(-1, 0), HexCellId(-1, 1), HexCellId(0, 1),
        ).forEach { assertTrue("$it should be inside sight", it in cells) }
        assertTrue(HexCellId(0, -2) !in cells)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative meter radius rejected`() {
        grid.cellsWithinMeters(lakeOrigin, -1.0)
    }

    @Test
    fun `cells in rect contains origin cell and covers the rectangle`() {
        val g = HexGrid(origin = lakeOrigin, cellSizeMeters = 40.0)
        // 以北湖为中心、约 200m x 200m 的矩形（纬度 0.001°≈111m）
        val cells = g.cellsInRect(
            minLat = lakeOrigin.latDegrees - 0.001,
            minLng = lakeOrigin.lngDegrees - 0.0013,
            maxLat = lakeOrigin.latDegrees + 0.001,
            maxLng = lakeOrigin.lngDegrees + 0.0013,
        )
        assertTrue(cells.contains(HexCellId(0, 0)))
        // 200m 跨度在 40m 格上每维至少 3~4 格，总数应为十几格量级
        assertTrue("got ${cells.size}", cells.size in 12..40)
        // 无重复
        assertEquals(cells.size, cells.distinct().size)
        // 每个返回的格中心或顶点确实与矩形相交（粗验：中心距矩形不远）
        cells.forEach { cell ->
            val center = g.cellCenter(cell)
            assertTrue(center.latDegrees in lakeOrigin.latDegrees - 0.003..lakeOrigin.latDegrees + 0.003)
        }
    }

    @Test
    fun `far away cells not included in tiny rect`() {
        val g = HexGrid(origin = lakeOrigin, cellSizeMeters = 40.0)
        val cells = g.cellsInRect(
            minLat = lakeOrigin.latDegrees + 0.01, // 约 1.1km 北
            minLng = lakeOrigin.lngDegrees - 0.001,
            maxLat = lakeOrigin.latDegrees + 0.012,
            maxLng = lakeOrigin.lngDegrees + 0.001,
        )
        assertTrue(HexCellId(0, 0) !in cells)
    }
}
