package com.rainingtrace.data.repository

import com.rainingtrace.data.content.ShippedContent
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceActionType
import com.rainingtrace.domain.map.PlaceType
import com.rainingtrace.domain.map.WorldCoordinate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlaceRepositoryTest {

    private val repo = FakePlaceRepository(ShippedContent.places)

    @Test
    fun `north lake found by id`() = runTest {
        val place = repo.placeById("place.bit.north_lake")
        assertEquals("北湖", place?.name)
    }

    @Test
    fun `unknown id returns null`() = runTest {
        assertNull(repo.placeById("place.nope"))
    }

    @Test
    fun `nearby within 300m of lake shore finds lake`() = runTest {
        val shore = WorldCoordinate(39.7320, 116.1712) // 湖南岸，距中心约 66m
        val found = repo.nearby(shore, radiusMeters = 300.0)
        assertTrue(found.any { it.id == "place.bit.north_lake" })
    }

    @Test
    fun `far away point finds nothing`() = runTest {
        val far = WorldCoordinate(39.9042, 116.4074) // 北京市中心
        assertTrue(repo.nearby(far, radiusMeters = 500.0).isEmpty())
    }

    @Test
    fun `nearby sorted by distance`() = runTest {
        val extra = listOf(
            ShippedContent.place("place.bit.north_lake"),
            Place(
                id = "place.bit.plaza",
                name = "中心广场",
                type = PlaceType.PLAZA,
                coordinate = WorldCoordinate(39.7290, 116.1712),
                actions = setOf(PlaceActionType.OBSERVE),
            ),
        )
        val multiRepo = FakePlaceRepository(extra)
        val fromNorth = multiRepo.nearby(WorldCoordinate(39.7400, 116.1712), radiusMeters = 3000.0)
        assertEquals(2, fromNorth.size)
        assertEquals("place.bit.north_lake", fromNorth.first().id)
    }
}