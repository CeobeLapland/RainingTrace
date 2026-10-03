# 校园版星露谷物语 · 地点表（Places）

> 配套 `npcs_design.md` / `resources_design.md` / `recipes_design.md`。本文字段命名与工作区示例 `places.json` 对齐。
> **本文仅为设计稿，暂不生成 json。经纬度（lat / lng）留空，由你自行填写。**
> 命名空间与人物一致，统一 `place.bit.*`。

---

## 一、字段与命名约定

| 字段 | 约定 | 是否进 json |
|------|------|------------|
| `id` | `place.bit.` + 英文小写下划线，英文取意象词，不用中文直译，跨条目不重名 | ✅ |
| `name` | 2–6 字，全表不重名 | ✅ |
| `type` | 地点类型，英文大写下划线（枚举见下） | ✅ |
| `lat` / `lng` | 经纬度，**本文留空待填** | ✅ |
| `actions` | 可在此地执行的动作（枚举见下），2–4 个 | ✅ |
| `description` | 一句话场景描述，≤ 40 字，写氛围不写功能 | ✅ |
| 分区 | A–J，**仅供策划查阅分区，不进 json** | ❌ |
| 关联人物 | 人物 id，**不进 json**，但建议程序侧建立反向索引 | ❌ |
| 关联资源 | 该地可产出的资源 id 前缀，**不进 json** | ❌ |
| 缩略图提示词 | 见第二节 | ❌ |

### 1.1 `type` 枚举（50 个）

初稿曾铺到 90 个，评审后按「功能相近者合并」收敛到 **50 个**。合并原则：**保留玩法差异，抹掉外观差异**——
判断两个 type 能不能合并，只看"玩家在这里能做的事是否相同"，不看"看起来像不像"。
例如石滩和湖心小岛外观差得远，但都是"走到水边、采集、钓鱼"，所以合并为 `SHORE`。

**沿用示例的 9 个**（`places.json` 已有，**不合并、不改名**）：
`LAKE` `LIBRARY` `CANTEEN` `PLAZA` `GARDEN` `DORM` `ORCHARD` `BERRY_BUSH` `MUSHROOM_PATCH`

**建筑与室内（19）**：

| type | 含义 | 由哪些原值合并而来 |
|------|------|--------------------|
| `CLASSROOM` | 教室 | 阶梯教室、空教室、多媒体教室 |
| `LAB` | 实验室 | — |
| `WORKSHOP` | 工作间 | 工坊 + **棚屋** + **储藏室** |
| `STUDIO` | 工作室 | 排练房、画室、暗房 |
| `MUSEUM` | 馆 | 馆 + **档案室**（校史馆与档案室玩法一致：观察 + 拾取） |
| `LOBBY` | 门厅 | 门厅 + **走廊** |
| `BASEMENT` | 地下室 | — |
| `ROOFTOP` | 屋顶 | 屋顶 + **天台** |
| `KITCHEN` | 后厨 | — |
| `LAUNDRY` | 洗涤房 | 洗衣房 + 水房 |
| `STUDY_ROOM` | 自习室 | — |
| `CLINIC` | 医疗点 | 校医务室 + **县医院** |
| `GYM` | 运动馆 | 体育馆、游泳馆、攀岩馆、乒乓室 |
| `STAGE` | 舞台 | 礼堂舞台、露天舞台 + **老影院**（都是"坐下看"） |
| `OFFICE` | 办公点 | 办公点 + **门房** + **旧楼 204** |
| `GATE` | 门 | 正门 |
| `STAIRWELL` | 楼梯间 | — |

**户外与自然（13）**：

| type | 含义 | 合并说明 |
|------|------|----------|
| `STREET` | 道路 | 街道 + 步行街 + 河堤 + 地下通道 |
| `PATH` | 小径 | 步道 |
| `PLAZA` | 广场 | 广场 + 老广场 |
| `FIELD` | 田地 | 田地 + 稻田 |
| `POND` | 池塘 | — |
| `WETLAND` | 湿地 | — |
| `SHORE` | 水边 | 石滩 + 湖心小岛 |
| `BRIDGE` | 桥 | — |
| `FOREST` | 林地 | 松林 + 竹林 |
| `HILL` | 山丘 | — |
| `GREENHOUSE` | 温室 | — |
| `CONSTRUCTION` | 工地 | 工地 + 旧厂 + 新厂 + 旧仓库 |
| `GARDEN` | 园子 | 花园 + 棋亭 |

**校外与城镇（6）**：`SHOP` 商店（含书店、供销社）/ `MARKET` 市集 / `CAFE` 茶饮点（咖啡馆 + 茶馆）/ `VILLAGE` 村落（村庄 + 镇子）/ `PARK` 城市公园 / `STATION` 车站（含地铁口）

**异常与不存在（7）**：
`ANOMALY_SPACE` 异常空间 / `EMPTY_ROOM` 空房间 / `DOOR` 单独的门 / `TUNNEL` 隧道 / `STAIRWAY_END` 楼梯尽头 / `BLANK_WALL` 无门之墙 / `TOWER` 塔

> 合计 9 + 19 + 13 + 6 + 7 = **54 个**（其中示例 9 个与自然 6 个在两处重复列出，去重后 **50 个**）。

### 1.2 `actions` 枚举


| 值 | 含义 | 备注 |
|----|------|------|
| `OBSERVE` | 观察 | 通用，几乎所有地点都有 |
| `COLLECT` | 采集 | 需要对应资源存在于该地 |
| `HARVEST` | 收获 | 果园/田/稻田专用 |
| `GATHER` | 拾取 | 落叶、传单、遗物等地面物 |
| `WATCH` | 观看/旁听 | 看人做事、听广播、旁观 |
| `TALK` | 交谈 | 有 NPC 在场时可用 |
| `REST` | 休息 | 座位类地点 |
| `EAT` | 用餐 | 食堂、面包房 |
| `WORK` | 干活 | 工坊、后厨、田地 |
| `STUDY` | 学习 | 图书馆、自习室 |
| `CREATE` | 制作 | 工位所在地点 |
| `ENTER` | 进入 | 建筑类 |
| `EXPLORE` | 探索 | 户外、边界地带 |
| `WATER` | 取水 | 井、河、湖 |
| `FISH` | 垂钓 | 水边 |
| `CAMP` | 露营 | 野外 |
| `NIGHT_ONLY` | 仅夜间 | 标注该地点夜间才可进入 |
| `RAIN_ONLY` | 仅雨中 | 标注该地点雨中才出现 |

> 组合原则：室内 2–3 个，户外 2–4 个，异常地点刻意只给 1–2 个（探索感）。
> `NIGHT_ONLY` / `RAIN_ONLY` 不是独立动作，是**限制标签**，写在 `actions` 里作为提示。

---

## 二、缩略图提示词约定

统一后缀（条目里只写差异部分，自动追加）：

`location thumbnail icon, semi-realistic anime style, single scene, elevated three-quarter view, soft ambient light, clean simple background, no text`

风格锚点：**二次元写实**（semi-realistic anime），略俯视的三分之四视角（类似星露谷的地图图标），不要纯赛璐璐、不要 3D 渲染、不要写实照片感。

**分区色调约定**（出图时保持同一分区色温一致）：

| 分区 | 色调倾向 |
|------|----------|
| A 校园主轴 | 明亮暖白，晴天 |
| B 教学科研 | 中性冷白，日光 |
| C 图书档案 | 昏黄暖光，安静 |
| D 宿舍生活 | 暖橘，夜间窗光 |
| E 餐饮后勤 | 暖黄，蒸汽感 |
| F 运动活动 | 高对比，明亮 |
| G 自然园林 | 高饱和绿，湿润 |
| H 旧址异常 | 冷青灰，低饱和 |
| I 校外城镇 | 暖土黄，市井 |
| J 异常空间 | 冷紫/单色，违和 |

