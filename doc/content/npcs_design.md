# 校园版星露谷物语 · 人物表（30 人）

> 配套 `resources_design.md` / `recipes_design.md`。本文字段命名与工作区示例 `npcs.json` 对齐。
> **本文仅为设计稿，暂不生成 json。**
> 已含 `schedule`（作息）。`sceneAnchor`（常驻地点速记）仅供策划参考，不进 json。

---

## 一、字段与命名约定

| 字段 | 约定 |
|------|------|
| `id` | `npc.bit.` + 英文小写，示例里已有 Lin / Zhou / Xu / He / Qi 五人沿用原 id |
| `name` | 姓/名一到两个字，全表不重名 |
| `role` | 一句话身份（含年级/职务），社交面板主标题 |
| `oneLiner` | 玩家在路边第一次撞见时看到的那句话，≤ 20 字 |
| `traits` | 性格标签，英文大写下划线，用于对话分支 |
| `topics` | 可聊话题标签，英文大写，2–4 个 |
| `favoriteTopic` | 玩家送对东西时好感涨得最快的那个话题 |
| `backstory` | 2–3 句，只写能改变玩家行为的往事 |
| `sceneAnchor` | 常驻地点（不进 json，策划用），对应 `placeId` |
| `giftPreferences` | `{ liked: [resourceId], disliked: [resourceId] }`，详见第八节 |
| `schedule` | `[{ startMinute, placeId, travelMinutes, activity }]`，详见第九节 |

**traits 候选（现有 8 个 + 新增，本文用到）**
现有：`TACITURN` 寡言 / `PUNCTUAL` 守时 / `WARM` 温和 / `TALKATIVE` 爱聊 / `DREAMY` 发呆 / `RESERVED` 疏离 / `CURIOUS` 好奇 / `PRACTICAL` 务实
新增：`OBSERVANT` 观察力强 / `SERIOUS` 认真 / `SHY` 怯场 / `EXACTING` 挑剔 / `BRIGHT` 明亮 / `CHATTY` 嘴碎 / `STUBBORN` 倔 / `LISTENER` 会听 / `SHARP` 锋利 / `GENEROUS` 讲义气 / `TIRELESS` 不歇 / `STEADY` 稳 / `MYSTERIOUS` 来路不明 / `ABSORBED` 忘我 / `MANNERED` 讲究 / `SMUG` 自得 / `GENTLE` 慢性子 / `PLAIN` 不起眼

**topics 候选**
`BOOKS` 书 / `SELF` 自己 / `FOOD` 吃的 / `RUNNING` 跑步 / `WEATHER` 天气 / `ART` 画画 / `NIGHT` 夜里 / `PLANTS` 植物 / `MUSIC` 音乐 / `SEWING` 缝纫 / `PHOTO` 拍照 / `TECH` 机器 / `SPORT` 运动 / `STUDY` 学习 / `PEOPLE` 人 / `SOUND` 声音 / `RAIN` 雨 / `MEMORY` 记忆 / `CAT` 猫 / `DRAWING` 制图 / `LAW` 规则 / `MONEY` 钱 / `ANOMALY` 异常

---

## 二、形象与出图提示词约定

统一后缀（两条都自动追加，条目里只写差异部分）：

- **头像**：`..., portrait bust shot, semi-realistic anime style, soft diffused light, detailed iris, subtle skin shading, clean plain background, no text`
- **立绘**：`..., full body, standing, semi-realistic anime style, detailed outfit and props, soft rim light, plain gradient background, no text`

风格锚点：**二次元写实**（semi-realistic anime），不要纯赛璐璐、不要厚涂、不要 3D 渲染。避免 `chibi`。

---

## 三、总表

| # | 姓名 | id | 身份 | traits | favorite | 场景锚点 |
|---|------|----|------|--------|----------|----------|
| 1 | 林砚 | `npc.bit.lin` | 建筑系大二 · 图书馆常客 | 寡言 守时 观察 | BOOKS | 图书馆三楼靠窗 |
| 2 | 周野 | `npc.bit.zhou` | 食堂帮工 · 晨跑者 | 温和 爱聊 稳 | RUNNING | 广场 → 一食堂 |
| 3 | 徐晚 | `npc.bit.xu` | 美术系大三 · 湖边画手 | 发呆 疏离 好奇 | ART | 小湖北岸 |
| 4 | 何岸 | `npc.bit.he` | 夜行的人 · 身份不明 | 疏离 好奇 观察 | NIGHT | 旧图书馆 → 花园 |
| 5 | 齐岭 | `npc.bit.qi` | 果林巡护人 | 务实 寡言 | PLANTS | 果林 / 浆果丛 / 树根 |
| 6 | 沈砚 | `npc.bit.shen_yan` | 物理系大三 · 仪器维修 | 认真 守时 沉得住 | TECH | 实验楼 415 |
| 7 | 苏黎 | `npc.bit.su_li` | 大一新生 · 手账爱好者 | 怯场 观察 嘴碎 | BOOKS | 图书馆四楼角落 |
| 8 | 陈秋 | `npc.bit.chen_qiu` | 校史馆管理员（老师） | 慢性子 讲究 温和 | MEMORY | 校史馆二层 |
| 9 | 高见 | `npc.bit.gao_jian` | 学生会 · 社团联合会 | 讲义气 锋利 不歇 | PEOPLE | 礼堂 / 学生活动中心 |
| 10 | 白依 | `npc.bit.bai_yi` | 设计系大二 · 缝纫社社长 | 挑剔 认真 温和 | SEWING | 综合楼 B1 缝纫社 |
| 11 | 罗小满 | `npc.bit.luo_xiaoman` | 音乐系大三 · 打击乐 | 明亮 嘴碎 讲义气 | SOUND | 音乐教室 / 操场台阶 |
| 12 | 蒋成一 | `npc.bit.jiang_chengyi` | 园艺社大二 · 果林助手 | 好奇 嘴碎 务实 | PLANTS | 果林 / 菜圃 |
| 13 | 韦禾 | `npc.bit.wei_he` | 校园保安 · 夜班 | 不起眼 守时 温和 | PEOPLE | 门卫值班室 |
| 14 | 苗小蝶 | `npc.bit.miao_xiaodie` | 摄影社大一 · 暗房常客 | 好奇 疏离 观察 | PHOTO | 旧图书馆三层暗房 |
| 15 | 欧阳让 | `npc.bit.ouyang_rang` | 物理系研究生 · 低温实验 | 忘我 不歇 寡言 | TECH | 实验楼地下低温室 |
| 16 | 唐糖 | `npc.bit.tang_tang` | 大二 · 食堂甜品组帮工 | 明亮 温和 嘴碎 | FOOD | 一食堂后厨 |
| 17 | 江予 | `npc.bit.jiang_yu` | 法学院大三 · 辩论队 | 锋利 认真 倔 | LAW | 模拟法庭 / 辩论室 |
| 18 | 阿武 | `npc.bit.a_wu` | 篮球队队长（大三） | 讲义气 务实 明亮 | SPORT | 体育馆球场 |
| 19 | 温宁 | `npc.bit.wen_ning` | 大二 · 心理社团值班学姐 | 稳 会听 温和 | PEOPLE | 旧楼 204 |
| 20 | 顾青 | `npc.bit.gu_qing` | 大三 · 校园广播播音 | 疏离 认真 观察 | SOUND | 广播站 |
| 21 | 廖一 | `npc.bit.liao_yi` | 大四 · 备考中 | 不歇 倔 认真 | STUDY | 自习室 |
| 22 | 米娅 | `npc.bit.mi_ya` | 交换生 · 学中文 | 好奇 明亮 嘴碎 | FOOD | 教学楼 / 一食堂 |
| 23 | 骆师傅 | `npc.bit.luo_shifu` | 水电维修（工龄 20 年） | 不起眼 温和 务实 | MONEY | 工具车 / 各栋楼 |
| 24 | 姜小渔 | `npc.bit.jiang_xiaoyu` | 大三 · 湿地与鸟类观察 | 沉得住 好奇 疏离 | PLANTS | 小湖湿地观测点 |
| 25 | 樟叔 | `npc.bit.zhang_shu` | 退休园林工 · 校门修车 | 寡言 好奇 慢性子 | PLANTS | 校门口车棚 |
| 26 | 老陈 | `npc.bit.chen_jiu` | 旧体育馆地下看夜人 | 神秘 稳 不起眼 | NIGHT | 旧体育馆地下室 |
| 27 | 三更 | `npc.bit.san_geng` | 会说话的乌鸦 | 好奇 锋利 挑 | NIGHT | 礼堂屋脊 / 异常各处 |
| 28 | 沈墨 | `npc.bit.shen_mo` | 雨天才出现的人 | 神秘 疏离 温和 | RAIN | 任何正在下雨的路 |
| 29 | 夏星 | `npc.bit.xia_xing` | 出现在旧照片里的人 | 明亮 观察 疏离 | MEMORY | 校史馆照片墙 |
| 30 | 娄七 | `npc.bit.lou_qi` | 失物招领处管理员 | 稳 讲义气 神秘 | MEMORY | 地下"不在图纸上"的房间 |

> 25–30 为特殊/边缘角色：其中 **沈墨 / 夏星 / 三更 / 娄七** 属于异常侧（与 `res.anomaly_*` 资源绑定），**樟叔 / 老陈** 是"人，但被传言包着"的存在，负责把异常侧和日常侧缝在一起。

---

## 四、角色详设（30 人）

### 1. 林砚 · npc.bit.lin

- **名字**：**林砚**（原只有姓「林」）。"砚"是磨墨的器具，贴他随手画线、摊开两张纸做事的习惯。

- **身份**：建筑系大二。选课很少，作业都在图书馆三楼靠窗那张桌子完成。
- **形象**：黑直短发偏分，架一副细银框眼镜；灰蓝连帽卫衣洗到发白，袖口起球；帆布包侧兜插一支自动铅笔，进门先擦椅子才坐。说话时习惯用指尖在桌上画线。
- **性格**：寡言、守时、观察力强。不讨厌说话，只是不认为有必要先开口。
- **爱好 / 话题**：书、建筑测绘、别人的伞（BOOKS / SELF / WEATHER）。
- **口吻**：一两个词回答；被问到在意的事会突然说很多，然后自己停下来。
- **背景**：
  1. 大一丢过一次伞，后来一直用储物柜里那把没人认领的伞，伞柄缠了胶布。
  2. 桌上摊着两本书是刻意的——他要有一本"不重要的书"垫手。
  3. 正在偷偷画一张校园地图，把"能看见三教那条路"的位置全部标红，没人知道为什么。
- **常驻**：图书馆三楼靠窗第三排。
- **相关**：手抄纸、纸艺、图书馆知识类资源。

- **头像提示词**：`young male college student, black parted short hair, thin silver glasses, faded grey-blue hoodie, calm downcast eyes, holding an automatic pencil`
- **立绘提示词**：`young male college student, full body, grey-blue hoodie and canvas shoulder bag, faded jeans, worn sneakers, one hand tracing a line on a folded campus map, quiet reserved posture`

### 2. 周野 · npc.bit.zhou

- **名字**：**周野**（原只有姓「周」）。"野"配他绕广场跑两圈的习惯，也配他"跑得动就什么都能扛"的劲头。

- **身份**：食堂帮工，勤工俭学，大三在读。每天先跑两圈再去收餐盘。
- **形象**：短寸头，晒得很黑；红色志愿者马甲套在白 T 恤外，运动鞋前头磨白；袖子永远卷到肘上；笑起来眼尾有三道纹。
- **性格**：温和、爱聊、稳。属于"全校都认得脸、没人叫得出名字"的那种人。
- **爱好 / 话题**：跑步、听广播、腌菜（RUNNING / FOOD / SOUND）。
- **口吻**：一句话里带三个感叹；劝人吃饭是本能。
- **背景**：
  1. 寒假不回家，在后厨做到腊月二十九，怕宿舍没人留灯。
  2. 每天跑两圈不是为了健康，是"跑得动，就什么都能扛"。
  3. 会把多打的饭分给晚自习结束的人，谁都欠他一顿，他从不记账。
