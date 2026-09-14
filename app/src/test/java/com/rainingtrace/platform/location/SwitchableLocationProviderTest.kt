package com.rainingtrace.platform.location

import com.rainingtrace.core.time.FakeWorldClock
import com.rainingtrace.domain.map.LocationSource
import com.rainingtrace.domain.map.WorldCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Switchable 的 GPS 侧依赖 Android 框架（Fused/LocationManager），
 * 切换行为在真机验收；纯 JVM 覆盖 Fake 提供方的来源标记。
 */
class SwitchableLocationProviderTest {

    private val origin = WorldCoordinate(39.7326, 116.1712)

    @Test
    fun `fake emits are tagged fake and update latest`() {
        val clock = FakeWorldClock(Instant.parse("2026-09-14T12:00:00Z"))
        val fake = FakeLocationProvider(clock)

        fake.emit(origin)

        assertEquals(LocationSource.FAKE, fake.latest?.source)
        assertEquals(origin, fake.latest?.coordinate)
    }
}