---

## 三、数量与分区总览

| 分区 | 内容 | 数量 |
|------|------|------|
| A | 校园主轴与公共 | 13 |
| B | 教学与科研 | 15 |
| C | 图书、档案与旧址 | 12 |
| D | 宿舍与生活区 | 13 |
| E | 餐饮、后勤与医务 | 9 |
| F | 运动、社团与活动 | 11 |
| G | 自然、园林与野外 | 19 |
| H | 旧址、边缘与过渡地带 | 10 |
| I | 校外城镇 | 18 |
| J | 异常与不存在的空间 | 10 |
| **小计（A–J）** | | **130** |
| 5.3 增补 | 纯场景点（无人物绑定） | 9 |
| **合计** | | **139** |

> 示例 `places.json` 的 9 个 id 已全部收录并对齐原 id，其中 `berry_bush` / `mushroom_patch` / `orchard` 归入 G 区。
> `type` 枚举已由初稿 90 个收敛为 **50 个**（见 1.1），139 处地点共用。

---

## 四、分区详设

### A. 校园主轴与公共（13）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.plaza` | 中心广场 | `PLAZA` | OBSERVE / GATHER / TALK | 开阔的广场，周末偶尔有社团活动，角落常落下几张传单。 | 周野、高见 | `res.plaza_flyer` `res.paper_plane` | `wide stone campus plaza, radial paving pattern, banner poles, few people, fallen flyers on ground, bright daylight` |
| `place.bit.campus_road` | 校园主干道 | `STREET` | OBSERVE / WALK / TALK | 从校门直通图书馆的梧桐道，骑车的人比走路的多。 | 沈砚、顾青 | `res.plane_leaf` `res.dandelion_fluff` | `tree-lined campus road, tall plane trees arching overhead, bicycle lane, dappled light, late afternoon` |
| `place.bit.gate_north` | 北门门房 | `OFFICE` | OBSERVE / TALK | 夜里唯一亮着的小窗，保温杯永远放在窗台上。 | 韦禾 | `res.thermos` `res.pager_note` | `small campus gatehouse at night, lit window, thermos on the sill, dark road outside, warm interior glow` |
| `place.bit.gate_main` | 正门 | `GATE` | OBSERVE / TALK | 两根旧石柱，中间挂着校牌，字被摸得发亮。 | 高见、骆师傅 | — | `old stone campus entrance gate, two weathered pillars, school sign, iron railings, daytime` |
| `place.bit.notice_board` | 公告栏 | `STREET` | OBSERVE / GATHER | 纸层压着纸层，最下面那张已经不是这个学期的了。 | 高见 | `res.club_flyer` `res.torn_letter` | `old outdoor notice board layered with many overlapping paper flyers, curling edges, torn corners, daylight` |
| `place.bit.lecture_hall` | 礼堂 | `STAGE` | OBSERVE / WATCH / REST | 舞台灯坏了一半，但每次活动都还是在这里办。 | 高见、罗小满 | `res.folding_chair` `res.paper_sculpture` | `old school auditorium interior, half-broken stage lights, rows of folding seats, red curtain, dim warm light` |
| `place.bit.club_center` | 学生活动中心 | `LOBBY` | OBSERVE / TALK / REST | 一楼是信箱和打印机，二楼往上全是社团。 | 高见、苏黎、温宁 | `res.notice_scrap` `res.paper_sheet` | `student activity center lobby, wall of pigeonholes and flyers, bulletin notices, staircase going up, daytime` |
| `place.bit.bike_shed` | 校门车棚 | `WORKSHOP` | OBSERVE / TALK / WORK | 樟叔的摊子，长凳上摊着一排修到一半的车。 | 樟叔、骆师傅 | `res.rubber_scrap` `res.cable` | `open bicycle repair shed, half-repaired bikes on a bench, tools laid out, old enamel mug, dappled shade` |
| `place.bit.pontoon` | 荷花池 | `POND` | OBSERVE / COLLECT / FISH | 池子不深，但荷叶夏天能铺满整片。 | 徐晚、姜小渔 | `res.lotus_leaf` `res.egg` | `small lotus pond, broad green lily pads covering water, a few buds, dragonfly hovering, summer` |
| `place.bit.campus_pond_bridge` | 小石桥 | `BRIDGE` | OBSERVE / REST | 桥面很窄，铺的石头被踩得发亮，只能过一个人。 | 徐晚 | `res.moss_ball` | `narrow small stone bridge over a pond, mossy edges, worn smooth stone, water reflection, quiet` |
| `place.bit.rain_road` | 雨中的路 | `STREET` | OBSERVE / TALK | 下雨时才有的那条路。伞都偏向同一边。 | 沈墨 | `res.reversed_rain` `res.rain_droplet` | `rain-soaked campus road in heavy rain, wet asphalt reflecting grey sky, one black umbrella tilted away, blurred figures` |
| `place.bit.paved_walk` | 砖砌小径 | `STREET` | OBSERVE / GATHER | 从宿舍到食堂最近的一条路，两边种了月季。 | 周野、米娅 | `res.petal` `res.dew_grass` | `red brick walkway lined with monthly roses, morning dew, petals on path, soft light` |
| `place.bit.underpass` | 教学楼连廊 | `LOBBY` | OBSERVE / TALK | 两栋楼之间的一条长廊，晴天晒得到、雨天也躲得开。 | 苏黎、江予 | `res.lost_and_found_record` | `covered corridor connecting two school buildings, long perspective, columns, hanging light, wet floor after rain` |

