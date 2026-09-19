package com.rainingtrace.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingSettingsTest {

    @Test
    fun `defaults are explicit opt-in and daytime only`() {
        val settings = TrackingSettings()
        assertFalse(settings.enabled)
        assertEquals(BackgroundInterval.M1, settings.backgroundInterval)
        assertTrue(settings.daytimeOnly)
    }

    @Test
    fun `daytime window is start inclusive and end exclusive`() {
        val settings = TrackingSettings(daytimeOnly = true, dayWindow = DayWindow.DAY)

        assertTrue(settings.allowsRecordingAt(7 * 60))
        assertTrue(settings.allowsRecordingAt(21 * 60 + 59))
        assertFalse(settings.allowsRecordingAt(22 * 60))
        assertFalse(settings.allowsRecordingAt(6 * 60 + 59))
        assertFalse(settings.allowsRecordingAt(3 * 60))
    }

    @Test
    fun `daytime only off allows every minute`() {
        val settings = TrackingSettings(daytimeOnly = false, dayWindow = DayWindow.SHORT)

        assertTrue(settings.allowsRecordingAt(0))
        assertTrue(settings.allowsRecordingAt(23 * 60 + 59))
    }

    @Test
    fun `background intervals are ordered and expose a label`() {
        assertTrue(BackgroundInterval.S30.millis < BackgroundInterval.M1.millis)
        assertTrue(BackgroundInterval.M1.millis < BackgroundInterval.M2.millis)
        assertTrue(BackgroundInterval.M2.millis < BackgroundInterval.M5.millis)
        assertTrue(BackgroundInterval.M1.label.isNotBlank())
    }
}