- **常驻**：广场 → 一食堂后厨。
- **相关**：烹饪、发酵、调味类配方，广播站闲聊。

- **头像提示词**：`college student part-time canteen worker, buzz cut, tanned skin, bright friendly smile, red volunteer vest over white tee, sleeves rolled up`
- **立绘提示词**：`college student canteen worker, full body, red volunteer vest, worn white tee, apron, scuffed running shoes, mid-motion jogging posture, warm open expression`

### 3. 徐晚 · npc.bit.xu

- **名字**：**徐晚**（原只有姓「徐」）。她白天在湖边、深夜也在湖边，一天里最清醒的时候总是傍晚到夜里那一段。

- **身份**：美术系大三。湖边支着画架，一坐一下午。
- **形象**：过肩黑发被风吹乱，锁骨上蹭一点靛蓝颜料；宽大的灰绿工装外套罩住裙子，指节全是洗不掉的绿；睫毛很长，眼神常常不在焦点上。
- **性格**：发呆、疏离、其实非常好奇——只是她只看不问。
- **爱好 / 话题**：画画、天气、夜色（ART / WEATHER / NIGHT）。
- **口吻**：句子说一半就停，会用"嗯……"代替回答。
- **背景**：
  1. 画架是父亲的，缺一条腿，用铁丝绑着，站了三年。
  2. 她在画同一个湖的三十七个版本，同学以为她在重复，其实她在等一个能画完的天气。
  3. 雨天她画得最好，好到她自己害怕——那种准确不像她画的。
- **常驻**：小湖北岸长椅旁。
- **相关**：花瓣、露水、颜料类，异常画材（雨天限定）。

- **头像提示词**：`young female art student, long black hair blown back, faint indigo paint smudge on collarbone, oversized sage work jacket, unfocused distant eyes, long eyelashes`
- **立绘提示词**：`young female art student, full body, oversized sage jacket over long skirt, paint-stained fingers, beside a tripod easel with a half-finished lake painting, wind moving her hair`

### 4. 何岸 · npc.bit.he

- **名字**：**何岸**（原只有姓「何」）。他一直在岸的这一边走，从不过去。

- **身份**：不明。没有课表、没有学籍登记，却在夜里出现在旧图书馆和花园。
- **形象**：高瘦，深色长外套领子立起来；头发遮住一只眼；手里永远握一只老式手电，金属壳磨掉了漆。
- **性格**：疏离、好奇、观察力强。答话很温和，但答案经常错位半拍。
- **爱好 / 话题**：夜里、旧书、说自己的事但不明说（NIGHT / BOOKS / SELF）。
- **口吻**：平静、缓慢，问什么答什么，但从不多给。
- **背景**：
  1. 宿舍 3 楼确实有他的床位，室友说从来没人见过他睡在那儿。
  2. 他在翻旧书目卡，翻到某一张就停很久，然后放回去。
  3. 有人看见他和一个不该出现在校园里的人打招呼——那个人是夏星。
- **常驻**：旧图书馆 → 花园 → 深夜的宿舍楼道。
- **相关**：异常资源、旧书目卡、深夜类发现物。

- **头像提示词**：`mysterious tall young man, long dark coat with raised collar, hair covering one eye, holding a worn metal flashlight, pale calm face, night ambience`
- **立绘提示词**：`mysterious tall young man, full body, long dark overcoat, worn metal flashlight in hand, standing on a dim library aisle, soft moonlight through tall window, half face in shadow`

### 5. 齐岭 · npc.bit.qi

- **名字**：**齐岭**（原只有姓「齐」）。他一辈子在果林、山脊这类地方走，"岭"是他实际站着的地形。

- **身份**：果林与林地巡护人。背布袋在果林、浆果丛、树根之间转，看果子熟没熟。
- **形象**：五十上下，寸头花白，皮肤晒得发红；灰绿工装外套换成旧的；腰间挂剪刀和一小卷麻绳；布袋上用马克笔写着"熟/不熟"。
- **性格**：务实、寡言、慢热。回答永远短，但给的答案都能用。
- **爱好 / 话题**：植物、天气、吃（PLANTS / WEATHER / FOOD）。
- **口吻**：不寒暄，先说重点。
- **背景**：
  1. 园艺系毕业，别的同学去了设计院，他留在学校看林子。
  2. 布袋里的标签是他自己写的，写了十几年，字迹换过三次。
  3. 樟叔退休那年，他把果林交出去的时候说过一句"我不行"，那是齐第一次承认自己会老。
- **常驻**：果林 → 浆果丛 → 树根一带。
- **相关**：大部分 NATURE 资源、采摘类配方。

- **头像提示词**：`middle-aged groundskeeper, greying buzz cut, sun-reddened weathered skin, old grey-green work jacket, canvas bag, pruning shears at belt, steady look`
- **立绘提示词**：`middle-aged groundskeeper, full body, worn grey-green work jacket, canvas sack with handwritten tags, pruning shears and hemp cord at belt, standing among fruit trees in an orchard`

### 6. 沈砚 · npc.bit.shen_yan

- **身份**：物理系大三。实验楼 415 常年有他，负责把坏掉的仪器修好。
- **形象**：黑发略长，额前一缕总是垂着；白大褂洗得笔挺但口袋塞满螺丝刀；工装裤膝盖有两块补丁；听东西时会微微侧头。
- **性格**：认真、守时、沉得住气。修东西的时候会跟机器说话。
- **爱好 / 话题**：机器、拆解、深夜实验（TECH / NIGHT / STUDY）。
- **口吻**：术语不加解释，嫌你不懂但还是解释一遍。
- **背景**：
  1. 家里开修理铺，从小听工具的声音长大，能凭一声"嗡"判断哪里要坏。
  2. 大二那年实验楼半夜跳闸，整层漆黑，他一个人把应急回路接通了，天亮才被人发现。
  3. 他给每台修好的机器都贴了一小条胶带写日期，没人知道为什么。
- **常驻**：实验楼 415 工位。
- **相关**：铜线、木板、仪器类配方。

- **头像提示词**：`college physics student, black medium hair with loose strand, clean lab coat stuffed with screwdrivers, focused listening expression, cool workshop light`
- **立绘提示词**：`college physics student, full body, white lab coat over patched work trousers, screwdriver in hand, standing at a cluttered electronics bench with oscilloscope and coiled copper wire`

### 7. 苏黎 · npc.bit.su_li

- **身份**：大一新生。转学三次。习惯坐在图书馆四楼最靠里的角落。
- **形象**：过肩黑发，怀里永远抱着一个贴满贴纸的本子；校服外套太大，袖子盖住半个手背；说话时手指会捏住本子边角。
- **性格**：怯场、观察力强、嘴碎（只对愿意听的人）。
- **爱好 / 话题**：书、别人的生活、收集贴纸（BOOKS / PEOPLE / SELF）。
- **口吻**：先小声道"那个……"，一旦说顺了就收不住。
- **背景**：
  1. 有一个本子记"今天有人跟我说话了"，到第九十七天的时候写满了。
  2. 她能背出常坐对面那人的所有借阅记录，自己一条都不敢借。
  3. 她收集的贴纸按颜色排，排到最后发现少了一张，贴在图书馆窗框上——就是现在还在那儿。
- **常驻**：图书馆四楼角落。
- **相关**：手抄纸、纸艺、知识类发现物。

- **头像提示词**：`shy first-year girl, shoulder-length black hair, oversized school jacket with sleeves over hands, clutching a sticker-covered notebook, small hesitant smile`
- **立绘提示词**：`shy first-year girl, full body, oversized uniform jacket, sticker notebook held to chest, standing in a dim library corner, hesitant posture, soft warm lamp light`

### 8. 陈秋 · npc.bit.chen_qiu

- **身份**：校史馆管理员。教过三代学生，在校时间比大多数教职工都长。
- **形象**：灰白短发梳得整整齐齐；藏青对襟外套，扣子扣到最上面一颗；白手套戴在手上取照片；说话慢，走路也慢。
- **性格**：慢性子、讲究、温和。规矩多，但从不为难人。
- **爱好 / 话题**：记忆、老照片、泡茶（MEMORY / PEOPLE / FOOD）。
- **口吻**：每句话都收在"啊""呢"上，从不打断别人。
- **背景**：
  1. 她手上有历届学生名册，能指着照片说出每个人的名字，包括后来不来的那几个。
  2. 每年有毕业生回来找不到当年合照，她会从抽屉最底层抽出一张没洗出来的。
  3. 2008 年那批照片里，有一张她记得有八个人，档案上只写了七个。
- **常驻**：校史馆二层。
- **相关**：旧照片、记忆类资源、校史知识类。

- **头像提示词**：`elderly female museum curator, neatly combed grey hair, navy buttoned jacket, white cotton gloves, holding a faded old photograph, gentle slow expression`
- **立绘提示词**：`elderly female curator, full body, navy buttoned jacket, long dark skirt, white gloves, holding a framed photograph, standing in a dim archive room with hanging photo prints`

### 9. 高见 · npc.bit.gao_jian

- **身份**：学生会与社团联合会。什么事都要过一遍他的手。
- **形象**：头发打理得一丝不乱，深色衬衫扣到第二颗，袖扣是旧的；腋下夹一叠表格，边角永远卷起来。
- **性格**：讲义气、锋利、不歇。可以连轴转三天，但不许别人说自己累。
- **爱好 / 话题**：人、活动、筹钱（PEOPLE / MONEY / SOUND）。
- **口吻**：一开口就是在分配任务，听的人才发现自己已经答应了。
- **背景**：
  1. 竞选输过两次，第三次拿到全部票，他说"我赢的不是选举，是你们终于怕我了"。
  2. 他偷偷筹备过一场演出，办完才发现全校只有六个人来看，还下着雨。
  3. 那六个人他一个都没忘，每年演出都还留着那六个空位。
- **常驻**：礼堂 / 学生活动中心。
- **相关**：文化类资源、社团道具、舞台装饰。

- **头像提示词**：`sharp student council president, neatly styled black hair, dark dress shirt with old cufflinks, rolled paperwork under arm, decisive confident look`
- **立绘提示词**：`student council president, full body, dark shirt and slacks, holding a stack of curled forms, standing in an empty auditorium aisle, sharp confident posture`

### 10. 白依 · npc.bit.bai_yi

- **身份**：设计系大二。缝纫社社长，把综合楼 B1 一间堆布的房间收拾成全社的天堂。
- **形象**：黑发在脑后随手一挽，几缕碎发垂着；戴半框眼镜，脖子上挂软尺；手指上有被针扎过的细小血点，洗不掉。
- **性格**：挑剔、认真、温和。给别人改衣服从不收钱，但会把你原来的衣服批评一遍。
- **爱好 / 话题**：缝纫、布料、给人改衣服（SEWING / ART / PEOPLE）。
- **口吻**：先问"你穿这件多久了"，然后开始讲 ergonomics。
- **背景**：
  1. 社办墙上钉着历届社员的尺寸表，最早一张纸已经泛黄。
  2. 她改过一百多件外套，理由都是"袖子太长了，遮住手腕写字会冷"。
  3. 有一件改完没交还的校服外套挂在最里面，她说是"等一个人回来拿"。
- **常驻**：综合楼 B1 缝纫社。
- **相关**：布艺、衣物、伞具类配方。

- **头像提示词**：`design student seamstress, black hair loosely tied back, half-rim glasses, measuring tape around neck, needle-pricked fingers, calm scrutinizing look`
- **立绘提示词**：`design student seamstress, full body, half-rim glasses, apron with pins, fabric draped over one arm, standing beside an old sewing machine in a cluttered club room`

### 11. 罗小满 · npc.bit.luo_xiaoman