### B. 教学与科研（15）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.lab_building` | 实验楼 | `LAB` | ENTER / OBSERVE / TALK | 走廊里有一股焊锡味，消防柜玻璃碎了一块没人报。 | 沈砚、骆师傅 | `res.copper_wire` `res.alu_scrap` | `school science laboratory building exterior, concrete facade, long corridor entrance, daylight, quiet` |
| `place.bit.lab_415` | 四一五实验室 | `LAB` | ENTER / WORK / CREATE | 沈砚的工位，桌上零件按大小排成一列。 | 沈砚 | `res.solder_iron` `res.circuit_board` `res.gear_small` | `cluttered electronics workbench, tools arranged by size, oscilloscope, soldering station, focused task lamp` |
| `place.bit.lab_basement` | 地下低温室 | `BASEMENT` | ENTER / OBSERVE / WORK | 常年 −196 度，门上结着霜，开门像进霾天。 | 欧阳让 | `res.ice_crystal` `res.mica_flake` | `underground cryogenic laboratory, frost-covered door, cold vapor spilling out, dim blue-white light, warning signs` |
| `place.bit.classroom_a` | 阶梯教室 | `CLASSROOM` | ENTER / STUDY / OBSERVE | 座位是活动的，谁抢到前排谁就得负责回答问题。 | 廖一、江予 | `res.exam_notes` `res.blackboard_photo` | `tiered lecture hall classroom, movable seats, big blackboard with chalk writing, daylight from high windows` |
| `place.bit.classroom_b` | 空教室 | `CLASSROOM` | ENTER / OBSERVE / GATHER | 期末考完就空了，桌上还留着没撕下来的笔记纸。 | 苏黎 | `res.timetable_scrawl` `res.paper_sheet` | `empty classroom after exams, scattered note paper on desks, chalk dust, quiet afternoon light through blinds` |
| `place.bit.moot_court` | 模拟法庭 | `CLASSROOM` | ENTER / WATCH / TALK | 圆桌、法槌、还有一面永远挂着的天平。 | 江予 | `res.transcript` `res.blackboard_photo` | `moot court room, round debate table, wooden gavel, scales of justice emblem, formal wood paneling` |
| `place.bit.art_room` | 艺术楼画室 | `STUDIO` | ENTER / CREATE / REST | 画架比人多，油画味混着松节油。 | 徐晚 | `res.paint_brush` `res.palette` `res.pigment` | `art studio room, many easels, canvases stacked, paint-splattered floor, north-facing windows, soft grey light` |
| `place.bit.art_terrace` | 艺术楼天台 | `ROOFTOP` | OBSERVE / REST | 栏杆矮得能坐上去，晾满了没干的画。 | 徐晚、白依 | `res.paper_sheet` | `art building rooftop terrace, low railing covered with drying paintings, city skyline beyond, string lights` |
| `place.bit.music_room` | 音乐教室 | `STUDIO` | ENTER / WATCH / CREATE | 角落堆着一整套打击乐，只有人在用。 | 罗小满 | `res.drum_stick` `res.music_score_fragment` | `school music classroom, scattered percussion instruments, upright piano in corner, music stands, warm lamp light` |
| `place.bit.sewing_room` | 缝纫社 | `WORKSHOP` | ENTER / WORK / CREATE | 布堆到天花板，墙上钉着历届社员的尺寸表。 | 白依 | `res.linen_thread` `res.dyed_fabric` `res.needle` | `sewing club room, fabric bolts stacked to ceiling, old sewing machine, measuring tape on wall, dense and warm` |
| `place.bit.workshop` | 机工房 | `WORKSHOP` | ENTER / WORK / CREATE | 电动工具一排排挂着，用完必须归位。 | 沈砚、骆师傅 | `res.hand_hammer` `res.solder_bar` `res.plank` | `school machine workshop wall, hand tools hung in neat rows, drill press, sawdust, fluorescent lighting` |
| `place.bit.lab_animal` | 饲养角 | `STUDIO` | OBSERVE / WORK | 笼子里是实验用的兔子，喂食要签字。 | 陈秋 | `res.farm_cabbage` `res.hay` | `small animal husbandry corner, wire cages with rabbits, feed tray, handwritten sign sheet, clean concrete floor` |
| `place.bit.greenhouse` | 生物温室 | `GREENHOUSE` | ENTER / OBSERVE / WORK | 玻璃顶常年滴水，白天闷得像蒸笼。 | 蒋成一、姜小渔 | `res.seedling` `res.watered_soil` | `humid greenhouse interior, glass roof with dripping condensation, rows of seedling trays, misty green light` |
| `place.bit.lecture_hall_tech` | 多媒体教室 | `CLASSROOM` | ENTER / STUDY / WATCH | 投影仪只认一种接口，所以大家都不带电脑来。 | 廖一 | `res.exam_notes` `res.cable` | `modern multimedia classroom, projector screen, dark projector, half-tilted whiteboard, dimmed lights` |
| `place.bit.roof_lab` | 实验楼天台 | `ROOFTOP` | OBSERVE / REST | 门锁坏了三年，一直没人报修。 | 欧阳让 | `res.solar_panel` `res.rain_gauge` | `science building rooftop, rusted water tank, railed edge, distant city, laundry lines from below, sunset` |

### C. 图书、档案与旧址（12）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.library` | 图书馆 | `LIBRARY` | ENTER / STUDY / OBSERVE | 安静的大楼，午后阳光斜落中庭，书架间偶尔能翻出旧书目卡。 | 林砚、苏黎、米娅 | `res.borrow_stub` `res.bookmark` | `grand school library reading hall, tall shelves, sunlight slanting into atrium, green reading lamps` |
| `place.bit.library_four` | 四楼自习区 | `STUDY_ROOM` | ENTER / STUDY / REST | 最靠里的座位常年没人抢，因为太偏。 | 苏黎、江予 | `res.reading_note` `res.english_word_sheet` | `quiet fourth-floor study area, desks in rows, warm desk lamps, large window with dusk light, empty seats` |
| `place.bit.study_room` | 自习室 | `STUDY_ROOM` | ENTER / STUDY / REST | 第三个座位被刻了字，刻得很深，搬了三次宿舍还在。 | 廖一、江予 | `res.math_formula_note` `res.seat_carving` | `campus study room, long shared desks, desk lamps, scattered notebooks, one carved seat, quiet night` |
| `place.bit.stacks` | 旧馆书架通道 | `MUSEUM` | ENTER / OBSERVE / GATHER | 灯要拧很久才亮，照出一层浮灰。 | 何岸 | `res.shelf_card` `res.catalogue_index` | `narrow old archive aisle between towering shelves, dim single bulb, dust motes in light, ladders on rails` |
| `place.bit.darkroom` | 暗房 | `MUSEUM` | ENTER / WORK / CREATE | 红灯下什么都是红 的，晾片子上夹着一百年的等待。 | 苗小蝶 | `res.film_roll` `res.photo_paper` `res.developer` | `photography darkroom, red safelight, trays of developer liquid, film drying on wires, deep shadows` |
| `place.bit.history_museum` | 校史馆 | `MUSEUM` | ENTER / OBSERVE / GATHER | 玻璃柜里是历届的旧物，最早那件展柜灯坏了。 | 陈秋 | `res.old_photo` `res.yearbook_page` | `school history museum interior, glass display cases with old artifacts, framed photos on wall, dim warm spotlights` |
| `place.bit.photo_wall` | 照片墙 | `MUSEUM` | ENTER / OBSERVE | 毕业照一张挨一张挂到天花板，2008 年那张在第三排。 | 陈秋、夏星、苗小蝶 | `res.group_photo` `res.founding_photo` | `large wall covered edge-to-edge with old black-and-white school photographs, uneven frames, dim gallery light` |
| `place.bit.lost_and_found` | 失物招领处 | `OFFICE` | ENTER / TALK / GATHER | 一屋子没人认领的东西，登记本上有空白签名。 | 娄七 | `res.lost_and_found_record` `res.key` | `cluttered lost-and-found office, shelves of mismatched objects, ledger open on desk, single warm lamp` |
| `place.bit.radio_studio` | 广播站 | `STUDIO` | ENTER / WATCH / CREATE | 隔音棉贴满一面墙，音量旋钮拧到 3 就够了。 | 顾青 | `res.radio_log` `res.music_score_fragment` | `small campus radio studio, mixing console, boom microphone, soundproof foam wall, glowing ON AIR sign` |
| `place.bit.archive_room` | 档案室 | `MUSEUM` | ENTER / OBSERVE / GATHER | 借阅要签字，签字要理由，理由要写满一行。 | 陈秋 | `res.archive_letter` `res.history_transcript` | `school archive room, tall document shelves, card catalog cabinets, single desk with lamp, dusty air` |
| `place.bit.library_roof` | 图书馆天台 | `ROOFTOP` | ENTER / OBSERVE / REST | 上面其实不上锁，只是没人上来。 | 林砚、顾青 | `res.star_chart` | `library rooftop, sloped tiles, view over campus treetops, water tank, night sky with a few stars` |
| `place.bit.old_library_annex` | 旧馆附楼 | `MUSEUM` | ENTER / EXPLORE | 附楼只有一层，但里面比主楼深。 | 何岸 | `res.duplicate_room` | `narrow single-storey annex building, one lit window, brick wall, deep doorway, dusk` |

