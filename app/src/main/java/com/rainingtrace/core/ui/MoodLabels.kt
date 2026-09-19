package com.rainingtrace.core.ui

import com.rainingtrace.domain.memory.Mood

/** 心情的中文标签（日记卡片与地图聚焦卡共用，避免两处文案漂移）。 */
fun Mood.label(): String = when (this) {
    Mood.CALM -> "平静"
    Mood.HAPPY -> "开心"
    Mood.CURIOUS -> "好奇"
    Mood.LONELY -> "孤独"
    Mood.EXCITED -> "兴奋"
    Mood.MELANCHOLY -> "低落"
}
