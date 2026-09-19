package com.rainingtrace.feature.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.memory.AudioNoteController
import com.rainingtrace.domain.memory.MemoryNode
import com.rainingtrace.domain.memory.MemoryRepository
import com.rainingtrace.domain.memory.groupMemoriesByDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

class JournalViewModel(
    private val memoryRepository: MemoryRepository,
    private val audioNote: AudioNoteController,
    private val clock: WorldClock,
) : ViewModel() {

    private val _days = MutableStateFlow<List<JournalDay>>(emptyList())
    val days: StateFlow<List<JournalDay>> = _days.asStateFlow()

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
    }

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
    }
}