### D. 宿舍与生活区（13）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.dorm_1` | 宿舍一号楼 | `DORM` | ENTER / REST / GATHER | 门厅堆着自行车，楼道里晾满了衣服。 | 苏黎、蒋成一 | `res.dorm_charm` `res.tube_paint` | `dormitory building entrance, bicycles parked in the hall, laundry hanging on balcony rails, evening` |
| `place.bit.dorm_2` | 宿舍二号楼 | `DORM` | ENTER / REST / GATHER | 楼下永远有人在喊谁的名字。 | 徐晚、白依、唐糖 | `res.dorm_scrap` | `second dormitory block, lit windows, bicycle racks, warm lamps, evening blue hour` |
| `place.bit.dorm_3` | 宿舍三号楼 | `DORM` | ENTER / REST / GATHER | 晚上亮着许多窗，是回来的地方。桌边总贴着写到一半的便签。 | 林砚、沈砚、廖一 | `res.memo_slip` `res.timetable_scrawl` | `third dormitory block at night, many lit windows, entrance light, bicycle parked, quiet night` |
| `place.bit.dorm_4` | 宿舍四号楼 | `DORM` | ENTER / REST / GATHER | 这栋的窗帘全是同一块布，楼下总有人练琴。 | 罗小满、温宁 | `res.lyric_scratch` | `fourth dormitory block, uniform curtains, warm light spilling out, bicycle leaning, evening` |
| `place.bit.dorm_roof` | 宿舍天台 | `ROOFTOP` | ENTER / OBSERVE / REST | 晾衣绳横过整个天台，晾出去的被单像帆。 | 米娅、温宁 | `res.stitch_scrap` | `dormitory rooftop laundry terrace, sheets and clotheslines strung across, water tank, low city glow at night` |
| `place.bit.washing_room` | 洗衣房 | `LAUNDRY` | ENTER / WORK / WATER | 六台机器永远有两台在坏，肥皂味很重。 | 白依、韦禾、娄七 | `res.soap` `res.towel` | `shared laundry room, old washing machines, plastic laundry baskets, soap suds on floor, dim light` |
| `place.bit.laundry` | 宿舍洗衣房 | `LAUNDRY` | ENTER / WORK / WATER | 骆师傅最常来的地方，墙角堆着他换下的水管。 | 骆师傅、娄七 | `res.steel_ingot` `res.water_pipe` | `basement-style laundry corner, exposed pipes, stacked spare parts, dryer drums, fluorescent light` |
| `place.bit.dorm_canteen` | 宿舍小卖部 | `SHOP` | ENTER / TALK | 夜里九点关门，老板会多留一盒牛奶。 | 米娅、唐糖 | `res.instant_noodle` `res.pudding_cup` | `tiny dormitory kiosk, shelves of snacks and drinks, warm counter light, night outside window` |
| `place.bit.clinic` | 校医务室 | `CLINIC` | ENTER / REST | 校医永远在听广播，血压计的袖带发黄了。 | 韦禾、米娅 | `res.remedy_powder` `res.bandaid` | `small school infirmary, single bed, medicine cabinet, wall-mounted radio, cold white light` |
| `place.bit.room_204` | 旧楼 204 | `OFFICE` | ENTER / TALK / REST | 门牌号换过一次，屋里还是两张椅子一张桌。 | 温宁、娄七 | `res.cloth_ribbon` | `tiny bare room with two chairs and a small table, window with soft light, quiet and plain` |
| `place.bit.dorm_attic` | 顶楼储物间 | `MUSEUM` | ENTER / EXPLORE / GATHER | 只有一扇小窗，堆着历年丢的旧物。 | 娄七 | `res.obsolete_gear` `res.key` | `dusty attic storage room, single small window, broken furniture and boxes stacked, shafts of light and dust` |
| `place.bit.dorm_courtyard` | 宿舍小院 | `PLAZA` | OBSERVE / REST | 院里有一棵比楼还老的树，树下一圈石凳。 | 徐晚、米娅 | `res.ginkgo_leaf` | `small dormitory courtyard, very old broad tree, stone bench in a circle, dappled shade, quiet` |
| `place.bit.dorm_stair` | 楼道尽头 | `STAIRWELL` | ENTER / OBSERVE | 转角平台的灯是声控的，但这次它自己亮了。 | 何岸、韦禾 | `res.extra_lamp` | `dim stairwell landing, flickering sensor light, peeling paint on wall, metal handrail, unsettling stillness` |

### E. 餐饮、后勤与医务（9）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.canteen_one` | 食堂一 | `CANTEEN` | ENTER / EAT / TALK / GATHER | 饭点前总飘着饭菜香，窗口前排着长长的队。窗口边挂着今日的菜签。 | 周野、唐糖、米娅 | `res.canteen_bowl` `res.steamed_bun` `res.pickled_jar` | `busy school canteen interior, serving windows with queue, steam rising, menu cards on wall, warm fluorescent` |
| `place.bit.dining_line` | 打饭队列 | `CANTEEN` | OBSERVE / TALK | 队伍自己会动。前面的人打完，后面的人往前一步。 | 周野、高见 | `res.menu_ticket` | `long queue of students at a canteen serving counter, trays in hands, steam, overhead menu board` |
| `place.bit.canteen_kitchen` | 食堂后厨 | `KITCHEN` | ENTER / WORK / CREATE | 油很响，锅比人还大，罗小满常来借锅。 | 唐糖、周野、罗小满 | `res.flour` `res.syrup` `res.jam` | `canteen back kitchen, large industrial pots, steam and heat, metal counters, cook working in background` |
| `place.bit.canteen_two` | 食堂二 | `CANTEEN` | ENTER / EAT | 二食堂便宜三毛，但要多走十分钟。 | 廖一、骆师傅 | `res.noodle_bowl` `res.tofu` | `smaller second canteen, simpler metal tables, warm light, half-full at midday, plain` |
| `place.bit.bakery` | 面包房 | `SHOP` | ENTER / EAT / GATHER | 早上六点出炉，糖霜会撒到围裙上。 | 唐糖、米娅 | `res.campus_bread` `res.candy_grain` | `small campus bakery, trays of bread and pastries, display case, flour dust in sunlight, morning` |
| `place.bit.pantry` | 后厨储藏间 | `WORKSHOP` | ENTER / GATHER | 米面堆到房梁，门后藏着一箱不登记的调料。 | 唐糖 | `res.jam` `res.vinegar` | `pantry storage room, sacks of rice and flour stacked to ceiling, dim bulb, jars on shelves, shadowy` |
| `place.bit.water_room` | 水房 | `LAUNDRY` | ENTER / WATER | 二十四小时热水，但深夜那会儿水声特别大。 | 米娅、骆师傅 | `res.fresh_water` | `shared washroom with water heater and basins, tiled walls, folding mirror, early morning light` |
| `place.bit.veggie_pantry` | 菜窖 | `BASEMENT` | ENTER / GATHER | 冬天的菜在这里能放到三月，洋葱一层一层码着。 | 周野、齐岭 | `res.potato` `res.radish` | `underground vegetable cellar, stone steps down, stacked crates of onions and potatoes, cold earthy air, single bulb` |
| `place.bit.power_room` | 配电间 | `WORKSHOP` | ENTER / OBSERVE | 铁门上挂着"非工作人员勿入"，锁是新的。 | 骆师傅、沈砚 | `res.copper_ore` `res.iron_ingot` | `electrical power room, heavy grey metal door with new padlock, warning signs, dim corridor light` |