- **身份**：音乐系大三。打击乐方向。社团只有她一个人，但她坚持按期排练。
- **形象**：高马尾，编了两股小辫；宽大黑色乐队卫衣，背后印着已解散的乐队名；手臂有鼓槌磨出的红印；背包侧边挂一串小铃铛。
- **性格**：明亮、嘴碎、讲义气。音量永远比需要的大两格。
- **爱好 / 话题**：声音、节奏、收集怪声音（SOUND / MUSIC / NIGHT）。
- **口吻**：一句话喊两遍，怕你听不见。
- **背景**：
  1. 坚持给一个人少的社团办演出，自己垫了三场才凑够人。
  2. 她有个本子记校园里的声音：编号 001 是图书馆闭馆前的椅子响，编号 188 是深夜教学楼的门。
  3. 编号 233 她记了"会响两次的门"，但她说自己没敢再听。
- **常驻**：音乐教室 / 操场看台台阶。
- **相关**：弦乐、打击乐、装饰件配方。

- **头像提示词**：`energetic music major girl, high ponytail with two thin braids, oversized black band hoodie, red marks on forearms, small bells on bag, wide open smile`
- **立绘提示词**：`energetic music major girl, full body, oversized band hoodie, ripped jeans, drumsticks in hand, small bell charms on her bag, standing in an empty music classroom`

### 12. 蒋成一 · npc.bit.jiang_chengyi

- **身份**：园艺社大二。齐的果林助手。农学院转专业过来的。
- **形象**：晒黑，圆框眼镜总滑到鼻尖；卷起的长袖衬衫沾满土；帆布鞋里塞了防滑鞋垫；手里拿的不是花剪，是一支记号笔。
- **性格**：好奇、嘴碎、务实。什么问题都要追着问到底。
- **爱好 / 话题**：植物、节气、吃当季的东西（PLANTS / WEATHER / FOOD）。
- **口吻**：连珠炮提问，被拒绝也不停。
- **背景**：
  1. 从农学院转来，因为"想学怎么把东西种得好看，而不是怎么种得活"。
  2. 宿舍阳台种满了苗，被室友投诉过一次，从此改到楼道窗台。
  3. 他偷偷给每棵树建了档案，记录开花与结果日期，写到第三年还没人看。
- **常驻**：果林 / 菜圃。
- **相关**：NATURE 资源、种植与烹饪链路。

- **头像提示词**：`agriculture student, sun-tanned, round glasses slipping down nose, rolled-up shirt sleeves with soil stains, holding a marker pen, curious bright expression`
- **立绘提示词**：`agriculture student, full body, rolled linen shirt and canvas shoes, clipboard in one hand, kneeling beside vegetable beds in a walled garden, sunlit`

### 13. 韦禾 · npc.bit.wei_he

- **身份**：校园保安，夜班。值班室在北门。
- **形象**：四十多岁，寸头，戴一副用胶布缠过腿的眼镜；深蓝保安制服洗得发白，肩章线还在；保温杯永远是满的；看手机屏幕会把亮度调到最低。
- **性格**：不起眼、守时、温和。跟你说话时会先把身体转过来面对你。
- **爱好 / 话题**：人、天气、旧八卦（PEOPLE / WEATHER / NIGHT）。
- **口吻**：慢，客气，能不说就不说，但被人问到会多说。
- **背景**：
  1. 值夜第七年，认得每一个翻墙进来的学生，也认得每一个没翻墙进来的。
  2. 值班室抽屉里有一本记名字的本子，写满了不认识的字。
  3. 有一个学生他从没在名册上见过，却每个夜班都来跟他打招呼。
- **常驻**：北门值班室。
- **相关**：夜间线索、何/夏星支线的入口。

- **头像提示词**：`middle-aged campus night security guard, buzz cut, taped-temple glasses, faded navy uniform, holding a thermos, polite calm expression, dim booth light`
- **立绘提示词**：`campus night security guard, full body, faded navy uniform with shoulder seams, thermos in hand, standing by a small lit gatehouse window, dark campus road behind`

### 14. 苗小蝶 · npc.bit.miao_xiaodie

- **身份**：摄影社大一。旧图书馆三层暗房的常驻。
- **形象**：齐耳短发，发梢染过一点紫色又褪了；黑围裙挂一整条背带；脖子上挂一台老胶片机，镜头盖用胶布写着"别碰"；右手中指有显影液留下的红斑。
- **性格**：好奇、疏离、观察力强。很少进人群，但会为一张照片站在原地两小时。
- **爱好 / 话题**：拍照、光、没人看的角落（PHOTO / ART / NIGHT）。
- **口吻**：短，句子之间停很久，问的问题却越来越具体。
- **背景**：
  1. 社费不够买第二个显影罐，她用饮料桶自己改了一个。
  2. 她拍了同一棵树整整一年，从芽到落叶，照片编号 001 到 366。
  3. 第 182 张多了一个不该在画面里的人，她洗出来了，没给任何人看过。
- **常驻**：旧图书馆三层暗房。
- **相关**：照片、影像、旧化处理类配方，异常侧线索。

- **头像提示词**：`first-year photography club girl, chin-length hair with faded violet tips, black film apron, vintage film camera on neck, red-stained fingers, quiet intense gaze`
- **立绘提示词**：`photography girl, full body, black film apron, vintage camera raised halfway, trays of developing liquid on a bench, red safelight glow in a darkroom`

### 15. 欧阳让 · npc.bit.ouyang_rang

- **身份**：物理系研究生。低温实验方向。长期泡在实验楼地下。
- **形象**：瘦，眼下青；头发乱得像没梳过；一件看不出季节的深蓝抓绒；手上戴一副过大的手套，只在写字时摘。
- **性格**：忘我、不歇、寡言。能把"现在几点"答成"数据还没稳定"。
- **爱好 / 话题**：机器、低温、夜里的实验楼（TECH / NIGHT / STUDY）。
- **口吻**：自说自话，讲到兴处会突然停住，因为想到了下一步。
- **背景**：
  1. 论文卡在第十一个月，不是因为做不出来，是因为实验结果每次都不一样。
  2. 他把食堂饭卡和实验服口袋里的东西混在一起，丢过三把钥匙。
  3. 地下室的温度记录本上有一行被划掉的字：`12:40 有人进过`。
- **常驻**：实验楼地下低温室。
- **相关**：铜线、玻璃、仪器类配方 + 夜间异常。

- **头像提示词**：`thin graduate physics student, messy unwashed dark hair, dark circles under eyes, oversized navy fleece, eyes locked on something unseen, distracted intense look`
- **立绘提示词**：`graduate student in underground lab, full body, oversized navy fleece, oversized gloves half removed, standing beside a frost-rimmed cryogenic vessel, cold vapor around ankles`

### 16. 唐糖 · npc.bit.tang_tang

- **身份**：大二。食堂甜品组帮工，正式课表排得满，甜品是中午之后的事。
- **形象**：栗色双丸子头，围裙上有一块洗不掉的奶油渍；随身带一把小刮刀；说话时手上动作不停。
- **性格**：明亮、温和、嘴碎。做出来的东西会先分给旁边的人。
- **爱好 / 话题**：吃的、甜的东西、节气食材（FOOD / PLANTS / WEATHER）。
- **口吻**：甜，语速快，会突然报一串材料数量。
- **背景**：
  1. 学做甜品是因为高中时家里开过一家小面包房，冬天最难卖出去的是红豆的那款。
  2. 她记得每个师兄的口味偏好，谁不吃香草她都记着。
  3. 后厨冰箱里有一盒做坏了的曲奇，标签写着一个人名，已经放了四个月。
- **常驻**：一食堂后厨。
- **相关**：烹饪、发酵、调味配方。

- **头像提示词**：`college girl in bakery apron, chestnut double buns hair, cream stain on apron, holding a small spatula, cheerful sweet smile, warm kitchen light`
- **立绘提示词**：`college girl baker, full body, white apron over casual clothes, small spatula in hand, standing at a stainless kitchen counter with cooling trays, warm steam in air`

### 17. 江予 · npc.bit.jiang_yu

- **身份**：法学院大三。辩论队主力。说话快，刀子准。
- **形象**：齐耳黑发，露耳；衬衫扎进西裤，袖扣是借来的；书包里永远装着两本互相矛盾的书；站着和人说话时重心在后一只脚上。
- **性格**：锋利、认真、倔。可以赢完辩赛后主动承认自己错了一半。
- **爱好 / 话题**：规则、逻辑、找人辩论（LAW / PEOPLE / STUDY）。
- **口吻**：先拆对方的前提，再回答问题；不提高音量。
- **背景**：
  1. 队里没人愿意跟她搭档第三轮，她自己写完了二辩稿。
  2. 她有一个本子记身边人说话的口头禅，攒了两年，共 431 条。
  3. 她赢过一场决定校规的辩论，然后第一个去申请"条款有歧义"的复核。
- **常驻**：模拟法庭 / 辩论室。
- **相关**：知识类、制度类资源。

- **头像提示词**：`sharp law student debate champion, ear-length black hair, tucked white shirt, borrowed cufflinks, confident half-smile, assertive chin`
- **立绘提示词**：`law student, full body, tucked shirt and dark slacks, holding two open books with conflicting notes, standing in a moot court room, one foot back, assertive stance`

### 18. 阿武 · npc.bit.a_wu

- **身份**：篮球队队长，大三。球场上永远最后一个走。
- **形象**：一米九，寸头，左耳后有一道旧伤疤；护腕戴在右手（左手才是主力）；训练背心外披一件外套；说话时会用整个手掌比划。
- **性格**：讲义气、务实、明亮。答应下来的事一定做到，做不到会提前说。
- **爱好 / 话题**：运动、球队、赢和输（SPORT / PEOPLE / NIGHT）。
- **口吻**：大声、直，句尾带"就这么定了"。
- **背景**：
  1. 左手腕有旧伤，医生说少打，他改成左手发球、右手护腕。
  2. 球队只剩七个人，他一个一个劝回来的，劝人的方式是先陪对方打半场。
  3. 学校要砍球队经费那一年，他把训练时间改到清晨五点，硬撑了一年。
- **常驻**：体育馆球场。
- **相关**：SPORT 文化类、木工与运动器材。

- **头像提示词**：`tall college basketball captain, buzz cut, scar behind left ear, sweatband on right wrist, teardrop eyes, loud confident grin`
- **立绘提示词**：`tall basketball captain, full body, sleeveless training jersey and warm-up jacket, basketball under one arm, gym floor reflections, wide open stance`

### 19. 温宁 · npc.bit.wen_ning

- **身份**：大二。心理社团值班学姐。旧楼 204 那间小屋子。
- **形象**：柔软的长发扎成低马尾；宽松针织开衫；手上戴一副绒布手套也戴（冬天和别的季节都戴）；说话时会看着你的眼睛，但不超过三秒。
- **性格**：稳、会听、温和。她从不给建议，只帮你把话说完。
- **爱好 / 话题**：人、听故事、雨天（PEOPLE / RAIN / NIGHT）。
- **口吻**：重复你最后三个字，然后再往下说。
- **背景**：
  1. 她只记得来找她的人那天天气，不记谈话内容——她说是保护别人。
  2. 值守 204 一年，最常来的人其实不难过，只是没地方坐。
  3. 204 的门牌号在她来之前是 206，某个晚上换了，她没问。
- **常驻**：旧楼 204。
- **相关**：记忆类资源、雨天地点异常。

- **头像提示词**：`kind college counselor, low soft ponytail, oversized knit cardigan, wearing cloth gloves, calm empathetic eyes, soft lamplight`
- **立绘提示词**：`college counselor, full body, long knit cardigan, cloth gloves, sitting on a small wooden chair in a tiny lamp-lit room, two paper cups on the table`

### 20. 顾青 · npc.bit.gu_qing

