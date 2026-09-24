package com.rainingtrace.data.content

import android.content.Context
import com.rainingtrace.core.time.WorldClock
import com.rainingtrace.domain.map.Place
import com.rainingtrace.domain.map.PlaceDraft
import com.rainingtrace.domain.map.PlaceWriteResult
import com.rainingtrace.domain.map.PlaceWriter
import com.rainingtrace.domain.map.newPlaceId
import com.rainingtrace.domain.map.rejectionReason
import java.io.File

/**
 * 把现场采到的地点追加进 `files/content/places.json`（覆盖层）。
 *
 * 三条硬要求：
 * 1. **不覆盖已有内容**：现有覆盖层读不出来时直接拒绝，而不是拿新文件盖掉它
 *    ——那是唯一会真正丢数据的操作，宁可失败。
 * 2. **原子写**：先写 `.tmp` 再改名。半截文件会让下次启动整份覆盖层失效。
 * 3. **写完立刻 reload**：不然地图上要等重启才看得见刚记的点。
 *
 * 用 [EntityFileEnvelope] 的 JsonElement 列表做"追加"，所以玩家手写在文件里的
 * 其他字段（注释性字段、将来新增的字段）都被原样保留。
 */
class JsonPlaceWriter(
    private val context: Context,
    private val store: ContentStore,
    private val clock: WorldClock,
) : PlaceWriter {

    override suspend fun addPlace(draft: PlaceDraft): PlaceWriteResult {
        draft.rejectionReason()?.let { return PlaceWriteResult.Rejected(it) }

        return runCatching { append(draft) }.getOrElse { error ->
            PlaceWriteResult.Rejected("写盘失败：${error.message ?: "未知原因"}")
        }
    }

    private fun append(draft: PlaceDraft): PlaceWriteResult {
        val file = ContentStore.overlayFile(context, ContentStore.PLACES_FILE)
        val existing = readEnvelope(file)
            ?: return PlaceWriteResult.Rejected(
                "${ContentStore.PLACES_FILE} 现在读不出来，先去设置页看诊断；" +
                    "这种情况下写进去会覆盖掉你已有的内容",
            )

        val index = store.index.value
        val id = newPlaceId(clock.now().toEpochMilli(), index.placeById.keys)

        // 领域模型的 require 同时也是这里的校验：不过就变成一条 Rejected。
        val place = runCatching {
            Place(
                id = id,
                name = draft.name.trim(),
                type = draft.type,
                coordinate = draft.coordinate,
                actions = draft.actions,
                description = draft.description.trim(),
            )
        }.getOrElse { return PlaceWriteResult.Rejected("这个地点不合法：${it.message}") }

        val element = ContentJsonFormat.encodeToJsonElement(PlaceDto.serializer(), place.toDto())
        val updated = existing.copy(entries = existing.entries + element)
        writeAtomically(
            file,
            ContentJsonFormat.encodeToString(EntityFileEnvelope.serializer(), updated),
        )

        store.reload()
        return PlaceWriteResult.Added(id)
    }

    /** 文件不存在 = 空信封（合法）；存在但读不出来 = null（拒绝写入）。 */
    private fun readEnvelope(file: File): EntityFileEnvelope? {
        if (!file.exists()) return EntityFileEnvelope()
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        return runCatching {
            ContentJsonFormat.decodeFromString<EntityFileEnvelope>(stripBom(text))
        }.getOrNull()
    }

    private fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            // 某些文件系统上 renameTo 不能覆盖已存在的目标，退化成"拷贝 + 删除"。
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}