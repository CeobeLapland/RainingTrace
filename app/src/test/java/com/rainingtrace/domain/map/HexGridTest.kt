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
}