- **身份**：大三。校园广播站播音。全校唯一用"我们"自称的人。
- **形象**：黑色西装外套搭在肩上，里面是宽松白衬衫；细框眼镜推在额头上；嗓子不好的时候包里总有润喉糖；播音时会不自觉地抬手理头发。
- **性格**：疏离、认真、观察力强。声音给全校听，人却很少被谁看见。
- **爱好 / 话题**：声音、雨、别人的校园广播（SOUND / WEATHER / NIGHT）。
- **口吻**：播音腔只在值班时用，私下说话很短。
- **背景**：
  1. 她录的黄昏播报被很多人当成背景音，她说那也算一种收听。
  2. 每逢下雨她主动申请加播，因为"雨天路上的人需要一个声音陪着"。
  3. 广播站旧档案里有她父亲的声音，他以前也在这个岗位。
- **常驻**：广播站。
- **相关**：SOUND 文化类、异常侧"只有一个人听"线索。

- **头像提示词**：`campus radio announcer student, black blazer over white shirt, glasses pushed up on forehead, throat lozenge in hand, composed low-voice expression`
- **立绘提示词**：`campus radio announcer, full body, blazer over loose white shirt, headphones around neck, standing in a small radio booth with mixing console, mic on boom arm`

### 21. 廖一 · npc.bit.liao_yi

- **身份**：大四。考研。占自习室第三个座位已经两年。
- **形象**：板寸；黑色卫衣；桌上摞着七本书，最上面一本翻到一半压着；笔袋里笔按颜色排；眼睛布满血丝，笑起来很腼腆。
- **性格**：不歇、倔、认真。不接受"来不及了"这个说法。
- **爱好 / 话题**：学习、笔记、把事情排成表（STUDY / BOOKS / NIGHT）。
- **口吻**：报数字，一天能干多少小时、还剩多少天。
- **背景**：
  1. 第一次考研差 18 分，第二年他把自己的作息表从 16 小时压到 20 小时。
  2. 自习室那个座位上刻着一个"廖"字，深度两毫米，第三次搬宿舍时他还在。
  3. 他给学弟写过一份资料清单，列了四页，自己一次都没用过。
- **常驻**：自习室。
- **相关**：KNOWLEDGE 资源、紙類消耗。

- **头像提示词**：`exhausted senior student, buzz cut, dark circles heavy, black hoodie, bloodshot eyes, shy faint smile, stack of books visible behind`
- **立绘提示词**：`senior student cramming, full body, black hoodie and jeans, pencil pouch with color-sorted pens, slouching at a study desk buried under seven books, night desk lamp glow`

### 22. 米娅 · npc.bit.mi_ya

- **身份**：交换生。学中文一年。语言学院旁听。
- **形象**：浅棕卷发扎成低马尾，戴一枚不合季节的贝壳发夹；穿羽绒服是一件明显大了两号的深蓝外套；笔记本摊开时中文和母语混写。
- **性格**：好奇、明亮、嘴碎。问问题不绕弯，被纠正时认真记下来。
- **爱好 / 话题**：吃的、语言、人情（FOOD / PEOPLE / STUDY）。
- **口吻**：说中文时不卡壳，卡壳时直接切母语再补一句中文。
- **背景**：
  1. 她的中文名字是给自己取的，理由是"听起来像我在食堂常点的那道菜"。
  2. 第一个月她把"多少"听成"桌布"，从此记住了多音字。
  3. 她在本子上画了一整页校门口的早餐摊，说这是她来这里的第一个早晨。
- **常驻**：教学楼 / 一食堂。
- **相关**：食物、文化类、跨文化对话支线。

- **头像提示词**：`foreign exchange student girl, light brown curly hair in low ponytail, shell hair clip, oversized navy down jacket, curious open smile, mixed-language notebook in hand`
- **立绘提示词**：`exchange student girl, full body, oversized navy puffer jacket, scarf, notebook open with mixed handwriting, standing by a campus breakfast stall, steam rising`

### 23. 骆师傅 · npc.bit.luo_shifu

- **身份**：校园水电维修。工龄二十年。三轮车上工具按大小排。
- **形象**：五十多，寸头花白，皮肤黑红；深灰工装，膝盖两块补丁；腰上挂一大串钥匙和一把永远借不出去的扳手；工具车三轮挡板贴了反光条。
- **性格**：不起眼、温和、务实。修东西时话特别多，修完就走。
- **爱好 / 话题**：钱、天气、哪栋楼又坏了（MONEY / WEATHER / TECH）。
- **口吻**：一边敲一边说，声音盖过机器。
- **背景**：
  1. 学校哪条水管是他装的，他都记得，包括三十年前那段没有图纸的。
  2. 工具车里有一样东西永远用不上：一把崭新的、只用来挂着的扳手。
  3. 他声称见过这栋楼每一个闭馆后的夜晚，但从不说明是哪一栋。
- **常驻**：工具车 → 各栋楼。
- **相关**：木材、金属类 CRAFT 资源、异常工位的前置。

- **头像提示词**：`middle-aged campus maintenance worker, greying buzz cut, dark sun-weathered skin, patched grey workwear, big key ring at belt, friendly shouting expression`
- **立绘提示词**：`campus maintenance worker, full body, patched grey work jumpsuit, tool belt with many keys, carrying a large wrench, standing beside a reflective-taped utility tricycle`

### 24. 姜小渔 · npc.bit.jiang_xiaoyu

- **身份**：大三。湿地与鸟类观察。小湖观测点的常驻。
- **形象**：深色冲锋衣裤全套，裤脚全是泥；一顶宽檐渔夫帽压得很低；望远镜挂在胸前；随身一个笔记本和一支铅笔，不用手机。
- **性格**：沉得住、好奇、疏离。等鸟的时候能一动不动站四十分钟。
- **爱好 / 话题**：植物、鸟、雨后的水（PLANTS / WEATHER / NIGHT）。
- **口吻**：轻声，报告式的："三点四十，两只。"说完就继续等。
- **背景**：
  1. 她的记录本里有 61 种鸟，其中 3 种是老师没见过的，她坚持自己的判断。
  2. 她钓到的鱼全部放回去，只为了确认"今年的鱼比去年早了两周"。
  3. 有一年冬天她在观测点等到一只从南方飞回来的鸟，比记录早了一天，那一页她写了又划，最后还是留着了。
- **常驻**：小湖湿地观测点。
- **相关**：NATURE + 湿地观察、天气类支线。

- **头像提示词**：`field ecology student, dark waterproof jacket and mud-stained pants, wide-brim bucket hat, binoculars on chest, low sunlit expression, holding a field notebook`
- **立绘提示词**：`field ecology student, full body, mud-stained waders, wide-brim hat, binoculars, standing still on a wetland boardwalk at dawn, reeds in foreground`

### 25. 樟叔 · npc.bit.zhang_shu

- **身份**：退休园林工。退休后在校门口修自行车，收费随心情。
- **形象**：七十多，背微驼；一件洗到发白的蓝布罩衫；戴老花镜挂绳；手指关节粗大变形；车棚下摆着一只搪瓷缸。
- **性格**：寡言、好奇、慢性子。听你说话时会一直看着你的鞋。
- **爱好 / 话题**：植物、老校园、树（PLANTS / MEMORY / NIGHT）。
- **口吻**：短句带点方言味道，问什么答什么，不主动说。
- **背景**：
  1. 校园里现存最老的一棵樟树是他 1980 年手种的，种的时候他还是临时工。
  2. 他能说出这栋楼拆之前的样子，也知道原来那儿有棵什么树。
  3. 有人问他为什么不再干了，他说"树不认得我了，我留着也是添乱"，但他每天还是来看一次。
- **常驻**：校门口车棚。
- **相关**：老照片、记忆类资源、校园旧地点串联。

- **头像提示词**：`very old retired groundskeeper, slightly hunched, faded blue cloth jacket, reading glasses on cord, thick deformed knuckles, enamel mug, gentle weathered face`
- **立绘提示词**：`retired old man, full body, faded blue cloth jacket, loose trousers, slippers, sitting on a stool beside a bicycle repair shed under a huge camphor tree, dappled light`

### 26. 老陈 · npc.bit.chen_jiu

- **身份**：旧体育馆地下看夜人。在这所学校的时间比任何人都长——包括校史馆的记录。
- **形象**：看不出年纪，穿一件洗得发灰的旧式夹克，扣子扣到顶；手电筒挂在胸前一圈电线；鞋是解放鞋，永远很干净；说话时先看一眼门口。
- **性格**：神秘、稳、不起眼。别人问什么他都答，但答案总留一半。
- **爱好 / 话题**：夜、规则、旧事（NIGHT / MEMORY / LAW）。
- **口吻**：慢、客气，措辞像在念规章。
- **背景**：
  1. 有人说他是 1952 年建校时的工人，有人说他那时还没出生。
  2. 地下室的钥匙只有他有一把，开门声是"两响半"——罗小满的编号 233 就是这个。
  3. 他每晚 1:10 会锁地下室的门，从不提前。有人在那之后进去过，门是开着的。
- **常驻**：旧体育馆地下室。
- **相关**：ANOMALY 核心来源 + 异常工位。

- **头像提示词**：`ageless old night watchman, grey faded old-style jacket buttoned to the throat, flashlight on a cord around chest, clean cloth shoes, glancing toward the door, dim concrete light`
- **立绘提示词**：`old night watchman in underground gym basement, full body, faded buttoned jacket, flashlight, worn cloth shoes, standing before a heavy locked door, single bulb overhead`

### 27. 三更 · npc.bit.san_geng

- **身份**：会说话的乌鸦。只在雨夜出现，落在礼堂屋脊、异常工位附近。
- **形象**：比普通乌鸦大一点，羽毛在光下泛蓝；右眼上方有一道旧疤；左爪是铜环，上面刻着一个看不清的字。
- **性格**：好奇、锋利、挑剔。会用人的语气说话，但语法是拼凑的。
- **爱好 / 话题**：夜、亮的东西、被藏起来的东西（NIGHT / ANOMALY / MEMORY）。
- **口吻**：短句，爱反问，会说"你身上有那个味道"。
- **背景**：
  1. 它会重复人说过的话，但只重复它觉得"重要"的那一句。
  2. 它的铜环和旧体育馆地下室某把锁上的刻字是同一套笔画。
  3. 有人见过它衔着一张 2008 年的合照，照片上没有它。
- **常驻**：雨夜各处，尤其是屋脊与异常工位。
- **相关**：ANOMALY 关键道具、引导型支线。

- **头像提示词**：`large talking crow, iridescent blue-black feathers, old scar above right eye, copper ring engraved on left leg, head tilted, rainy night atmosphere`
- **立绘提示词**：`anthropomorphic crow, full body, iridescent dark feathers spread slightly, copper ring on leg, perched on a gothic building rooftop ridge, rain streaks behind, glowing faint eyes`

### 28. 沈墨 · npc.bit.shen_mo

- **身份**：雨天才出现的人。不是学生，教职工名册里没有，访客记录里也不全。
- **形象**：黑伞总是偏向别人那侧；深灰长风衣，肩线很旧；白衬衫，最上面一颗扣着；湿发贴在额前；从不让雨落在他自己肩上。
- **性格**：神秘、疏离、温和。**他从不主动说话，只会等你先开口**。
- **爱好 / 话题**：雨、走过的路、被问到的事（RAIN / NIGHT / SELF）。
- **口吻**：安静，回答很短，答完会补一句无关的话。
- **背景**：
  1. 校园里所有人都见过他，但没人能说清他的名字——他自我介绍时说的是"沈"。
  2. 他只在雨真正落下来的时候出现，太阳一出就不见了，连影子都不留。
  3. 他认识夏星，也认识何；三个人的线索交叉在"2008 年那张合照"上。
- **常驻**：任何正在下雨的路。
- **相关**：雨天限定 ANOMALY 资源、"雨停"即离场。