### F. 运动、社团与活动（11）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.gym` | 体育馆 | `GYM` | ENTER / WATCH / WORK | 队训到很晚，只剩阿武一个人在擦篮板。 | 阿武、娄七 | `res.referee_whistle` | `school gymnasium interior, basketball court with worn floor, high windows, single light on, evening` |
| `place.bit.playground` | 操场看台 | `STAGE` | ENTER / WATCH / REST | 台阶上永远坐着几个人，谁都不认识谁。 | 罗小满、姜小渔 | `res.sticker_sheet` | `outdoor running track bleachers, a few students sitting on concrete steps, evening field, scattered stickers` |
| `place.bit.court` | 篮球场 | `GYM` | ENTER / WATCH / WORK | 白天打球，晚上也打球，只是换了一拨人。 | 阿武、高见 | `res.ball` `res.wrist_band` | `outdoor basketball court, chain-link fence, worn backboard, dusk light, few players` |
| `place.bit.tennis_court` | 网球场 | `STATION` | ENTER / WATCH | 很久没人用，网松了，线也断了。 | 江予 | `res.timing_paper` | `abandoned tennis court, sagging net, faded line markings, overgrown edges, quiet afternoon` |
| `place.bit.running_track` | 塑胶跑道 | `STREET` | OBSERVE / TALK | 周野每天跑两圈，一圈 400 米。 | 周野、米娅 | `res.wrist_band` | `red rubber running track around a field, white lane lines, morning haze, empty` |
| `place.bit.swimming_pool` | 游泳馆 | `GYM` | ENTER / WATCH | 池底有一块补丁，补了三次都没对。 | 骆师傅、蒋成一 | `res.cloth_swim_cap` | `indoor swimming pool, empty lanes, tiled pool edge, overhead lights reflecting on still water, echo` |
| `place.bit.table_tennis` | 乒乓球室 | `STUDIO` | ENTER / WATCH | 球桌上永远有人放一支球拍当占位。 | 陈秋、廖一 | `res.paddle` | `table tennis room, green table with net, paddles left on surface, bare walls, ceiling light` |
| `place.bit.chess_pavilion` | 棋亭 | `GARDEN` | ENTER / WATCH / REST | 石桌上刻满了棋盘，谁都能下一盘。 | 陈秋、高见 | `res.go_stone` `res.chess_piece` | `open stone pavilion with carved chess board table, old men playing, trees around, soft afternoon shade` |
| `place.bit.band_room` | 乐队排练室 | `STUDIO` | ENTER / WATCH / CREATE | 隔音不好，整层楼都知道他们在排什么。 | 罗小满 | `res.tambourine` `res.harmonica` | `band rehearsal room, instruments stacked, soundproof panels, music stands, amps, chaotic energy` |
| `place.bit.climbing_gym` | 攀岩馆 | `GYM` | ENTER / WATCH | 墙很高，垫子很旧，安全绳磨得发白。 | 阿武、姜小渔 | `res.rope` `res.climbing_shoe` | `indoor climbing gym, tall textured wall, worn safety mats, coiled ropes, high ceiling light` |
| `place.bit.school_stage` | 露天舞台 | `STAGE` | ENTER / WATCH / PERFORM | 每年迎新都在这儿，音响有一只耳朵是哑的。 | 高见、罗小满、蒋成一 | `res.stage_light` | `outdoor school stage, mismatched speakers, faded banners, string lights, evening crowd blur` |

### G. 自然、园林与野外（19）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.north_lake` | 北湖 | `LAKE` | OBSERVE / COLLECT / FISH / WATER | 校区中轴最北侧的一潭水。岸边有芦苇，雨后的傍晚常有麻雀贴着水面掠过。 | 徐晚、姜小渔 | `res.reed_leaf` `res.fresh_water` | `calm campus lake, reed beds along the bank, overcast dusk, birds skimming the water surface, reflections` |
| `place.bit.lake_wood` | 湖边木栈道 | `BRIDGE` | OBSERVE / REST / FISH | 木板被踩得发亮，走到头要原路返回。 | 徐晚、姜小渔 | `res.drift_wood` | `wooden boardwalk over lake, weathered planks, railings with peeling paint, mist over water` |
| `place.bit.lake_center` | 湖心小岛 | `SHORE` | ENTER / EXPLORE / GATHER | 平时过不来，只在枯水期能走一段。 | 徐晚、齐岭 | `res.wild_mint` `res.egg` | `small island in a lake, wild grass, one bare tree, wooden plank path, calm water around` |
| `place.bit.wetland_board` | 湿地观测点 | `WETLAND` | OBSERVE / EXPLORE / GATHER | 木平台伸进芦苇丛，脚下是软的，鸟比人多。 | 姜小渔、齐岭 | `res.cattail` `res.water_algae` | `wooden observation platform in a reed wetland, muddy water below, birds in distance, dawn mist` |
| `place.bit.reed_bank` | 芦苇滩 | `WETLAND` | EXPLORE / GATHER | 芦苇比人高，进去就看不见路了。 | 齐岭、蒋成一 | `res.reed_leaf` `res.plant_fiber` | `dense reed bank, tall reeds blocking the view, narrow muddy path, autumn light` |
| `place.bit.garden` | 湖心花园 | `GARDEN` | ENTER / OBSERVE / GATHER | 一条小径绕着几棵老树。苔藓、松果与花瓣随季节换着铺在地上。 | 温宁、樟叔、米娅 | `res.petal` `res.moss_ball` `res.daisy_bud` | `winding garden path around old trees, moss and fallen petals on ground, flower bed border, soft green shade` |
| `place.bit.orchard` | 果林 | `ORCHARD` | ENTER / HARVEST / GATHER | 花园北侧的一小片果木，秋天枝头最沉。 | 齐岭、蒋成一、樟叔 | `res.green_apple` `res.tree_sap` | `small orchard with fruit trees, hanging green apples, grass underfoot, autumn afternoon light` |
| `place.bit.berry_bush` | 浆果丛 | `BERRY_BUSH` | ENTER / GATHER / HARVEST | 小径旁的一丛灌木，走过去顺手就能摘几颗。 | 蒋成一、齐岭 | `res.wild_berry` | `wild berry bush along a path, ripe berries on branches, dappled sunlight, hand almost picking` |
| `place.bit.mushroom_patch` | 菌丛 | `MUSHROOM_PATCH` | ENTER / GATHER | 老树根边常冒菌子，下过雨那几天最好找。 | 齐岭、蒋成一 | `res.wild_mushroom` | `cluster of wild mushrooms at the base of an old tree root, mossy ground, after rain` |
| `place.bit.leaf_lane` | 落叶小径 | `STREET` | OBSERVE / GATHER | 落叶厚到能没过脚踝，踩上去很响。 | 米娅、林砚 | `res.maple_leaf` `res.ginkgo_leaf` | `leaf-covered lane under autumn trees, thick fallen leaves, golden light filtering through, quiet` |
| `place.bit.greenhouse_yard` | 温室前院 | `GARDEN` | OBSERVE / WORK / GATHER | 苗盆摆了一地，浇水壶从来没人还。 | 蒋成一 | `res.farm_cabbage` | `yard in front of greenhouse, many plant pots and seed trays, watering can left out, morning sun` |
| `place.bit.field_plot` | 菜圃 | `FIELD` | ENTER / HARVEST / WORK | 一人一块地，界线自己划，谁也不越界。 | 蒋成一、齐岭 | `res.potato` `res.shepherd_purse` | `small community vegetable plots separated by bamboo fences, neat rows of greens, hoe leaning on edge` |
| `place.bit.rice_paddy` | 稻田 | `FIELD` | OBSERVE / HARVEST | 水田里养着鱼，收稻时鱼也一起收。 | 齐岭、骆师傅 | `res.rice_ear` `res.crucian_carp` | `flooded rice paddy reflecting sky, young rice seedlings in rows, wooden water gate, evening` |
| `place.bit.wild_field` | 野草地 | `FIELD` | EXPLORE / GATHER | 没人管的一片草，长到小腿，风一吹全是声音。 | 姜小渔、温宁 | `res.dandelion_fluff` `res.wild_mint` | `untended wild grass field, waist-high grass rippling in wind, dandelions, late afternoon sun` |
| `place.bit.pine_forest` | 松林 | `FOREST` | ENTER / EXPLORE / GATHER | 地上全是松针，踩上去很软。 | 齐岭、姜小渔 | `res.pine_cone` `res.pine_needle` | `open pine forest, tall straight trunks, carpet of brown needles, shafts of light, still air` |
| `place.bit.bamboo_grove` | 竹林 | `FOREST` | ENTER / EXPLORE / REST | 风穿过竹子的声音比风本身大。 | 姜小渔、骆师傅 | `res.bamboo` `res.bamboo_shoot` | `bamboo grove, tall green stalks close together, wind swaying, dappled light, path` |
| `place.bit.rocky_shore` | 石滩 | `SHORE` | EXPLORE / GATHER / WATER | 水退的时候石头缝里能翻出小东西。 | 姜小渔、齐岭 | `res.freshwater_shell` `res.seaweed` | `rocky shoreline, wet flat stones, shallow tide pools, distant water, overcast morning` |
| `place.bit.camp_field` | 露营草地 | `FIELD` | CAMP / REST / GATHER | 远处山上的那片空地，夜里能看见很多星。 | 姜小渔、蒋成一 | `res.tent` `res.campfire_ash` | `grassy clearing on a hillside, small tent pitched, campfire remains, starry night sky above mountains` |
| `place.bit.hill_grass` | 后山草坡 | `HILL` | ENTER / EXPLORE / REST | 爬到顶能看见整个学校，山下全是灯。 | 欧阳让、米娅 | `res.wild_chrysanthemum` | `grassy hill slope above campus, city lights and school buildings far below, dusk panorama, wildflowers` |

