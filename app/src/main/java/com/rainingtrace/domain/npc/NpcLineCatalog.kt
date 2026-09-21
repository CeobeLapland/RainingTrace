package com.rainingtrace.domain.npc

/**
 * 台词表。键的约定：
 *
 * - `"$npcId.$key"` 是**该 NPC 的专属覆盖**（优先级最高）；
 * - `key` 是共享台词；
 * - 查不到时由 `TemplateNarrativeService` 退回 `unparsed`，不留空。
 *
 * 每个部件至少 4 条变体——变体太少，同一句在几轮对话里就会重复出现，
 * 那是最容易露馅的地方。文案刻意短、具体、口语，不要写成"AI 式的周到"。
 */
internal object NpcLineCatalog {

    val LINES: Map<String, List<String>> = buildMap {
        // ---- 招呼（按关系阶段） ----
        put(
            "greet.stranger",
            listOf("你好。", "嗯，你好。", "……你好。", "你好，有什么事吗？"),
        )
        put(
            "greet.nodding",
            listOf("哦，是你。", "嗯，你好。", "又碰上你了。", "你好呀。"),
        )
        put(
            "greet.acquainted",
            listOf("诶，是你。", "嗯？怎么了。", "在的。", "哦，你来了。"),
        )
        put(
            "greet.friend",
            listOf("在。", "怎么啦？", "说吧。", "嗯，我在。"),
        )

        // ---- 接话（按话题） ----
        put(
            "topic.BOOKS",
            listOf(
                "最近在看一本旧书，翻到一半卡住了。",
                "图书馆靠窗那排的光最好，我常在那儿。",
                "书这东西，翻第二遍才看得见第一遍漏掉的东西。",
                "看书得挑时间，脑子不清醒的时候看什么都白看。",
            ),
        )
        put(
            "topic.ART",
            listOf(
                "今天光线不错，画了两张。",
                "画画急不来，得等它自己出来。",
                "我画得慢，一张能磨一下午。",
                "颜色比形状难，差一点就不是那个意思了。",
            ),
        )
        put(
            "topic.RUNNING",
            listOf(
                "今天跑了两圈，风挺大。",
                "跑起来就不想停了。",
                "跑步没什么技巧，就是别停。",
                "早上广场人少，适合跑。",
            ),
        )
        put(
            "topic.FOOD",
            listOf(
                "食堂今天的菜还行。",
                "饭点人太多了，我一般错开去。",
                "你要是来食堂，多半能碰上我。",
                "吃这个事，别凑合。",
            ),
        )
        put(
            "topic.WEATHER",
            listOf(
                "今天这天气，挺适合在外面待着。",
                "下雨天我反而愿意出门。",
                "变天的时候，校园里会有不一样的味道。",
                "天不好就别硬出门了。",
            ),
        )
        put(
            "topic.NIGHT",
            listOf(
                "夜里安静，适合走路。",
                "晚上校园里几乎没人，只有灯。",
                "我睡得晚，习惯了。",
                "夜里的声音比白天清楚。",
            ),
        )
        put(
            "topic.PLANTS",
            listOf(
                "果子还得再等等，现在摘了太涩。",
                "雨后那几天，树根边上会冒东西。",
                "看叶子就知道季节到哪了。",
                "北边那片今年结得不错。",
            ),
        )
        put(
            "topic.SELF",
            listOf(
                "我？就那样，按点过日子。",
                "没什么特别的，每天差不多。",
                "你问这个干嘛。",
                "反正就是上课、吃饭、睡觉。",
            ),
        )
        put(
            "topic.default",
            listOf("嗯，这个……", "这个我没什么可说的。", "不太懂这个。", "嗯。"),
        )

        // ---- 提到地点 ----
        put(
            "place",
            listOf(
                "「{place}」啊，我熟。",
                "{place}？我常去那边。",
                "「{place}」挺好的。",
                "{place}——嗯，那地方我知道。",
            ),
        )

        // ---- 如实回答作息（只引用 ScheduleFacts 的字段） ----
        put(
            "schedule.stay",
            listOf(
                "{time}我应该在{place}，{activity}。",
                "{time}啊——我一般在{place}，{activity}。",
                "{time}的话，我应该在{place}。",
                "{time}我大概在{place}。",
            ),
        )
        put(
            "schedule.walking",
            listOf(
                "{time}那会儿我大概在往{place}走的路上。",
                "{time}？我应该在去{place}的路上。",
                "{time}我可能正走在去{place}的路上。",
            ),
        )
        put(
            "unknownSchedule",
            listOf("这个我说不准。", "我没法确定。", "不好说。"),
        )

        // ---- 答应见面（片 3：作息已被覆盖，所以这句是真的） ----
        put(
            "commitment",
            listOf(
                "行，{time}我在{place}。",
                "{time}我在{place}，你要是来就碰上了。",
                "好，{time}我过去{place}。",
                "{time}就在{place}吧，我记下了。",
            ),
        )

        // ---- 有安排去不了（拒绝也给出信息） ----
        put(
            "noMeet.busy",
            listOf(
                "{time}我走不开，换个时间行吗。",
                "{time}我有别的安排，过不去。",
                "{time}那会儿有课，实在不行。",
            ),
        )

        // ---- 软拒绝（片 1 的通用说法，现在用于"没说清时间/地点"） ----
        put(
            "noMeet.practical",
            listOf("我不好说死。", "我按自己的点走，说不准。", "我不太会安排这种事。"),
        )
        put(
            "noMeet.reserved",
            listOf("我尽量，但我一向按自己的点走。", "……我不太习惯等人。", "别特意为我跑一趟。"),
        )
        put(
            "noMeet.warm",
            listOf("你要来我当然高兴，不过别特意等我。", "别为我改行程，碰上了就聊。", "我随缘的，你别记着这事。"),
        )
        put(
            "noMeet.default",
            listOf("我不太定得下来。", "我不好答应，碰上了再说吧。", "这个真说不准。"),
        )

        // ---- 没听懂 ----
        put(
            "unparsed",
            listOf("……嗯？", "没太听明白。", "你说什么？", "我没懂你的意思。"),
        )

        // ---- 风味尾（按情绪；CALM 没有尾，所以这里没有 tail.CALM 键） ----
        put("tail.GLAD", listOf("……嗯。", "今天心情还不错。", "挺好。"))
        put("tail.BUSY", listOf("我这边正走着，回得慢。", "我在路上，先这样。", "等我到了再说。"))
        put("tail.TIRED", listOf("有点困了。", "今天累了。", "我先歇会儿。"))
        put("tail.DOWN", listOf("……", "天气不太行。", "嗯。"))
        put("tail.INTRIGUED", listOf("你倒是挺有意思。", "这个说法有点意思。", "嗯，我记下了。"))

        // ---- 追问（话多的人才会用） ----
        put("ask", listOf("你呢？", "你怎么想？", "你也做这个吗？", "你平时都干嘛？"))

        // ---- NPC 专属覆盖：只写"这个人会说、别人不会说"的 ----
        put(
            "npc.bit.lin.topic.BOOKS",
            listOf("靠窗第三排是我的位置。", "那本书我看了三遍，还是没看完。", "书别借给别人，会丢。"),
        )
        put(
            "npc.bit.zhou.topic.FOOD",
            listOf("今天窗口多打了一勺，运气好。", "食堂几点人少我清楚得很。", "你要是来，我给你留个座。"),
        )
        put(
            "npc.bit.xu.topic.ART",
            listOf("今天光线很软，适合画水。", "我在这儿坐了一下午，水面一直在变。", "画架是旧的，但用着顺手。"),
        )
        put(
            "npc.bit.he.topic.NIGHT",
            listOf("这个点校园里只有我和路灯。", "夜里走路不用想事情。", "我一般两三点才睡。"),
        )
        put(
            "npc.bit.qi.topic.PLANTS",
            listOf("那棵树的果子再等半个月。", "菌子得等雨，急不来。", "今年的果比去年小。"),
        )
    }
}