- **头像提示词**：`mysterious man appearing only in rain, wet black hair plastered to forehead, grey long coat, collar up, holding a black umbrella tilted away from himself, gentle unreadable eyes`
- **立绘提示词**：`man in the rain, full body, long grey coat, white shirt, black umbrella, standing on a rain-soaked campus road at dusk, rain falling, wet reflections on pavement`

### 29. 夏星 · npc.bit.xia_xing

- **身份**：出现在 2008 年那张合照里的人。她会背出照片上每个人的名字，但没人记得她的。
- **形象**：及腰黑发在阳光里有一点透明感；白色连衣裙加一件薄开衫；赤脚（永远）；眼睛是很浅的灰蓝，看人的时候像在看很久以前的事。
- **性格**：明亮、观察力强、疏离。她对谁都很熟，但所有人都对她客气得像对客人。
- **爱好 / 话题**：记忆、照片、天气（MEMORY / ART / WEATHER）。
- **口吻**：礼貌、怀旧，会用只有熟人才用的称呼，然后发现自己不该这么叫。
- **背景**：
  1. 她是校史馆那张 2008 年合照里第八个孩子，陈秋记得有八个人，档案只写了七个。
  2. 苗小蝶第 182 张底片里多出来的那个背影，衣服和她今天穿的一样。
  3. 她自己记得所有事，也知道所有人都会忘记她——她说"这样反而比较轻松"。
- **常驻**：校史馆照片墙 / 晴天时的旧操场。
- **相关**：MEMORY + ANOMALY 交界的核心 NPC。

- **头像提示词**：`ethereal girl with waist-length translucent black hair, pale grey-blue eyes, white dress with thin cardigan, barefoot, nostalgic gentle expression, soft sunlight`
- **立绘提示词**：`ethereal girl, full body, white dress and thin cardigan, barefoot, standing in front of a wall of old black-and-white school photographs, sunlight through window dust`

### 30. 娄七 · npc.bit.lou_qi

- **身份**：失物招领处管理员。房间不在任何一张图纸上——旧体育馆地下室往里走到底。
- **形象**：三十来，短发，戴一副厚框眼镜；穿深绿色旧毛衣，外面套工装围裙；腰上挂一大串钥匙和一个小本子；手边永远摊着几件不属于任何人的东西。
- **性格**：稳、讲义气、神秘。对任何人都客气，但从不劝人把东西留在她这儿。
- **爱好 / 话题**：记忆、丢的东西、名字（MEMORY / ANOMALY / PEOPLE）。
- **口吻**：平和，办事利索，收东西时一定要问一句"这个你还要吗"。
- **背景**：
  1. 失物招领处登记本上有些东西领走了，签名是空的——东西确实不见了。
  2. 她知道每一件东西"为什么被丢下"，但只说给愿意听的人听。
  3. 她和夏星是旧识，夏星说她"从一开始就在这儿了"。
- **常驻**：旧体育馆地下室最里面的房间。
- **相关**：ANOMALY 核心、全部 MEMORY 资源的收口。

- **头像提示词**：`calm woman in her thirties running a lost-and-found, short hair, thick-rim glasses, deep green old sweater, work apron, big key ring, kind steady eyes`
- **立绘提示词**：`lost-and-found keeper, full body, green sweater and work apron, keys at waist, holding an unfamiliar old object, standing in a small basement room with shelves of odd items, single warm lamp`

---

## 五、关系网（供对话与支线设计参考）

```
        ┌───────────── 日常侧 ─────────────┐
        林(图书馆) ── 苏黎(仰慕, 不敢搭话) ── 陈秋(校史馆)
        齐(林子) ── 蒋成一(助手) ── 樟叔(旧同事)
        周(食堂) ── 唐糖(后厨) ── 米娅(常客)
        沈砚(实验楼) ── 欧阳让(地下) ── 骆师傅(维修)
        白依(缝纫社) ── 高见(社团经费) ── 温宁(心理社团)
        苗小蝶(暗房) ── 顾青(广播) ── 罗小满(声音采集)
        江予 ── 阿武(球场) ── 廖一(自习) ── 姜小渔(观测点)
                          │
        ┌───────────── 异常侧 ─────────────┐
        沈墨(雨) ── 夏星(2008 照片) ── 何(夜行)
                          │
              老陈(地下钥匙) ── 三更(铜环)
                          │
                     娄七(失物处)
```

- **三条可交叉主线**：
  1. **2008 年那张合照**（第八个孩子）→ 夏星 ← 陈秋 / 苗小蝶 第 182 张
  2. **地下室那把锁**（两响半）→ 老陈 ← 罗小满 编号 233 / 三更的铜环 ← 娄七
  3. **雨夜的伞**（沈墨从不让雨落在自己肩上）→ 韦禾的值班本 ← 何的床位

## 六、特殊角色的出场规则（供程序参考，不进 json）

| 角色 | 出现条件 | 消失条件 |
|------|----------|----------|
| 沈墨 | 玩家所在处正在下雨 | 雨停即刻消失，不留掉落物 |
| 三更 | 雨夜 + 玩家持有 ANOMALY 类资源 或 深夜 23:00 后 | 天亮或玩家靠近时飞走 |
| 夏星 | 晴天 + 玩家在照片相关地点（校史馆/暗房/旧操场） | 与特定 NPC 对话后逐步淡出 |
| 老陈 | 23:00 后进入旧体育馆地下室 | 1:10 锁门前 |
| 娄七 | 玩家已至少见过何 / 夏星 / 三更 中的两个 | 无（常驻） |

## 七、字段补充：礼物偏好与作息

本轮按评审意见新增两个字段，并给原 5 位"只有姓"的角色补全名。

| 字段 | 约定 |
|------|------|
| `giftPreferences` | `{ liked: [resourceId], disliked: [resourceId] }`。liked 3–4 项、disliked 1–2 项，**必须引用 `resources.json` 中已存在的 id**（470 项，转换脚本会校验） |
| `schedule` | `[{ startMinute, placeId, travelMinutes, activity }]`。`startMinute` 为 0–1439 的当日分钟数；`placeId` 见下方占位表；`travelMinutes` 为从上一处走到本处所需分钟 |
| 名字 | 原 5 人保留 `id` 不变（`npc.bit.lin` 等），`name` 补成全名：林砚 / 周野 / 徐晚 / 何岸 / 齐岭 |

### 7.1 地点占位表（**临时占位，待地点数据扩充后统一替换**）

沿用示例命名风格 `place.bit.<snake_case>`。带 ✔ 的是示例 `npcs.json` 里已有的，其余为本次新增占位。

| placeId | 说明 | 来源 |
|---------|------|------|
| `place.bit.dorm_3` | 宿舍 3 号楼 | ✔ 已有 |
| `place.bit.library` | 图书馆借还与阅览 | ✔ 已有 |
| `place.bit.canteen_one` | 一食堂（打饭窗口） | ✔ 已有 |
| `place.bit.north_lake` | 小湖北岸 | ✔ 已有 |
| `place.bit.garden` | 花园 | ✔ 已有 |
| `place.bit.plaza` | 中心广场 | ✔ 已有 |
| `place.bit.orchard` | 果林 | ✔ 已有 |
| `place.bit.berry_bush` | 浆果丛 | ✔ 已有 |
| `place.bit.mushroom_patch` | 树根菌子区 | ✔ 已有 |
| `place.bit.dorm_1` | 1 号宿舍楼 | 新增占位 |
| `place.bit.dorm_2` | 2 号宿舍楼 | 新增占位 |
| `place.bit.dorm_3` | 3 号宿舍楼 | ✔ 已有 |
| `place.bit.dorm_4` | 4 号宿舍楼 | 新增占位 |
| `place.bit.dorm_roof` | 宿舍天台 / 阳台 | 新增占位 |
| `place.bit.library_four` | 图书馆四楼自习区 | 新增占位 |
| `place.bit.darkroom` | 旧图书馆三层暗房 | 新增占位 |
| `place.bit.stacks` | 旧馆书架通道 | 新增占位 |
| `place.bit.canteen_kitchen` | 一食堂后厨 | 新增占位 |
| `place.bit.dining_line` | 打饭队列 | 新增占位 |
| `place.bit.lake_wood` | 湖边木栈道 | 新增占位 |
| `place.bit.wetland_board` | 湿地观测点 | 新增占位 |
| `place.bit.field_plot` | 菜圃 | 新增占位 |
| `place.bit.lab_building` | 实验楼 | 新增占位 |
| `place.bit.lab_415` | 实验楼 415 工位 | 新增占位 |
| `place.bit.lab_basement` | 实验楼地下低温室 | 新增占位 |
| `place.bit.workshop` | 机工房 / 维修间 | 新增占位 |
| `place.bit.art_room` | 艺术楼画室 | 新增占位 |
| `place.bit.art_terrace` | 艺术楼天台 | 新增占位 |
| `place.bit.music_room` | 音乐教室 | 新增占位 |
| `place.bit.sewing_room` | 综合楼 B1 缝纫社 | 新增占位 |
| `place.bit.laundry` | 宿舍楼洗衣房 | 新增占位 |
| `place.bit.gate_north` | 北门值班室 | 新增占位 |
| `place.bit.bike_shed` | 校门车棚 | 新增占位 |
| `place.bit.history_museum` | 校史馆 | 新增占位 |
| `place.bit.photo_wall` | 校史馆照片墙 | 新增占位 |
| `place.bit.radio_studio` | 广播站 | 新增占位 |
| `place.bit.study_room` | 自习室 | 新增占位 |
| `place.bit.club_center` | 学生活动中心 | 新增占位 |
| `place.bit.playground` | 操场看台台阶 | 新增占位 |
| `place.bit.lost_and_found` | 失物招领处（不在图纸上的房间） | 新增占位 |
| `place.bit.room_204` | 旧楼 204 | 新增占位 |
| `place.bit.moot_court` | 模拟法庭 | 新增占位 |
| `place.bit.gym` | 体育馆球场 | 新增占位 |
| `place.bit.gym_basement` | 旧体育馆地下室 | 新增占位 |
| `place.bit.lecture_hall` | 礼堂 | 新增占位 |
| `place.bit.hall_roof` | 礼堂屋脊 | 新增占位 |
| `place.bit.campus_road` | 校园主干道 | 新增占位 |
| `place.bit.rain_road` | 任意"正在下雨的路" | 新增占位（沈墨专用） |
| `place.bit.old_ground` | 旧操场（已停用） | 新增占位（夏星专用） |

> 转换 json 时会一并校验：`schedule.placeId` 只需字符串非空即可（地点数据尚未扩充，不做引用校验），待地点表补齐后再加校验。

---

## 八、礼物偏好 giftPreferences（30 人）

liked 给"送礼加好感"，disliked 给"送礼扣好感"，每条后面标注理由，方便你判断是否合理。