### H. 旧址、边缘与过渡地带（10）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.old_ground` | 旧操场 | `PLAZA` | EXPLORE / OBSERVE / GATHER | 跑道线还在，杂草从裂缝里长出来，已经很多年没人用。 | 夏星、苗小蝶 | `res.cracked_tracks` `res.dandelion_fluff` | `abandoned old sports ground, faded track lines, weeds growing through cracks, dusk, empty` |
| `place.bit.gym_basement` | 旧体育馆地下室 | `BASEMENT` | ENTER / EXPLORE / GATHER | 一年四季都凉，墙上有很深的旧划痕。 | 老陈、娄七 | `res.anomaly_key` `res.parallel_ladder` | `underground gym basement, cold concrete, deep scratches on wall, single bare bulb, long shadows` |
| `place.bit.hall_roof` | 礼堂屋脊 | `ROOFTOP` | ENTER / EXPLORE | 屋脊很窄，从来没规定过不许上去。 | 三更、罗小满 | `res.crow_feather` | `narrow tiled roof ridge of an old auditorium, ridge tiles uneven, night sky, city glow below` |
| `place.bit.abandoned_dorm` | 空楼 | `DORM` | ENTER / EXPLORE / GATHER | 整栋楼都空着，钥匙孔里塞满了纸。 | 何岸、娄七 | `res.dorm_scrap` `res.paper_sheet` | `abandoned dormitory building interior, dusty floor, peeling walls, boarded windows, shafts of grey light` |
| `place.bit.abandoned_garden` | 荒废园子 | `GARDEN` | ENTER / EXPLORE / GATHER | 园门锁着，但从豁口能进，里面比外面看起来大。 | 樟叔、三更 | `res.unnamed_flower` | `overgrown walled garden, rusted gate with a gap, tangled vines, crumbling brick, afternoon light` |
| `place.bit.back_alley` | 后巷 | `STREET` | ENTER / EXPLORE / GATHER | 两栋楼之间的窄巷，垃圾桶后面有人在弹吉他。 | 罗小满、骆师傅 | `res.music_sheet` | `narrow back alley between buildings, bins, faint music from somewhere, laundry overhead, night` |
| `place.bit.overgrown_path` | 荒径 | `PATH` | ENTER / EXPLORE / GATHER | 路牌还在，字被藤蔓吃掉了两个。 | 齐岭、蒋成一 | `res.gold_axe` | `overgrown woodland path, crumbling trail sign, vines covering the route, dense green, quiet` |
| `place.bit.river_bridge` | 河上旧桥 | `BRIDGE` | ENTER / EXPLORE / FISH | 桥栏上的花被摸没了，桥下有人在钓鱼。 | 骆师傅、姜小渔 | `res.crow_feather` | `old stone bridge over a river, worn railing, fishing rod hanging below, misty morning` |
| `place.bit.closed_storeroom` | 封库 | `MUSEUM` | ENTER / EXPLORE / GATHER | 门封条是十年前的，日期还能看清。 | 娄七、欧阳让 | `res.archive_letter` | `sealed storage room, old tape across the door with a date, dusty lock, dim corridor` |
| `place.bit.unfinished_floor` | 未完工楼层 | `CONSTRUCTION` | ENTER / EXPLORE / GATHER | 钢筋伸到楼外，风一吹整层都在响。 | 沈砚、欧阳让 | `res.scaffold_clamp` `res.concrete_block` | `unfinished concrete building floor, exposed rebar reaching out, scaffolding, windy open sides, overcast` |

