package com.rainingtrace.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 本应用的世界时区：GDD 现实映射目前是单一校园/城市，所以全局一个时区。
 * 所有"本地日期/时段/季节"判断都走它，不要在别处再写字面量。
 */
val WORLD_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/**
 * RT-DOM-008: 世界时钟。
 *
 * 所有业务时间必须经此接口获取（见 05_领域模型 §10）。
 * UseCase / domain 中禁止直接 `Instant.now()`。
 */
interface WorldClock {
    fun now(): Instant

    fun localDate(zone: ZoneId): LocalDate = now().atZone(zone).toLocalDate()
}

/** 生产实现：系统时间。 */
class SystemWorldClock : WorldClock {
    override fun now(): Instant = Instant.now()
}

/** 测试/Fake 实现：固定时刻，可手动推进。 */
class FakeWorldClock(initial: Instant) : WorldClock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}
