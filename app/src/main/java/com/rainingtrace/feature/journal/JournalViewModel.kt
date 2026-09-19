package com.rainingtrace.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.memory.groupMemoriesByDay
import com.rainingtrace.domain.track.TRACK_ZONE
import com.rainingtrace.domain.track.TrackDay
import com.rainingtrace.domain.track.TrackPoint
import com.rainingtrace.domain.track.TrackRepository
import com.rainingtrace.domain.track.dayEndEpochMs
import com.rainingtrace.domain.track.dayStartEpochMs
import com.rainingtrace.domain.track.trackLengthMeters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 日记中的一天：已算好展示用标题（今天 / 昨天 / 9月17日 星期三）。 */
data class JournalDay(
    val date: LocalDate,
    val label: String,
    val memories: List<MemoryNode>,
)

/** 轨迹日历里点选的一天：选中时才加载该天的点，算距离与起止时间。 */
data class TrackDaySummary(
    val date: LocalDate,
    val pointCount: Int,
    val lengthMeters: Double,
    val startLabel: String,
    val endLabel: String,
)

class JournalViewModel(
    private val memoryRepository: MemoryRepository,
    private val audioNote: AudioNoteController,
    private val trackRepository: TrackRepository,
    private val clock: WorldClock,
) : ViewModel() {

    private val _days = MutableStateFlow<List<JournalDay>>(emptyList())
    val days: StateFlow<List<JournalDay>> = _days.asStateFlow()

    /** 有轨迹的日子（日期倒序），驱动日历上的实心圆。 */
    private val _trackDays = MutableStateFlow<List<TrackDay>>(emptyList())
    val trackDays: StateFlow<List<TrackDay>> = _trackDays.asStateFlow()

    /** 日历里选中的一天（默认今天，没今天的轨迹就选最近一天）。 */
    private val _selectedTrackDay = MutableStateFlow<TrackDaySummary?>(null)
    val selectedTrackDay: StateFlow<TrackDaySummary?> = _selectedTrackDay.asStateFlow()

    /** 正在回放的语音 URI（跨分组共享一个播放器）。 */
    val playingRef: StateFlow<String?> = audioNote.playingRef

    fun refresh() {
        viewModelScope.launch {
            val today = clock.now().atZone(JOURNAL_ZONE).toLocalDate()
            _days.value = groupMemoriesByDay(memoryRepository.latest(limit = LIMIT), JOURNAL_ZONE)
                .map { day ->
                    JournalDay(
                        date = day.date,
                        label = dayLabel(day.date, today),
                        memories = day.memories,
                    )
                }
        }
        refreshTrackDays()
    }

    fun refreshTrackDays() {
        viewModelScope.launch {
            val days = trackRepository.days()
            _trackDays.value = days
            val today = clock.now().atZone(JOURNAL_ZONE).toLocalDate()
            val initial = days.firstOrNull { it.date == today } ?: days.firstOrNull()
            if (initial == null) {
                _selectedTrackDay.value = null
            } else {
                loadTrackDay(initial.date)
            }
        }
    }

    fun selectTrackDay(date: LocalDate) {
        viewModelScope.launch { loadTrackDay(date) }
    }

    private suspend fun loadTrackDay(date: LocalDate) {
        val points = trackRepository.between(dayStartEpochMs(date), dayEndEpochMs(date))
        _selectedTrackDay.value = TrackDaySummary(
            date = date,
            pointCount = points.size,
            lengthMeters = trackLengthMeters(points),
            startLabel = points.firstOrNull()?.let(::timeLabelOf) ?: "--:--",
            endLabel = points.lastOrNull()?.let(::timeLabelOf) ?: "--:--",
        )
    }

    private fun timeLabelOf(point: TrackPoint): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(point.timestampEpochMs).atZone(TRACK_ZONE))

    /** 点语音条：播放 / 停止。 */
    fun onToggleAudio(uri: String) {
        if (playingRef.value == uri) audioNote.stopPlayback() else audioNote.play(uri)
    }

    /** 离开日记页：停止回放。 */
    fun onLeave() {
        audioNote.stopPlayback()
    }

    private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> DATE_FORMAT.format(date)
    }

    private companion object {
        const val LIMIT = 100
        val JOURNAL_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