| # | 姓名 | liked（3–4 项） | disliked（1–2 项） |
|---|------|------------------|---------------------|
| 1 | 林砚 | `res.blueprint_book` `res.mechanical_drawing` `res.umbrella` `res.pencil` | `res.popcorn`（他嫌吵） |
| 2 | 周野 | `res.canteen_bowl` `res.campus_bread` `res.pickled_radish` `res.egg_pancake` | `res.remedy_powder`（他什么都往嘴里放，唯独不吃药） |
| 3 | 徐晚 | `res.paint_brush` `res.palette` `res.pigment` `res.wild_chrysanthemum` | `res.mirror`（她不画也不看镜子） |
| 4 | 何岸 | `res.flashlight` `res.shelf_card` `res.borrow_stub` `res.archive_letter` | `res.crow_feather` `res.bat_wing`（夜里看见会绕路） |
| 5 | 齐岭 | `res.tree_sap` `res.tea_leaf` `res.green_apple` `res.pruning_shears` | `res.candle`（会招虫，他院里一根都不点） |
| 6 | 沈砚 | `res.copper_wire` `res.solder_iron` `res.repair_manual` `res.led_bulb` | `res.paper_star`（叠一次扔一次） |
| 7 | 苏黎 | `res.sticker_sheet` `res.notebook` `res.paper_sheet` `res.borrow_stub` | `res.firecracker_ash`（突然的响声会吓到她） |
| 8 | 陈秋 | `res.old_photo` `res.yearbook_page` `res.tea_leaf` `res.monument_rubbing` | `res.speaker`（正进行式的扩音） |
| 9 | 高见 | `res.club_flyer` `res.plaza_flyer` `res.badge_ribbon` `res.poster_paint` | `res.torn_letter`（他见不得废纸） |
| 10 | 白依 | `res.linen_thread` `res.needle` `res.dyed_fabric` `res.felt` | `res.glue`（味道洗不掉，她宁可返工） |
| 11 | 罗小满 | `res.drum_stick` `res.tambourine` `res.small_bell` `res.music_score_fragment` | `res.lost_bell`（丢掉的铃铛让她的录音编号对不上） |
| 12 | 蒋成一 | `res.seed_saving_note` `res.watering_can` `res.farm_cabbage` `res.wheat_ear` | `res.clock`（他讨厌被催） |
| 13 | 韦禾 | `res.thermos` `res.coffee` `res.umbrella` `res.wrist_band` | `res.firecracker_ash`（值班时最烦这个） |
| 14 | 苗小蝶 | `res.film_roll` `res.photo_paper` `res.photo_print` `res.developer` | `res.flashlight`（会毁掉她的暗房适应） |
| 15 | 欧阳让 | `res.mica_flake` `res.iron_ingot` `res.repair_manual` `res.clock` | `res.crow_feather`（他只信任仪表） |
| 16 | 唐糖 | `res.candy_grain` `res.flour` `res.honey` `res.campus_bread` | `res.vegetable_oil`（味会渗进工服） |
| 17 | 江予 | `res.transcript` `res.register_ledger` `res.blackboard_photo` `res.paperweight` | `res.lucky_knot`（她说靠运气说话的人赢不了） |
| 18 | 阿武 | `res.wrist_band` `res.cheer_megaphone` `res.club_pin` `res.paper_star` | `res.sleeping_bag`（他讨厌睡不够） |
| 19 | 温宁 | `res.tea_egg` `res.small_table` `res.cushion` `res.sticker_sheet` | `res.extra_lamp`（走廊尽头多出来的那盏） |
| 20 | 顾青 | `res.headphones` `res.radio` `res.speaker` `res.music_score_fragment` | `res.river_snail`（底噪像蜗牛爬） |
| 21 | 廖一 | `res.exam_notes` `res.math_formula_note` `res.coffee` `res.alarm_clock` | `res.plaza_flyer`（旁边有人在玩就学不进去） |
| 22 | 米娅 | `res.instant_noodle` `res.milk_bottle` `res.canteen_bowl` `res.popcorn` | `res.remedy_powder`（苦到她说不出中文） |
| 23 | 骆师傅 | `res.key` `res.wood_tag` `res.hand_hammer` `res.gloves` | `res.canvas_bag`（磨腰） |
| 24 | 姜小渔 | `res.bird_watch_sheet` `res.field_manual` `res.tide_note` `res.fishing_rod` | `res.crow_feather`（惊鸟的东西她不碰） |
| 25 | 樟叔 | `res.ginkgo_leaf` `res.tea_leaf` `res.bamboo_strip` `res.wind_chime` | `res.frost_flower`（假花） |
| 26 | 老陈 | `res.patrol_log` `res.lantern_riddle` `res.small_bell` `res.wind_chime` | `res.same_bird`（他讨厌重复的东西） |
| 27 | 三更 | `res.small_bell` `res.glass_lamp` `res.rain_droplet` `res.writing_pen` | `res.wood_tag`（刻字的木头他嫌无聊） |
| 28 | 沈墨 | `res.umbrella` `res.rain_droplet` `res.raincoat` `res.tea_drink` | `res.summer_heat`（他只在雨里） |
| 29 | 夏星 | `res.founding_photo` `res.group_photo` `res.photo_strip` `res.polaroid` | `res.campus_calendar`（她停在过去） |
| 30 | 娄七 | `res.lost_and_found_record` `res.anomaly_key` `res.old_uniform` `res.keychain` | `res.diary_page`（她不看别人的日记） |

> 说明：`disliked` 多数不是"讨厌这个东西"，而是"这个东西会勾起某段经历"，负面礼物设计成剧情钩子比单纯扣好感更有用。

---

## 九、作息 schedule（30 人）

格式为最终 json 结构。`travelMinutes` 只在"从上一处走到本处"有意义；跨楼栋的行走时间已按 15–45 分钟取值。

### 1. 林砚 · npc.bit.lin

```json
[
  { "startMinute": 420, "placeId": "place.bit.dorm_3", "travelMinutes": 0, "activity": "起床，收拾书包" },
  { "startMinute": 510, "placeId": "place.bit.dining_line", "travelMinutes": 20, "activity": "早饭，站着吃完就走" },
  { "startMinute": 540, "placeId": "place.bit.library", "travelMinutes": 25, "activity": "三楼靠窗第三排，摊开两本书" },
  { "startMinute": 750, "placeId": "place.bit.dining_line", "travelMinutes": 20, "activity": "午饭，把书留在桌上占座" },
  { "startMinute": 810, "placeId": "place.bit.library", "travelMinutes": 15, "activity": "下午继续画校园地图" },
  { "startMinute": 1110, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "晚饭" },
  { "startMinute": 1200, "placeId": "place.bit.library", "travelMinutes": 20, "activity": "自习到闭馆" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_3", "travelMinutes": 30, "activity": "回宿舍，路上绕远看雨" }
]
```

### 2. 周野 · npc.bit.zhou

```json
[
  { "startMinute": 350, "placeId": "place.bit.plaza", "travelMinutes": 0, "activity": "慢跑第一圈" },
  { "startMinute": 420, "placeId": "place.bit.plaza", "travelMinutes": 0, "activity": "慢跑第二圈，然后拉伸" },
  { "startMinute": 660, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 15, "activity": "后厨备菜、切配" },
  { "startMinute": 750, "placeId": "place.bit.dining_line", "travelMinutes": 10, "activity": "打饭窗口，帮人添饭" },
  { "startMinute": 900, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 5, "activity": "收餐盘、洗第一轮" },
  { "startMinute": 1230, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 0, "activity": "腌一坛菜，等它慢慢发" },
  { "startMinute": 1350, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍，边走边听广播" }
]
```

### 3. 徐晚 · npc.bit.xu

```json
[
  { "startMinute": 0, "placeId": "place.bit.north_lake", "travelMinutes": 0, "activity": "整夜在湖边，架着画架" },
  { "startMinute": 540, "placeId": "place.bit.lake_wood", "travelMinutes": 10, "activity": "沿栈道走一段，看水面" },
  { "startMinute": 660, "placeId": "place.bit.canteen_one", "travelMinutes": 25, "activity": "买一份饭，带回湖边吃" },
  { "startMinute": 780, "placeId": "place.bit.north_lake", "travelMinutes": 20, "activity": "继续画，一直画到天色变" },
  { "startMinute": 1140, "placeId": "place.bit.art_room", "travelMinutes": 30, "activity": "把湿画夹进板子，回画室" },
  { "startMinute": 1230, "placeId": "place.bit.dorm_2", "travelMinutes": 25, "activity": "睡觉，窗帘拉严" },
  { "startMinute": 1380, "placeId": "place.bit.north_lake", "travelMinutes": 20, "activity": "天没亮就又回湖边了" }
]
```

### 4. 何岸 · npc.bit.he

> 末段跨零点，`startMinute` 大于前一段即表示次日，请程序侧按"跨夜"处理。

```json
[
  { "startMinute": 60, "placeId": "place.bit.dorm_3", "travelMinutes": 0, "activity": "回到那个没人睡的床位，坐一会儿就出门" },
  { "startMinute": 180, "placeId": "place.bit.garden", "travelMinutes": 15, "activity": "在花园长椅上待到天亮" },
  { "startMinute": 300, "placeId": "place.bit.stacks", "travelMinutes": 20, "activity": "旧馆书架通道，翻书目卡" },
  { "startMinute": 600, "placeId": "place.bit.campus_road", "travelMinutes": 15, "activity": "走在没人的路上，避开人多的那段" },
  { "startMinute": 780, "placeId": "place.bit.garden", "travelMinutes": 15, "activity": "坐在同一张长椅上" },
  { "startMinute": 1140, "placeId": "place.bit.stacks", "travelMinutes": 20, "activity": "把某张卡放回原处" },
  { "startMinute": 1380, "placeId": "place.bit.campus_road", "travelMinutes": 15, "activity": "又走到雨里也不肯打伞的地方" }
]
```

### 5. 齐岭 · npc.bit.qi

```json
[
  { "startMinute": 400, "placeId": "place.bit.bike_shed", "travelMinutes": 0, "activity": "从校门进来，先去车棚放车" },
  { "startMinute": 420, "placeId": "place.bit.orchard", "travelMinutes": 15, "activity": "果林巡一圈，看哪边熟透了" },
  { "startMinute": 780, "placeId": "place.bit.berry_bush", "travelMinutes": 25, "activity": "浆果丛边蹲着，标了号就不摘" },
  { "startMinute": 1020, "placeId": "place.bit.field_plot", "travelMinutes": 20, "activity": "菜圃，替园艺社看苗" },
  { "startMinute": 1140, "placeId": "place.bit.mushroom_patch", "travelMinutes": 20, "activity": "树根边找菌子，只找不采" },
  { "startMinute": 1230, "placeId": "place.bit.dining_line", "travelMinutes": 25, "activity": "在食堂吃自己摘的菜" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_2", "travelMinutes": 25, "activity": "回宿舍，把布袋里的标签整理好" }
]
```

### 6. 沈砚 · npc.bit.shen_yan

```json
[
  { "startMinute": 420, "placeId": "place.bit.dorm_3", "travelMinutes": 0, "activity": "起床，先在宿舍听两分钟电流声" },
  { "startMinute": 510, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 570, "placeId": "place.bit.lab_415", "travelMinutes": 25, "activity": "开工位，先处理昨天报修的三台" },
  { "startMinute": 750, "placeId": "place.bit.dining_line", "travelMinutes": 20, "activity": "午饭，端着盒饭回工位边吃边焊" },
  { "startMinute": 780, "placeId": "place.bit.lab_415", "travelMinutes": 5, "activity": "继续" },
  { "startMinute": 1080, "placeId": "place.bit.workshop", "travelMinutes": 15, "activity": "去机工房领零件，顺路修台风扇" },
  { "startMinute": 1230, "placeId": "place.bit.lab_415", "travelMinutes": 15, "activity": "贴今天的日期胶带" },
  { "startMinute": 1350, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍，路上还在听机器的声音" }
]
```

### 7. 苏黎 · npc.bit.su_li

```json
[
  { "startMinute": 450, "placeId": "place.bit.dorm_1", "travelMinutes": 0, "activity": "起床，先在阳台站一会儿" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，一个人坐角落" },
  { "startMinute": 600, "placeId": "place.bit.library_four", "travelMinutes": 25, "activity": "四楼最靠里的座位，摊开手账" },
  { "startMinute": 780, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，避开人最多的那阵" },
  { "startMinute": 840, "placeId": "place.bit.library_four", "travelMinutes": 20, "activity": "下午继续写" },
  { "startMinute": 1140, "placeId": "place.bit.dorm_1", "travelMinutes": 25, "activity": "回宿舍，翻窗框上那张贴纸" },
  { "startMinute": 1320, "placeId": "place.bit.club_center", "travelMinutes": 20, "activity": "在活动中心门口站着看别人排练" }
]
```

### 8. 陈秋 · npc.bit.chen_qiu

