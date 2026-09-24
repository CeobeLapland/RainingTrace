package com.rainingtrace.domain.map

import com.rainingtrace.domain.track.RecordTrackPointUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationQualityTest {

    @Test
    fun `good within fifteen meters`() {
        assertEquals(LocationQuality.GOOD, locationQualityOf(0.0))
        assertEquals(LocationQuality.GOOD, locationQualityOf(5.0))
        assertEquals(LocationQuality.GOOD, locationQualityOf(15.0))
    }

    @Test
    fun `fair up to the accuracy gate`() {
        assertEquals(LocationQuality.FAIR, locationQualityOf(15.1))
        assertEquals(LocationQuality.FAIR, locationQualityOf(30.0))
        assertEquals(LocationQuality.FAIR, locationQualityOf(50.0))
    }

    /**
     * POOR 的分界必须**就是**轨迹点的精度闸门：玩家看到"信号弱"的时候，
     * 系统正好也在丢点。两边差一点点，界面就开始骗人了。
     */
    @Test
    fun `poor starts exactly at the track accuracy gate`() {
        assertEquals(RecordTrackPointUseCase.MAX_ACCURACY_METERS, 50.0, 0.0001)
        assertEquals(LocationQuality.FAIR, locationQualityOf(RecordTrackPointUseCase.MAX_ACCURACY_METERS))
        assertEquals(
            LocationQuality.POOR,
            locationQualityOf(RecordTrackPointUseCase.MAX_ACCURACY_METERS + 0.1),
        )
    }

    @Test
    fun `no fix is unknown and provider fallback is poor`() {
        assertEquals(LocationQuality.UNKNOWN, locationQualityOf(null))
        assertEquals(LocationQuality.UNKNOWN, locationQualityOf(Double.NaN))
        // AndroidLocationProvider 在没有 accuracy 时给 100.0 —— 那必须算"弱"。
        assertEquals(LocationQuality.POOR, locationQualityOf(100.0))
    }
}