### I. 校外城镇（18）

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.old_street` | 老街 | `STREET` | ENTER / OBSERVE / TALK / GATHER | 青石板被磨得发亮，两边是修表铺和一家开了三十年的面馆。 | 罗小满、米娅 | `res.worn_coin` | `old cobblestone street, small watch repair shop and a noodle shop, weathered eaves, lanterns, evening` |
| `place.bit.noodle_shop` | 面馆 | `CAFE` | ENTER / EAT / TALK | 老板从不问你要什么端上来，热汤面三块钱。 | 周野、米娅 | `res.rice_wine` `res.dried_vegetable` | `small old noodle shop interior, wooden counter, steam from bowls, red stools, warm yellow light` |
| `place.bit.town_market` | 早市 | `MARKET` | ENTER / OBSERVE / GATHER | 五点半就有人蹲在地上摆摊，菜还带着泥。 | 周野、齐岭、唐糖 | `res.farm_cabbage` `res.fish_scale` | `busy early morning town market, produce laid on tarps on the ground, vendors crouching, hazy dawn light` |
| `place.bit.second_hand_shop` | 二手书店 | `SHOP` | ENTER / OBSERVE / TALK / GATHER | 书架顶到天花板，找书要爬梯子，梯子也快塌了。 | 林砚、苏黎 | `res.used_book` | `cramped second-hand bookstore, towering shelves, wobbly ladder, stacks of books on floor, dusty light` |
| `place.bit.bookshop` | 旧书店 | `SHOP` | ENTER / OBSERVE / GATHER | 老板自己会补书，补得比原版还整齐。 | 陈秋、林砚 | `res.torn_letter` | `small old bookshop with repair workbench, glue and paper stacks, handwritten price tags, warm lamp` |
| `place.bit.cafe_press` | 转角咖啡馆 | `CAFE` | ENTER / REST / TALK | 靠窗那台机器坏了三个月，老板说要等零件。 | 米娅、顾青 | `res.coffee_bean` | `corner coffee shop, broken espresso machine by the window, small tables, warm afternoon light` |
| `place.bit.teahouse` | 茶馆 | `CAFE` | ENTER / REST / TALK | 竹椅、旧茶具，店里永远只有四五个人。 | 樟叔、陈秋 | `res.tea_leaf` | `traditional teahouse interior, bamboo chairs, old clay teapots, quiet, steam rising, soft light` |
| `place.bit.bakery_town` | 老城面包房 | `SHOP` | ENTER / EAT / GATHER | 排队的人从门口排到巷子里，队伍是弯的。 | 唐糖 | `res.candy_grain` | `busy town bakery, long queue bending into the alley, display of pastries, warm morning light` |
| `place.bit.town_mall` | 百货楼 | `SHOP` | ENTER / TALK / OBSERVE | 扶梯有一半时间是坏的，广播还特别大声。 | 高见、米娅 | `res.shopping_bag` | `old department store interior, half-broken escalator, loud ceiling speakers, bright harsh lighting` |
| `place.bit.town_hospital` | 县医院 | `CLINIC` | ENTER / REST | 挂号窗只有两个，走廊里的椅子永远满着。 | 韦禾、欧阳让 | `res.remedy_powder` | `county hospital corridor, crowded waiting chairs, two registration windows, fluorescent light, patients waiting` |
| `place.bit.town_station` | 小火车站 | `STATION` | ENTER / OBSERVE / TALK | 一天四班车，站台上只有一块电子屏在闪。 | 罗小满、蒋成一 | `res.ticket_stub` | `small rural railway station, one platform, flickering electronic board, empty benches, overcast morning` |
| `place.bit.town_park` | 城中公园 | `PARK` | ENTER / EXPLORE / REST | 人工湖上有人划船，租船的押金要现金。 | 姜小渔、温宁 | `res.duckweed` | `urban city park, small artificial lake, paddle boats, stone paths under trees, daylight` |
| `place.bit.riverside_street` | 河堤老街 | `STREET` | ENTER / OBSERVE / EXPLORE | 堤上跑步的人很多，钓鱼的人也有两个。 | 姜小渔、骆师傅 | `res.freshwater_shell` | `riverside embankment promenade, joggers, a few fishermen on the slope, evening river light` |
| `place.bit.underpass_street` | 地下通道 | `STATION` | ENTER / OBSERVE / GATHER | 通道里有人摆摊，墙上贴着治脱发的广告。 | 高见 | `res.pasted_poster` | `underground pedestrian passage, small vendors, wall covered in pasted advertisements, harsh backlit entrance` |
| `place.bit.cinema_old` | 老影院 | `STAGE` | ENTER / WATCH | 银幕有一道横划痕，放映机还是胶片的。 | 江予、苗小蝶 | `res.film_strip` | `old single-screen cinema interior, scratched film screen, projector booth at back, red velvet seats, dusty` |
| `place.bit.town_warehouse` | 旧仓库 | `CONSTRUCTION` | ENTER / EXPLORE / GATHER | 空得能听见回声，货架倒了一半，没人知道为什么。 | 高见、骆师傅 | `res.crate_wood` | `empty old warehouse, collapsed shelving, dusty concrete floor, long echoes, shafts of light from high windows` |
| `place.bit.suburb_construction` | 郊区工地 | `CONSTRUCTION` | ENTER / EXPLORE / GATHER | 塔吊还在转，围挡上画着还没盖完的楼。 | 骆师傅、沈砚 | `res.concrete_block` | `suburban construction site, tall tower crane, painted hoardings, dirt tracks, overcast` |
| `place.bit.village_stone` | 石村 | `VILLAGE` | ENTER / EXPLORE / TALK / GATHER | 石头房子，屋顶是黑的，晚上安静得能听见泉声。 | 齐岭、骆师傅 | `res.handwoven_bag` | `stone village in the mountains, dark stone roofs, narrow lanes, a spring channel running through, dawn mist` |

### J. 异常与不存在的空间（10）

> 设计原则：这一区的地点**只在特定条件（雨夜 / 深夜 / 持有 ANOMALY 资源 / 特定对话后）出现**，
> `actions` 刻意只给 1–2 个，不提供采集与制作，不提供 `TALK`（除了明确标注的两个）。
> 出图统一用冷紫/单色调，画面里**不要放人**，避免"图里有人但游戏里没有"的错觉。

| id | name | type | actions | description | 关联人物 | 关联资源 | 缩略图提示词 |
|----|------|------|---------|-------------|----------|----------|-------------|
| `place.bit.rain_room` | 雨里的房间 | `ANOMALY_SPACE` | OBSERVE / ENTER | 下雨时某个房间突然多出来，进去就出不来。 | 沈墨 | `res.reversed_rain` | `impossible room appearing in rain, open doorway floating with no walls, rain falling inside, cold monochrome` |
| `place.bit.blank_wall` | 无门之墙 | `BLANK_WALL` | OBSERVE | 走廊尽头是一面没有门的墙，明天这里会有门。 | 何岸 | `res.blank_slot` | `flat blank corridor wall with no door, faint dust outline of a doorframe, single dim light, unsettling` |
| `place.bit.stair_end` | 楼梯尽头 | `STAIRWAY_END` | OBSERVE / EXPLORE | 这层楼按图纸没有，但台阶上有磨损的痕迹。 | 韦禾、何岸 | `res.extra_floor` | `staircase leading to a floor that should not exist, worn step edges fading into darkness, eerie cool light` |
| `place.bit.anomaly_corridor` | 多出来的走廊 | `TUNNEL` | ENTER / EXPLORE | 长度比外面那条长，走到底还是走廊。 | 娄七、三更 | `res.parallel_ladder` | `endless repeating school corridor, identical doors, fluorescent light, slight cold purple tint` |
| `place.bit.duplicate_room_708` | 708 室 | `DOOR` | ENTER / OBSERVE | 门牌是 708，可这栋楼最高只有 6 层。 | 娄七、沈砚 | `res.door_708` | `single door in a plain concrete wall with number 708 plaque, dim corridor, cold light, no handle on the inside` |
| `place.bit.mirror_room` | 镜子的房间 | `ANOMALY_SPACE` | OBSERVE | 房间不大，但镜子里的房间看起来大一点。 | 徐晚、何岸 | `res.twin_mirror` | `small room lined with full-height mirrors, reflections slightly out of sync, pale light, cold` |
| `place.bit.empty_classroom_night` | 深夜的空教室 | `EMPTY_ROOM` | ENTER / OBSERVE | 晚自习后还有一间教室亮着灯，没有人。 | 何岸、苗小蝶 | `res.empty_seat_roll` | `empty classroom lit only by overhead light at night, rows of unoccupied desks, chairs slightly askew` |
| `place.bit.same_bird_road` | 同一只鸟的路 | `STREET` | OBSERVE / FOLLOW | 同一只鸟每天在同一根电线上停，同一个时间。 | 三更、姜小渔 | `res.same_bird` | `single bird perched on a telephone wire at dusk, blurred motion ghosting behind it, cold blue tone` |
| `place.bit.lost_bell_tower` | 失钟塔 | `TOWER` | ENTER / EXPLORE | 塔上的钟早就没了，塔还在。 | 老陈、三更 | `res.lost_bell` | `old bell tower with no bell, open stone frame, pigeons circling, dusk sky, low saturation` |
| `place.bit.below_piano_room` | 钢琴下面的房间 | `ANOMALY_SPACE` | ENTER / EXPLORE | 琴房地板下面还有一层，图纸上只画了琴房。 | 罗小满、娄七 | `res.room_under_score` | `hidden room beneath a music room, low ceiling, scattered sheet music, single warm lamp, cramped` |

---

## 五、地点 ↔ 人物 ↔ 资源 索引（供程序侧建反向表）

### 5.1 人物常驻地点对照

| 人物 | 常驻 | 次要出现 | 关联小节 |
|------|------|----------|----------|
| 林砚 | `library` | `library_four` `study_room` `second_hand_shop` `leaf_lane` | A C G I |
| 周野 | `running_track` | `canteen_one` `dining_line` `dining_line` `town_market` `noodle_shop` | F E I |
| 徐晚 | `north_lake` | `lake_wood` `art_room` `art_terrace` `mirror_room` | G B J |
| 何岸 | `stacks` | `old_library_annex` `garden` `dorm_stair` `blank_wall` | C D H J |
| 齐岭 | `orchard` | `berry_bush` `mushroom_patch` `veggie_pantry` `village_stone` | G E I |
| 沈砚 | `lab_415` | `lab_building` `workshop` `power_room` `duplicate_room_708` | B H J |
| 苏黎 | `library_four` | `dorm_1` `classroom_b` `club_center` `second_hand_shop` | C D B A I |
| 陈秋 | `history_museum` | `photo_wall` `archive_room` `bookshop` `chess_pavilion` | C I F |
| 高见 | `club_center` | `plaza` `lecture_hall` `school_stage` `town_mall` | A F I |
| 白依 | `sewing_room` | `laundry` `art_terrace` `dorm_2` | B D |
| 罗小满 | `music_room` | `band_room` `playground` `back_alley` `below_piano_room` | B F H J |
| 蒋成一 | `field_plot` | `greenhouse` `greenhouse_yard` `rice_paddy` `camp_field` | G I |
| 韦禾 | `gate_north` | `dorm_stair` `town_hospital` `clinic` `stair_end` | A H I J |
| 苗小蝶 | `darkroom` | `photo_wall` `old_ground` `cinema_old` `empty_classroom_night` | C H I J |
| 欧阳让 | `lab_basement` | `roof_lab` `closed_storeroom` `unfinished_floor` | B H |
| 唐糖 | `canteen_kitchen` | `pantry` `bakery` `bakery_town` `canteen_one` | E I |
| 江予 | `moot_court` | `classroom_a` `underpass_street` `cinema_old` `tennis_court` | B I F |
| 阿武 | `gym` | `court` `climbing_gym` `swimming_pool` | F |
| 温宁 | `room_204` | `garden` `hill_grass` `dorm_roof` `town_park` | D G I |
| 顾青 | `radio_studio` | `library_roof` `campus_road` `underpass_street` `cafe_press` | C A I |
| 廖一 | `study_room` | `classroom_a` `canteen_two` `lecture_hall_tech` `table_tennis` | C B E F |
| 米娅 | `town_market` | `dining_line` `campus_road` `dorm_courtyard` `old_street` `hill_grass` | I A D G |
| 骆师傅 | `bike_shed` | `power_room` `laundry` `riverside_street` `river_bridge` `village_stone` | A B E I |
| 姜小渔 | `wetland_board` | `rocky_shore` `camp_field` `bamboo_grove` `town_park` | G I |
| 樟叔 | `bike_shed` | `orchard` `abandoned_garden` `teahouse` `dorm_courtyard` | A G I D |
| 老陈 | `gym_basement` | `lost_bell_tower` `closed_storeroom` | H J |
| 三更 | `hall_roof` | `anomaly_corridor` `same_bird_road` `lost_bell_tower` `abandoned_garden` | H J |
| 沈墨 | `rain_road` | `rain_room` `campus_road` `underpass_street` | J A |
| 夏星 | `old_ground` | `photo_wall` `history_museum` `north_lake` | H C G |
| 娄七 | `lost_and_found` | `dorm_attic` `gym_basement` `laundry` `anomaly_corridor` `below_piano_room` | C D H J |

### 5.2 "只有地点、无人"的纯场景（氛围与探索向）

这类地点**不绑定任何 NPC**，用于填地图、藏彩蛋、放稀有资源。共 24 处：

`campus_gate` `notice_board` `pontoon` `campus_pond_bridge` `paved_walk` `underpass` `garden` `library_roof` `reeds_bank` `dorm_canteen` `pantry` `veggie_pantry` `power_room` `tennis_court` `table_tennis` `swimming_pool` `chess_pavilion` `chess_pavilion` `hill_grass` `closed_storeroom` `unfinished_floor` `noodle_shop` `bakery_town` `town_mall` `underpass_street` `warehouse` `campus_map_wall`（见下方增补）

### 5.3 增补：9 个纯场景点（无人物绑定）

| id | name | type | 描述要点 |
|----|------|------|----------|
| `place.bit.campus_map_wall` | 校园地图墙 | `PLAZA` | 正门内侧一面瓷砖画的校园平面图，下雨就会把一角冲淡。 |
| `place.bit.sundial` | 日晷 | `PLAZA` | 广场中央的石日晷，指针是铜的，据说某天会走偏。 |
| `place.bit.lost_shoe_cabinet` | 旧鞋柜 | `WORKSHOP` | 车棚后面一排没人要的鞋，鞋尖都朝同一个方向。 |
| `place.bit.corner_store` | 校门口小卖部 | `SHOP` | 五毛一瓶水，老板娘记得每个学生喝什么。 |
| `place.bit.laundry_drying_yard` | 晾晒场 | `LAUNDRY` | 后勤楼顶的晾晒场，白床单一排排，风大时全在飘。 |
| `place.bit.broken_tricycle` | 坏三轮车 | `WORKSHOP` | 车棚后面一辆卸不下来的三轮，车斗里全是落叶。 |
| `place.bit.painted_wall` | 涂鸦墙 | `STREET` | 操场外一整面墙，图案被雨水泡开，看不出原来画的是什么。 |
| `place.bit.cat_shelter` | 猫窝棚 | `WORKSHOP` | 纸箱加一块布，猫在里面睡，猫比人多。 |
| `place.bit.empty_flagpole` | 空旗杆 | `PLAZA` | 国旗升到一半，绳子断了，没人报修。 |

---

## 六、配套新增资源（41 项）

> **本节内容已迁出**：41 项新增资源的完整设计（id / name / category / rarity / tags / description / stackLimit / 图标提示词）
> 见 **`resources_design.md` 第七节「配套新增（地点表引入 · 41 项）」**，那里是唯一真源。

本表地点共引用 175 个资源 id，已全部并入 `resources.json`（**现共 511 条**），零悬空引用。

---

## 七、已定事项与待办

### 7.1 本轮已定（2026-10-03 评审确认）

| # | 事项 | 结论 |
|---|------|------|
| 1 | `type` 枚举 | ✅ **已由 90 个收敛为 50 个**，合并规则见 1.1（保留玩法差异、抹掉外观差异） |
| 2 | 41 项配套新增资源 | ✅ **已追加进 `resources_design.md` 第七节**（唯一真源），并已重新生成 `resources.json`（470 → 511） |
| 3 | `actions` 与代码端不一致 | ✅ 代码端未实现的动作先留着，**不阻塞出图与评审** |
| 4 | 总量 139 处 | ✅ 不再收敛，按当前规模推进 |

### 7.2 转换 places.json 前的必要前置

1. ~~重新生成 `resources.json`~~ ✅ **已完成**（511 条，41 项配套新增已合入，零悬空引用）。
2. **填入 lat / lng**（本文全部留空，等你填）。
3. ~~`npcs.json` 的 48 个 `placeId` 引用校验~~ ✅ 已确认全部有定义，转人物 json 时可安全开启。

### 7.3 仍待你决定（不阻塞当前工作）

| # | 事项 | 建议 |
|---|------|------|
| 1 | `NIGHT_ONLY` / `RAIN_ONLY` 塞在 `actions` 里是否合适 | 建议单独开 `conditions` 字段，J 区触发条件（雨夜 / 深夜 / 持 ANOMALY 资源 / 特定对话后）塞进动作列表语义太乱 |
| 2 | 室外地点是否需要接独立场景图 | 建议分批：先做 G 区自然 + I 校外，室内可复用 |
| 3 | 校名 / 省份未定 | I 区"县医院""小火车站""石村"为占位，世界观定了统一替换 |
| 4 | J 区是否完全不进人物对话 | 现设计为不给 `TALK`，人物线（何岸 / 夏星 / 娄七 / 三更）与地点线分开推进 |
| 5 | `travelMinutes` 与真实距离是否校验 | 需要一份地点间距表；经纬度填好后可自动生成 |