```json
[
  { "startMinute": 480, "placeId": "place.bit.dorm_4", "travelMinutes": 0, "activity": "起床，先泡第一壶茶" },
  { "startMinute": 570, "placeId": "place.bit.photo_wall", "travelMinutes": 25, "activity": "开馆，戴上手套，取下今天要修的相框" },
  { "startMinute": 720, "placeId": "place.bit.history_museum", "travelMinutes": 5, "activity": "查名册，核对第 2008 届那一页" },
  { "startMinute": 810, "placeId": "place.bit.canteen_one", "travelMinutes": 25, "activity": "午饭，同事留的一份" },
  { "startMinute": 870, "placeId": "place.bit.history_museum", "travelMinutes": 20, "activity": "下午整理捐赠文件" },
  { "startMinute": 1110, "placeId": "place.bit.history_museum", "travelMinutes": 0, "activity": "闭馆前再把所有照片看一遍" },
  { "startMinute": 1230, "placeId": "place.bit.dorm_4", "travelMinutes": 25, "activity": "回宿舍，收音机开着当背景" }
]
```

### 9. 高见 · npc.bit.gao_jian

```json
[
  { "startMinute": 420, "placeId": "place.bit.dorm_3", "travelMinutes": 0, "activity": "起床，边刷牙边回消息" },
  { "startMinute": 510, "placeId": "place.bit.dining_line", "travelMinutes": 20, "activity": "早饭，边吃边派活" },
  { "startMinute": 570, "placeId": "place.bit.club_center", "travelMinutes": 20, "activity": "社团联合会开会，先过一遍本周经费" },
  { "startMinute": 750, "placeId": "place.bit.dining_line", "travelMinutes": 15, "activity": "午饭，速战速决" },
  { "startMinute": 810, "placeId": "place.bit.lecture_hall", "travelMinutes": 20, "activity": "礼堂那边盯场地布置" },
  { "startMinute": 1080, "placeId": "place.bit.club_center", "travelMinutes": 15, "activity": "回办公室改海报文案" },
  { "startMinute": 1230, "placeId": "place.bit.dining_line", "travelMinutes": 15, "activity": "晚饭，请客（他请得起的时候）" },
  { "startMinute": 1320, "placeId": "place.bit.lecture_hall", "travelMinutes": 20, "activity": "散场后一个人搬椅子" }
]
```

### 10. 白依 · npc.bit.bai_yi

```json
[
  { "startMinute": 450, "placeId": "place.bit.dorm_2", "travelMinutes": 0, "activity": "起床，量一下自己的袖长（职业病）" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 600, "placeId": "place.bit.sewing_room", "travelMinutes": 25, "activity": "开门，先检查昨天的绷布还紧不紧" },
  { "startMinute": 750, "placeId": "place.bit.sewing_room", "travelMinutes": 10, "activity": "午饭在缝纫机边吃" },
  { "startMinute": 810, "placeId": "place.bit.sewing_room", "travelMinutes": 0, "activity": "接单，量尺寸" },
  { "startMinute": 1080, "placeId": "place.bit.laundry", "travelMinutes": 15, "activity": "去洗衣房取洗好的衬里布" },
  { "startMinute": 1170, "placeId": "place.bit.sewing_room", "travelMinutes": 15, "activity": "锁边、钉扣子" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_2", "travelMinutes": 25, "activity": "回宿舍，手上还别着针" }
]
```

### 11. 罗小满 · npc.bit.luo_xiaoman

```json
[
  { "startMinute": 540, "placeId": "place.bit.dorm_4", "travelMinutes": 0, "activity": "起床，先敲两下床板当节拍器" },
  { "startMinute": 630, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 690, "placeId": "place.bit.music_room", "travelMinutes": 20, "activity": "一个人排练，回路弹了四十分钟" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 15, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.playground", "travelMinutes": 15, "activity": "看台台阶，录操场上所有声音" },
  { "startMinute": 1020, "placeId": "place.bit.music_room", "travelMinutes": 15, "activity": "整理编号本" },
  { "startMinute": 1140, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 20, "activity": "去后厨借一口锅，敲" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_4", "travelMinutes": 20, "activity": "回宿舍，路上还在哼" }
]
```

### 12. 蒋成一 · npc.bit.jiang_chengyi

```json
[
  { "startMinute": 400, "placeId": "place.bit.dorm_1", "travelMinutes": 0, "activity": "起床，先去楼道窗台看苗" },
  { "startMinute": 480, "placeId": "place.bit.field_plot", "travelMinutes": 15, "activity": "菜圃，浇水" },
  { "startMinute": 570, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，边吃边翻种植笔记" },
  { "startMinute": 630, "placeId": "place.bit.orchard", "travelMinutes": 20, "activity": "果林找齐岭，听他讲这周该采什么" },
  { "startMinute": 750, "placeId": "place.bit.dining_line", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.field_plot", "travelMinutes": 20, "activity": "搭架子，记第 43 号苗的位置" },
  { "startMinute": 1080, "placeId": "place.bit.orchard", "travelMinutes": 20, "activity": "果林收工，顺手捡落叶做覆盖" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_1", "travelMinutes": 25, "activity": "回宿舍写观察日记" }
]
```

### 13. 韦禾 · npc.bit.wei_he

```json
[
  { "startMinute": 1260, "placeId": "place.bit.dorm_4", "travelMinutes": 0, "activity": "接班前在家躺一会儿" },
  { "startMinute": 1380, "placeId": "place.bit.gate_north", "travelMinutes": 25, "activity": "到岗，泡上第一壶水" },
  { "startMinute": 480, "placeId": "place.bit.gate_north", "travelMinutes": 0, "activity": "凌晨巡逻，一圈十五分钟" },
  { "startMinute": 690, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "去食堂打热水，替夜班的留一份" },
  { "startMinute": 720, "placeId": "place.bit.gate_north", "travelMinutes": 15, "activity": "回岗，天快亮了" },
  { "startMinute": 840, "placeId": "place.bit.laundry", "travelMinutes": 20, "activity": "把值班室床单送洗" },
  { "startMinute": 900, "placeId": "place.bit.gate_north", "travelMinutes": 15, "activity": "交班，签字" }
]
```

### 14. 苗小蝶 · npc.bit.miao_xiaodie

```json
[
  { "startMinute": 480, "placeId": "place.bit.dorm_2", "travelMinutes": 0, "activity": "起床，先在窗台晾一张没洗的底片" },
  { "startMinute": 570, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 630, "placeId": "place.bit.darkroom", "travelMinutes": 25, "activity": "暗房，红灯下看今天该洗哪几张" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，一个人在楼梯上吃完" },
  { "startMinute": 810, "placeId": "place.bit.lake_wood", "travelMinutes": 20, "activity": "去拍那棵树，第 217 天" },
  { "startMinute": 1020, "placeId": "place.bit.darkroom", "travelMinutes": 20, "activity": "回暗房显影" },
  { "startMinute": 1200, "placeId": "place.bit.photo_wall", "travelMinutes": 20, "activity": "去校史馆比对 2008 年的照片" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_2", "travelMinutes": 25, "activity": "回宿舍，底片夹在书里" }
]
```

### 15. 欧阳让 · npc.bit.ouyang_rang

```json
[
  { "startMinute": 0, "placeId": "place.bit.lab_basement", "travelMinutes": 0, "activity": "已经在低温室，第三次读数" },
  { "startMinute": 360, "placeId": "place.bit.lab_basement", "travelMinutes": 0, "activity": "换液，等下一轮降温" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "上来吃饭，筷子拿反了两次" },
  { "startMinute": 600, "placeId": "place.bit.lab_basement", "travelMinutes": 20, "activity": "下去，忘了吃晚饭" },
  { "startMinute": 1140, "placeId": "place.bit.lab_415", "travelMinutes": 15, "activity": "上去查仪器记录" },
  { "startMinute": 1200, "placeId": "place.bit.lab_basement", "travelMinutes": 15, "activity": "又下去" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍躺两小时（等于没睡）" }
]
```

### 16. 唐糖 · npc.bit.tang_tang

```json
[
  { "startMinute": 420, "placeId": "place.bit.dorm_2", "travelMinutes": 0, "activity": "起床，边化妆边看食谱" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，兼看今天有什么剩料" },
  { "startMinute": 630, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 15, "activity": "后厨开工，先称黄油" },
  { "startMinute": 900, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 0, "activity": "烤第一炉，给师兄们留一盘" },
  { "startMinute": 1050, "placeId": "place.bit.canteen_one", "travelMinutes": 5, "activity": "看甜品被拿走，记谁没来" },
  { "startMinute": 1140, "placeId": "place.bit.canteen_kitchen", "travelMinutes": 5, "activity": "洗模、整理冷柜" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_2", "travelMinutes": 20, "activity": "回宿舍，围裙挂在门后" }
]
```

### 17. 江予 · npc.bit.jiang_yu

```json
[
  { "startMinute": 420, "placeId": "place.bit.dorm_3", "travelMinutes": 0, "activity": "起床，把要背的稿子读一遍" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，边吃边听播客" },
  { "startMinute": 600, "placeId": "place.bit.moot_court", "travelMinutes": 25, "activity": "模拟法庭，占最好的位子" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.library_four", "travelMinutes": 20, "activity": "查判例，抄别人说话的口头禅" },
  { "startMinute": 1110, "placeId": "place.bit.moot_court", "travelMinutes": 20, "activity": "辩论队训练" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍，还在改稿" }
]
```

### 18. 阿武 · npc.bit.a_wu

```json
[
  { "startMinute": 300, "placeId": "place.bit.gym", "travelMinutes": 0, "activity": "清晨训练，全队最早到" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，吃到能吃两碗" },
  { "startMinute": 600, "placeId": "place.bit.gym", "travelMinutes": 20, "activity": "战术课，带新来的" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.gym", "travelMinutes": 20, "activity": "下午加练投篮" },
  { "startMinute": 1080, "placeId": "place.bit.gym", "travelMinutes": 0, "activity": "清场，一个人擦篮板" },
  { "startMinute": 1260, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "请队员吃饭" },
  { "startMinute": 1350, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍，护腕没摘" }
]
```

### 19. 温宁 · npc.bit.wen_ning

```json
[
  { "startMinute": 540, "placeId": "place.bit.dorm_4", "travelMinutes": 0, "activity": "起床，把 204 的窗开一指宽" },
  { "startMinute": 630, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 690, "placeId": "place.bit.room_204", "travelMinutes": 20, "activity": "开门，烧水，摆两个杯子" },
  { "startMinute": 780, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 840, "placeId": "place.bit.room_204", "travelMinutes": 20, "activity": "下午值班，听" },
  { "startMinute": 1080, "placeId": "place.bit.garden", "travelMinutes": 15, "activity": "去花园坐十分钟（她自己的时间）" },
  { "startMinute": 1140, "placeId": "place.bit.room_204", "travelMinutes": 15, "activity": "回 204，天黑前把灯调暗" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_4", "travelMinutes": 20, "activity": "回宿舍" }
]
```

### 20. 顾青 · npc.bit.gu_qing

```json
[
  { "startMinute": 570, "placeId": "place.bit.dorm_2", "travelMinutes": 0, "activity": "起床，含着润喉糖试音" },
  { "startMinute": 660, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭" },
  { "startMinute": 720, "placeId": "place.bit.radio_studio", "travelMinutes": 20, "activity": "早间播报，念天气和早安" },
  { "startMinute": 810, "placeId": "place.bit.radio_studio", "travelMinutes": 0, "activity": "剪播音垫乐" },
  { "startMinute": 900, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，耳机不摘" },
  { "startMinute": 960, "placeId": "place.bit.library", "travelMinutes": 20, "activity": "还书，顺路在四楼待一会儿" },
  { "startMinute": 1140, "placeId": "place.bit.radio_studio", "travelMinutes": 20, "activity": "黄昏播报（雨天必加播）" },
  { "startMinute": 1230, "placeId": "place.bit.radio_studio", "travelMinutes": 0, "activity": "把今天的播音稿归档" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_2", "travelMinutes": 20, "activity": "回宿舍，走很慢" }
]
```

