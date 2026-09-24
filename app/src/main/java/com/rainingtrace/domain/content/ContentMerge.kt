package com.rainingtrace.domain.content

/**
 * 内置内容 + 用户覆盖层的合并规则（纯函数，可单测）。
 *
 * 语义（覆盖层用**完整条目替换**，不做补丁式字段合并）：
 * 1. 内置层同 id 重复：后者胜 + ERROR（歧义必须是确定性的）。
 * 2. 覆盖层命中内置 id：**原位替换**（保持渲染/菜单顺序稳定，不跳到末尾）。
 * 3. 覆盖层没命中的 id：追加到末尾——这就是"加一个新地点"。
 * 4. `removedIds` 剔除条目；剔除一个不存在的 id 只是 WARN，不算错
 *    （用户删了内置条目之后，内置文件又有变动时不该报错）。
 */
object ContentMerge {

    fun <T> byId(
        builtIn: List<T>,
        overlay: List<T>,
        removedIds: Set<String>,
        /** 诊断里显示的文件名（指覆盖层，因为这一层才是用户写的）。 */
        file: String,
        idOf: (T) -> String,
        report: ContentReport,
    ): List<T> {
        // LinkedHashMap：顺序 = 第一次出现的顺序，所以"原位替换"天然成立。
        val merged = LinkedHashMap<String, T>()

        builtIn.forEach { item ->
            val id = idOf(item)
            if (merged.put(id, item) != null) {
                report.error(file, id, "内置内容里 id 重复，后面的条目生效")
            }
        }

        overlay.forEach { item ->
            val id = idOf(item)
            val replaced = merged.containsKey(id)
            merged[id] = item
            if (!replaced) {
                report.warn(file, id, "覆盖层新增了内置内容里没有的条目")
            }
        }

        removedIds.forEach { id ->
            if (merged.remove(id) == null) {
                report.warn(file, id, "removedIds 里的 id 不存在，已忽略")
            }
        }

        return merged.values.toList()
    }

    /**
     * 映射型内容的合并（台词表、地点别名）：按 key 覆盖/追加/删除。
     *
     * 与 [byId] 的差别只有一个：**新增 key 不记 WARN**。
     * 台词表里加一个 `topic.XXX` 或别名里加一个词是再正常不过的事，
     * 每次都报一条"这不是内置内容"纯属噪音；删错 key 才是真的写错了。
     */
    fun <V> byKey(
        builtIn: Map<String, V>,
        overlay: Map<String, V>,
        removedKeys: Set<String>,
        file: String,
        report: ContentReport,
    ): Map<String, V> {
        val merged = LinkedHashMap(builtIn)
        overlay.forEach { (key, value) -> merged[key] = value }
        removedKeys.forEach { key ->
            if (merged.remove(key) == null) {
                report.warn(file, key, "removedKeys 里的 key 不存在，已忽略")
            }
        }
        return merged
    }
}