### 21. 廖一 · npc.bit.liao_yi

```json
[
  { "startMinute": 360, "placeId": "place.bit.study_room", "travelMinutes": 0, "activity": "第三个座位，永远是这个座位" },
  { "startMinute": 720, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，20 分钟" },
  { "startMinute": 780, "placeId": "place.bit.study_room", "travelMinutes": 20, "activity": "下午，泡掉第三杯咖啡" },
  { "startMinute": 1110, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "晚饭" },
  { "startMinute": 1170, "placeId": "place.bit.study_room", "travelMinutes": 20, "activity": "晚自习，看表算还能学几小时" },
  { "startMinute": 1320, "placeId": "place.bit.study_room", "travelMinutes": 0, "activity": "闭馆前最后一段" },
  { "startMinute": 1350, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "回宿舍路上买一个包子" }
]
```

### 22. 米娅 · npc.bit.mi_ya

```json
[
  { "startMinute": 480, "placeId": "place.bit.dorm_2", "travelMinutes": 0, "activity": "起床，把不会的词写在袖子上" },
  { "startMinute": 570, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，第一个到窗口" },
  { "startMinute": 630, "placeId": "place.bit.library", "travelMinutes": 20, "activity": "旁听语言学院的课，笔记写满两页" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，学会了点三道菜" },
  { "startMinute": 810, "placeId": "place.bit.campus_road", "travelMinutes": 0, "activity": "在主干道上练发音，路人回头她就改小声" },
  { "startMinute": 1020, "placeId": "place.bit.library", "travelMinutes": 15, "activity": "泡在借还处，看每个人怎么用中文" },
  { "startMinute": 1230, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "晚饭" },
  { "startMinute": 1320, "placeId": "place.bit.dorm_2", "travelMinutes": 20, "activity": "回宿舍，录一段今天听到的声音" }
]
```

### 23. 骆师傅 · npc.bit.luo_shifu

```json
[
  { "startMinute": 390, "placeId": "place.bit.bike_shed", "travelMinutes": 0, "activity": "出车前先把工具按大小排一遍" },
  { "startMinute": 420, "placeId": "place.bit.dorm_2", "travelMinutes": 20, "activity": "报修：四号楼水阀" },
  { "startMinute": 540, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "吃面，站着" },
  { "startMinute": 570, "placeId": "place.bit.lab_building", "travelMinutes": 20, "activity": "实验室换灯管，边换边讲原理" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.laundry", "travelMinutes": 15, "activity": "洗衣房，水管又漏" },
  { "startMinute": 960, "placeId": "place.bit.dorm_3", "travelMinutes": 20, "activity": "宿舍楼逐间查线路" },
  { "startMinute": 1230, "placeId": "place.bit.bike_shed", "travelMinutes": 25, "activity": "收工回车棚，搪瓷缸泡上茶" }
]
```

### 24. 姜小渔 · npc.bit.jiang_xiaoyu

```json
[
  { "startMinute": 300, "placeId": "place.bit.wetland_board", "travelMinutes": 0, "activity": "天没亮就位，等第一拨鸟" },
  { "startMinute": 480, "placeId": "place.bit.lake_wood", "travelMinutes": 15, "activity": "沿栈道记录，铅笔别在绳上防丢" },
  { "startMinute": 570, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，边吃边核对时间" },
  { "startMinute": 630, "placeId": "place.bit.wetland_board", "travelMinutes": 20, "activity": "继续蹲守，一动不动" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭" },
  { "startMinute": 810, "placeId": "place.bit.library", "travelMinutes": 25, "activity": "查鸟类图鉴" },
  { "startMinute": 1080, "placeId": "place.bit.wetland_board", "travelMinutes": 25, "activity": "黄昏那一班，钓到的鱼放回去" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_2", "travelMinutes": 25, "activity": "回宿舍整理记录本" }
]
```

### 25. 樟叔 · npc.bit.zhang_shu

```json
[
  { "startMinute": 450, "placeId": "place.bit.dorm_4", "travelMinutes": 0, "activity": "起床，烧水，泡搪瓷缸里的隔夜茶" },
  { "startMinute": 570, "placeId": "place.bit.bike_shed", "travelMinutes": 30, "activity": "推车出摊，修车兼补胎" },
  { "startMinute": 750, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "午饭，听人讲学校的事" },
  { "startMinute": 840, "placeId": "place.bit.bike_shed", "travelMinutes": 20, "activity": "回到摊子，对着老樟树发一会儿呆" },
  { "startMinute": 1080, "placeId": "place.bit.orchard", "travelMinutes": 30, "activity": "去果林看当年他种的那几棵" },
  { "startMinute": 1200, "placeId": "place.bit.bike_shed", "travelMinutes": 25, "activity": "收摊，把工具箱锁好" },
  { "startMinute": 1290, "placeId": "place.bit.dorm_4", "travelMinutes": 20, "activity": "回宿舍，路上跟树说话" }
]
```

### 26. 老陈 · npc.bit.chen_jiu

```json
[
  { "startMinute": 1320, "placeId": "place.bit.gym_basement", "travelMinutes": 0, "activity": "接班，检查地下室的灯" },
  { "startMinute": 1380, "placeId": "place.bit.gym", "travelMinutes": 15, "activity": "绕场馆一圈，锁门" },
  { "startMinute": 60, "placeId": "place.bit.gym_basement", "travelMinutes": 0, "activity": "坐在那儿，听楼上的动静" },
  { "startMinute": 70, "placeId": "place.bit.gym_basement", "travelMinutes": 0, "activity": "一点十分，锁门（两响半）" },
  { "startMinute": 90, "placeId": "place.bit.campus_road", "travelMinutes": 20, "activity": "在没人的路上走一圈" },
  { "startMinute": 180, "placeId": "place.bit.gym_basement", "travelMinutes": 20, "activity": "回地下室，天快亮时最安静" }
]
```

### 27. 三更 · npc.bit.san_geng

> 出现受条件限制（雨夜 / 23:00 后 / 玩家携带 ANOMALY 资源），因此作息只有"出现时段"。

```json
[
  { "startMinute": 0, "placeId": "place.bit.hall_roof", "travelMinutes": 0, "activity": "蹲在礼堂屋脊，抖羽毛" },
  { "startMinute": 60, "placeId": "place.bit.orchard", "travelMinutes": 25, "activity": "飞进果林，啄两口青苹果就走" },
  { "startMinute": 90, "placeId": "place.bit.library", "travelMinutes": 20, "activity": "落在图书馆窗沿，看里面的人" },
  { "startMinute": 300, "placeId": "place.bit.gym_basement", "travelMinutes": 20, "activity": "到地下室门口，啄两下铜环" },
  { "startMinute": 330, "placeId": "place.bit.gym_basement", "travelMinutes": 0, "activity": "（天亮前必须离开）" },
  { "startMinute": 1380, "placeId": "place.bit.hall_roof", "travelMinutes": 30, "activity": "雨来了，回屋脊" }
]
```

### 28. 沈墨 · npc.bit.shen_mo

> **特殊规则**：`rain_road` 是"任意正在下雨的路"，他只在下雨时存在。`startMinute` 表示"他在雨里等着的那个时间点"，`travelMinutes` 恒为 0（他不在两个地方之间移动，他就在你碰见他的那条路上）。

```json
[
  { "startMinute": 0, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "在雨里走，伞偏向别人那侧" },
  { "startMinute": 480, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "雨最大的时段，站在路中间也不躲" },
  { "startMinute": 720, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "在图书馆外的台阶下避一会儿" },
  { "startMinute": 900, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "在主干道上走，很慢" },
  { "startMinute": 1140, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "雨小下来的时候开始变得模糊" },
  { "startMinute": 1200, "placeId": "place.bit.rain_road", "travelMinutes": 0, "activity": "雨停即消失，不留任何掉落物" }
]
```

### 29. 夏星 · npc.bit.xia_xing

> 出现条件：晴天 + 玩家在照片相关地点。她"没有生活"，只有"在某个地方被想起"。

```json
[
  { "startMinute": 540, "placeId": "place.bit.old_ground", "travelMinutes": 0, "activity": "在停用的旧操场上，逆着光站着" },
  { "startMinute": 660, "placeId": "place.bit.photo_wall", "travelMinutes": 30, "activity": "在照片墙前，挨张看过去" },
  { "startMinute": 840, "placeId": "place.bit.history_museum", "travelMinutes": 10, "activity": "看 2008 年那张，手指停在第 8 个位置" },
  { "startMinute": 1020, "placeId": "place.bit.north_lake", "travelMinutes": 30, "activity": "回到湖边，坐在徐晚常坐的那张长椅上" },
  { "startMinute": 1200, "placeId": "place.bit.old_ground", "travelMinutes": 30, "activity": "太阳偏西，她开始变得不太清楚" },
  { "startMinute": 1290, "placeId": "place.bit.old_ground", "travelMinutes": 0, "activity": "日落前淡出" }
]
```

### 30. 娄七 · npc.bit.lou_qi

```json
[
  { "startMinute": 540, "placeId": "place.bit.lost_and_found", "travelMinutes": 0, "activity": "开门，把昨晚收进来的东西摆一遍" },
  { "startMinute": 660, "placeId": "place.bit.canteen_one", "travelMinutes": 20, "activity": "早饭，听人说丢了什么" },
  { "startMinute": 720, "placeId": "place.bit.lost_and_found", "travelMinutes": 20, "activity": "回房间，等人上门" },
  { "startMinute": 780, "placeId": "place.bit.gym", "travelMinutes": 20, "activity": "收体育馆的队服和哨子" },
  { "startMinute": 960, "placeId": "place.bit.lost_and_found", "travelMinutes": 20, "activity": "登记本上补记录" },
  { "startMinute": 1080, "placeId": "place.bit.laundry", "travelMinutes": 15, "activity": "去要一件没人认领的、已经洗干净的外套" },
  { "startMinute": 1170, "placeId": "place.bit.lost_and_found", "travelMinutes": 15, "activity": "回房间，泡茶" },
  { "startMinute": 1320, "placeId": "place.bit.lost_and_found", "travelMinutes": 0, "activity": "夜里不关灯——她说这样丢东西的人找得到路" }
]
```

---

## 十、待确认事项

1. **30 人是否够**？目前日常侧 24 人、异常侧 6 人。若后续想扩，建议按"每个工位至少 1 人"再补 6–10 人。
2. ~~是否需要 `giftPreferences`~~ → **本轮已加**，见第八节，liked 3–4 项、disliked 1–2 项，全部指向 `resources.json` 现有 470 项中的 id。
3. **头像与立绘是否要分两套风格**（如立绘改用更适合全身的三视图/白底），以便批量出图。
4. **前 5 人新名（林砚 / 周野 / 徐晚 / 何岸 / 齐岭）是否合意**？`id` 保持原样未变，只改了 `name`。若要换，直接说。
5. **地点占位表（第九节 7.1）需与地点数据对齐**：共 48 个 `placeId`，其中 9 个来自示例 `npcs.json`，39 个为本次占位。扩充地点数据时按此表对齐即可。
6. **跨零点作息的处理约定**（何岸、韦禾、老陈、徐晚四人跨日）：作息按"天环"解析（`NpcScheduleEntry` 要求 `startMinute` ∈ 0–1439），**不写 1440 的跨日标记条目**——当天最后一条到达后，次日从 0 分起的条目自然接上（同地点的停留会被环语义延续，例如夜班保安 22:00 到岗后一路待到次日清晨）。
7. **异常侧三人的作息逻辑特殊**，不是常规作息表：沈墨（只在雨里，`travelMinutes` 恒 0）、夏星（只在照片相关地点，日落淡出）、三更（受雨夜/ANOMALY 资源触发）。建议这三人不走 schedule 系统，另用条件触发。



