# 校园版星露谷物语 · 资源物品表（300 项）

> 本文用于**内容评审与图标生成**，字段命名与 `resources.json` 对齐。
> 确认清单后再统一转成 JSON（`id` / `name` / `category` / `rarity` / `tags` / `description`，制造类额外带 `stackLimit`）。

---

## 一、字段与命名约定

- **category**：`NATURE` 自然 / `KNOWLEDGE` 知识 / `CULTURE` 文化 / `MEMORY` 记忆 / `ANOMALY` 异常 / `CRAFT` 制造
- **rarity**：`COMMON` 普通 → `UNCOMMON` 少见 → `RARE` 稀有 → `ANOMALY` 异常
- **id**：`res.` + 英文小写下划线，英文取意象词，不用中文直译，跨条目不重名
- **tags**：英文标签，用于图鉴检索与筛选，2–4 个
- **获取方式**：给策划看的入口线索，不进 JSON

## 二、数量分配（共 300）

| 类别 | 数量 | 主产地 |
|------|------|--------|
| 自然资源 NATURE | 80 | 林荫道、小湖湿地、菜圃果园、屋顶与围墙 |
| 知识资源 KNOWLEDGE | 48 | 图书馆、实验楼、校史馆、档案室 |
| 文化资源 CULTURE | 52 | 食堂、社团、礼堂、节庆与日常仪式 |
| 记忆资源 MEMORY | 28 | 旧照片、声音、手写纸条、地点痕迹 |
| 异常资源 ANOMALY | 24 | 雨天、深夜、镜子、楼道尽头 |
| 制造资源 CRAFT | 68 | 工作台、缝纫社、机工房、陶艺教室、食堂后厨 |

> 记忆与异常刻意少，制造与自然多——符合"校园日常为主、异常夜里才出现"的节奏。

## 三、图标提示词约定

所有提示词后统一追加：**`game item icon, single object, centered, flat vector shading, soft rim light, clean background, no text`**。
物品主体描述控制在 8–16 字，风格统一即可批量出图。

---

# 一、自然资源 NATURE（80）

## 1.1 草叶野果（18）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 雨生苔痕 | res.rain_moss | COMMON | moss, rain, garden | 雨后才舒展的苔藓，摸上去凉而软。 | 石阶缝里的湿青苔，浅绿绒毛 |
| 芦苇叶 | res.reed_leaf | COMMON | reed, lake | 湖岸的芦苇叶，晒干可以编点什么。 | 单片长芦苇叶，边缘卷曲 |
| 松果 | res.pine_cone | COMMON | pine, autumn | 落在小径上的松果，秋天尤其多。 | 棕色松果，鳞片张开 |
| 花瓣 | res.petal | COMMON | flower, spring | 刚落下不久的花瓣，颜色还很新。 | 单枚浅粉花瓣，边缘带水珠 |
| 带露的草叶 | res.dew_grass | COMMON | grass, dawn | 天刚亮时草叶上挂着的露水，一碰就碎。 | 草叶挂着透明露珠 |
| 野莓 | res.wild_berry | COMMON | berry, bush | 灌木上摘下来的野莓，酸甜都靠运气。 | 三颗紫红野莓带叶 |
| 野菌 | res.wild_mushroom | COMMON | mushroom, rain | 雨后从树根边冒出来的菌子，只有湿润那几天好找。 | 棕色伞菌，菌盖湿润 |
| 青苹果 | res.green_apple | COMMON | apple, orchard, autumn | 果林里挂着的青苹果，秋天最沉。 | 青苹果带一片叶 |
| 植物纤维 | res.plant_fiber | COMMON | fiber, bush | 从灌木上剥下来的韧皮纤维，搓一搓就能当绳用。 | 一束浅褐韧皮纤维 |
| 浮木 | res.drift_wood | COMMON | wood, lake | 湖岸冲上来的小木块，晒干以后是很好的材料。 | 灰白浮木，表面有水痕 |
| 白桑葚 | res.white_mulberry | UNCOMMON | mulberry, tree, spring | 透明发白的桑葚，落在树下的水泥台阶上。 | 半透明白桑葚一串 |
| 野胡萝卜 | res.wild_carrot | COMMON | root, grass | 园艺园篱笆边冒出来的野胡萝卜，橙得晃眼。 | 橙红胡萝卜带缨叶 |
| 荠菜 | res.shepherd_purse | COMMON | herb, spring | 荠菜是春天最先能采到的，焯水后很清爽。 | 锯齿叶荠菜一束 |
| 蒲公英绒球 | res.dandelion_fluff | COMMON | dandelion, wind | 一吹就散的东西，得用广口瓶倒着收。 | 白色蒲公英球，半散 |
| 蕨芽 | res.fern_shoot | COMMON | fern, moist | 湿地边卷成拳头的嫩蕨芽，展开要两天。 | 卷曲嫩蕨芽，浅绿 |
| 野薄荷 | res.wild_mint | COMMON | mint, herb | 掐一片揉一揉，凉气能从鼻子里冲到眼睛。 | 圆叶薄荷，带银白绒毛 |
| 车前草 | res.plantain_leaf | COMMON | herb, lawn | 操场边最常见的矮草，叶脉是五条平行线。 | 车前草叶片露珠 |
| 苔藓球 | res.moss_ball | UNCOMMON | moss, forest | 从湿地上捧起来能团成球的苔藓，装饰用。 | 团成球的绿苔藓 |

## 1.2 树木花草（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 银杏叶 | res.ginkgo_leaf | COMMON | ginkgo, autumn | 扇形叶片，落在操场跑道上能铺满一条。 | 金黄扇形银杏叶 |
| 樱花瓣 | res.sakura_petal | COMMON | sakura, spring | 樱花季的一阵风就能攒一小捧。 | 淡粉樱花瓣两片 |
| 梧桐落叶 | res.plane_leaf | COMMON | plane, autumn | 阔大厚实的梧桐叶，踩上去有脆响。 | 大片褐黄梧桐叶 |
| 松针束 | res.pine_needles | COMMON | pine, forest | 捡的时候一扎一扎地扎手。 | 一束深绿松针 |
| 竹节 | res.bamboo_segment | COMMON | bamboo, garden | 竹林里断掉的一节竹子，断面很干净。 | 单节青竹，带切口 |
| 树枝 | res.branch | COMMON | branch, wood | 掉在操場边的细枝，随便捡。 | 中等粗细枯树枝 |
| 树皮块 | res.bark_chunk | COMMON | bark, wood | 松树皮，掰下来时带着松香味。 | 一块红褐松树皮 |
| 桂花 | res.osmanthus | UNCOMMON | osmanthus, autumn | 晚风一吹，整条路都是这个味道。 | 小簇金桂小花 |
| 蒲公英小花 | res.dandelion_flower | COMMON | flower, lawn | 黄得很彻底的小花，冬天也没了。 | 黄色蒲公英小花 |
| 野菊花 | res.wild_chrysanthemum | COMMON | flower, autumn | 墙角一开就是一片，淡黄得温柔。 | 淡黄野菊花一朵 |
| 茉莉枝 | res.jasmine_sprig | RARE | jasmine, night | 晚自习后路过花坛能闻到，花期只有几周。 | 白茉莉小枝 |
| 枫叶 | res.maple_leaf | COMMON | maple, autumn | 尖角的红叶，夹进书里会一直红着。 | 五裂红枫叶 |

## 1.3 鸟虫小兽（14）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 麻雀羽 | res.sparrow_feather | COMMON | feather, bird | 操场边捡的，灰褐色带白边。 | 灰褐麻雀羽毛 |
| 乌鸦黑羽 | res.crow_feather | UNCOMMON | feather, crow | 乌黑泛蓝的羽毛，摸上去像纸。 | 蓝黑乌鸦羽毛 |
| 白鹭羽 | res.egret_feather | UNCOMMON | feather, wetland | 湿地芦苇丛里偶尔飘来一根。 | 纯白鹭羽，细长 |
| 蝴蝶残鳞 | res.butterfly_scale | RARE | butterfly, scale | 一只闪蓝的蝴蝶落在花坛，碰一下就碎了。 | 零散闪蓝蝶鳞 |
| 独角仙壳 | res.rhinoceros_beetle_shell | UNCOMMON | beetle, summer | 黑色甲壳，角断了一半。 | 黑色独角仙甲壳 |
| 萤火虫灯 | res.firefly_lantern | RARE | firefly, night | 草地上明明灭灭，捉到会亮一小会儿。 | 尾部发光的萤火虫 |
| 蜻蜓翅 | res.dragonfly_wing | COMMON | dragonfly, summer | 薄得能看见背景的翅膀。 | 透明蜻蜓翅一对 |
| 蚯蚓饵 | res.earthworm_bait | COMMON | worm, fishing | 钓鱼的人说本地蚯蚓不上钩，但他们照用。 | 弯扭的深红蚯蚓 |
| 田螺 | res.river_snail | COMMON | snail, river | 稻田沟渠边一翻一大把。 | 深褐田螺壳 |
| 螺壳 | res.snail_shell | COMMON | shell, snail | 白底螺旋纹，年头越久纹路越清楚。 | 奶白螺旋贝壳 |
| 兔绒毛 | res.rabbit_fur | UNCOMMON | rabbit, fur | 操场草坪边缘的兔子留下的，柔软得过分。 | 灰白兔毛一团 |
| 猫毛 | res.cat_fur | COMMON | cat, fur | 教学楼窗台那只总在晒太阳的狸花猫。 | 狸花猫毛卷 |
| 蝙蝠翼膜 | res.bat_wing | RARE | bat, night | 旧体育馆屋顶下面有它们。 | 展开的褐色小蝙蝠翼 |
| 鸟巢枝 | res.nest_twig | UNCOMMON | nest, twig | 掉在树上的空鸟巢，被风拆了一半。 | 编织的空鸟巢 |

## 1.4 水生（10）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 湖白鱼 | res.lake_whitefish | COMMON | fish, lake | 校湖里的银色小鱼，一网能十几条。 | 银色小鱼，尾巴张开 |
| 鲫鱼 | res.crucian_carp | COMMON | carp, lake | 脾气倔，钓上来时腮帮子鼓鼓的。 | 中等鲫鱼，鳞片反光 |
| 河虾 | res.river_shrimp | UNCOMMON | shrimp, river | 透明的小虾，湖水凉处最多。 | 半透明青虾，弓背 |
| 蝌蚪 | res.tadpole | COMMON | tadpole, pond | 一团黑的小东西，游起来像逗号。 | 一串黑色蝌蚪 |
| 水藻 | res.water_algae | COMMON | algae, lake | 捞上来滑溜溜，能闻到水草味。 | 绿色丝状水藻 |
| 荷叶 | res.lotus_leaf | COMMON | lotus, pond | 圆得像伞，蹲下去看能看见叶脉。 | 大圆荷叶带露 |
| 莲子 | res.lotus_seed | UNCOMMON | lotus, seed | 埋在泥里的莲藕深处，别急着挖。 | 圆润莲子一对 |
| 浮萍 | res.duckweed | COMMON | duckweed, pond | 铺满水面的细小绿点，踩上去像地毯。 | 密集浮萍小圆叶 |
| 蚌壳 | res.clam_shell | UNCOMMON | clam, river | 河边翻到的，壳内一层虹色。 | 打开的虹色蚌壳 |
| 鱼鳞 | res.fish_scale | COMMON | scale, lake | 收拾鱼时顺手留一片，闪着银光。 | 单片银色鱼鳞 |

## 1.5 石土矿物（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 石块 | res.stone | COMMON | stone, rubble | 随处可见，敲碎能填缝。 | 灰白不规则石块 |
| 雨痕石 | res.rain_stone | COMMON | stone, rain | 表面被雨水蚀出细密麻点。 | 带麻点的深灰石 |
| 湖底石 | res.lakeside_stone | UNCOMMON | stone, lake | 捞上来的石头，水草还缠在上面。 | 缠水草的河石 |
| 沙粒 | res.sand | COMMON | sand, ground | 操场沙坑里的细沙，装一把就漏。 | 一小撮细沙 |
| 湿沙 | res.wet_sand | COMMON | sand, rain | 雨后结块的湿沙，可以塑形。 | 深色湿沙块 |
| 黏土 | res.clay | COMMON | clay, craft | 陶艺教室门口的箱子，来路不明但很软。 | 灰白黏土团 |
| 石英碎块 | res.quartz_chunk | UNCOMMON | quartz, crystal | 断口像玻璃，边缘很利。 | 透明乳白石英晶体 |
| 云母片 | res.mica_flake | UNCOMMON | mica, crystal | 薄得能透光，一片一片剥下来。 | 闪银云母薄片叠层 |
| 赤铁块 | res.hematite | RARE | hematite, ore | 铁红发暗的矿石，很沉。 | 铁锈红赤铁矿石 |
| 硫磺结晶 | res.sulfur_crystal | RARE | sulfur, crystal | 标本馆窗台上摆的那块，凑近有味道。 | 明黄硫磺晶簇 |
| 冰晶 | res.ice_crystal | UNCOMMON | ice, winter | 冬天从水龙头接的，握着刺骨。 | 透明六角冰晶 |
| 煤块 | res.coal | UNCOMMON | coal, rock | 黑得发亮，用手指一掐就掉粉。 | 亮黑煤块带棱 |

## 1.6 天气与现象（9）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 晨露 | res.morning_dew | COMMON | dew, dawn | 太阳一出来就没了，得赶早。 | 玻璃上的一排露珠 |
| 雨水滴 | res.rain_droplet | COMMON | rain, water | 屋檐下接了一整排，接住就是一小份。 | 单颗水滴带涟漪 |
| 雪花 | res.snowflake | UNCOMMON | snow, winter | 落在掌心会化的那一种。 | 六角雪花晶体 |
| 霜花 | res.frost_flower | UNCOMMON | frost, winter | 清晨玻璃内侧长出来的白色枝状花纹。 | 白色霜花纹样 |
| 积雨云 | res.thundercloud | RARE | cloud, storm | 操场上看云的人很多，谁都没拍照。 | 低垂的深灰积雨云 |
| 虹光碎片 | res.rainbow_shard | RARE | rainbow, rain | 雨后水洼里那道虹，被踩到就散了。 | 水洼中的彩虹碎光 |
| 江雾 | res.river_mist | UNCOMMON | mist, water | 后半夜的江面会浮起一层白，久不散。 | 河面低垂白雾 |
| 暑气纹 | res.summer_heat | COMMON | heat, summer | 操场塑胶跑道上会抖的一层空气。 | 扭曲的热气波纹 |
| 玻璃碎片 | res.glass_shard | COMMON | glass, shard | 旧窗户剩下的，边角发绿。 | 带绿边的玻璃碎片 |

## 1.7 作物（5）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 稻穗 | res.rice_ear | COMMON | rice, farm, autumn | 艺术楼下那片水田的收成，沉得压弯。 | 金黄饱满稻穗 |
| 麦穗 | res.wheat_ear | COMMON | wheat, farm, summer | 麦田边缘的野麦，穗子很轻。 | 浅黄麦穗 |
| 玉米棒 | res.corn_cob | COMMON | corn, farm, autumn | 剥开就是整齐的一排黄牙齿。 | 剥开的玉米棒 |
| 土豆 | res.potato | COMMON | potato, farm | 挖出来一串，形状都不规矩。 | 带泥的土豆两个 |
| 草莓 | res.strawberry | UNCOMMON | strawberry, farm, spring | 园艺园边垄种的那几行，红得最早。 | 红草莓带蒂 |

---

# 二、知识资源 KNOWLEDGE（48）

## 2.1 图书档案（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 借书证 | res.library_card | COMMON | library, card | 塑封磨白的借书证，照片上的人剪了刘海。 | 泛蓝借书证挂绳 |
| 书目卡 | res.shelf_card | COMMON | library, book | 从书脊间抽出的一张旧书目卡，字迹已经发淡。 | 淡黄手写书目卡 |
| 阅读随记 | res.reading_note | COMMON | library, note | 坐下来读完一段之后写下的几行字。 | 摊开的笔记本页 |
| 藏书印章 | res.library_stamp | UNCOMMON | library, stamp | 椭圆形朱红印章，盖在扉页上。 | 朱红椭圆印章 |
| 馆藏索引 | res.catalogue_index | COMMON | library, index | 抽屉卡片的目录，一格一格塞满。 | 卡片式目录抽屉 |
| 术语页 | res.glossary_page | COMMON | library, paper | 从术语表上撕下来的一页，定义只有一句。 | 单页名词解释 |
| 旧信件 | res.archive_letter | UNCOMMON | archive, letter | 档案室的牛皮纸信封，邮票是很早的面值。 | 牛皮纸信封，邮票褪色 |
| 考试笔记 | res.exam_notes | COMMON | exam, note | 塞在书里的折角小抄，铅笔字擦不掉。 | 折角的手写笔记 |
| 图册 | res.blueprint_book | UNCOMMON | library, book | 建筑图册，比手掌大一圈。 | 摊开的建筑图册 |
| 野外手册 | res.field_manual | UNCOMMON | manual, outdoor | 生物社的活动手册，最后一页是空的。 | 厚野外手册，封面磨旧 |
| 书签 | res.bookmark | COMMON | library, bookmark | 夹在读到一半的地方，通常停在一整章开头。 | 半露的书签条 |
| 借阅小票 | res.borrow_stub | COMMON | library, receipt | 打了孔的窄条，日期那一栏有时是空的。 | 打孔的借阅小票 |

## 2.2 学科知识（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 数学推演纸 | res.math_formula_note | COMMON | math, paper | 写了满满一页又划掉半页的那种纸。 | 写满公式又涂改的纸 |
| 物理实验记录 | res.physics_experiment_record | COMMON | physics, lab | 数据栏写着"待补"，实际再也不补了。 | 带表格的实验记录单 |
| 化学试剂标签 | res.chemistry_reagent_label | COMMON | chemistry, lab | 实验室废液桶上撕下的标签，字被腐蚀了一半。 | 泛黄试剂标签 |
| 标本卡 | res.biology_specimen_card | RARE | biology, specimen | 蝴蝶标本的原始登记卡，编号 07。 | 手写标本登记卡 |
| 地形等高图 | res.geography_contour | UNCOMMON | geography, map | 等高线密得像指纹，画的是学校后山。 | 等高线地形图 |
| 历史讲义抄本 | res.history_transcript | COMMON | history, note | 抄得很整齐，连批注也一起抄了。 | 竖排抄本纸页 |
| 单词默写纸 | res.english_word_sheet | COMMON | english, paper | 前三行对，后面全是错的。 | 默写单词纸，红笔批改 |
| 乐谱断页 | res.music_score_fragment | UNCOMMON | music, score | 从谱夹里掉出来的一页，正好缺了结尾。 | 手写五线谱残页 |
| 色彩练习页 | res.art_color_study | COMMON | art, color | 二十个灰色方块里的第七个突然有了颜色。 | 灰阶色卡，第七格着彩 |
| 算法草稿纸 | res.algorithm_paper | COMMON | algorithm, paper | 背面全是箭头和箭头指向的箭头。 | 满页流程箭头草稿 |
| 机械制图纸 | res.mechanical_drawing | UNCOMMON | engineering, drawing | 机房的图，红线改了三遍。 | 蓝图风格机械零件图 |
| 哲学批注 | res.philosophy_note | RARE | philosophy, note | 某一句话被用三种笔迹批过三次。 | 密集手写批注的书页 |

## 2.3 校史档案（8）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 楼层平面图 | res.school_floorplan | UNCOMMON | archive, map | 标着"原教务处"的那版，现在那里是杂物间。 | 折痕很多的楼层平面图 |
| 创校合影 | res.founding_photo | RARE | archive, photo | 台阶上站满人的黑白合影，右下角有编号。 | 黑白老式集体合影 |
| 校刊一页 | res.yearbook_page | COMMON | archive, paper | 印刷粗糙，纸已经泛黄到文字发橙。 | 泛黄校刊内页 |
| 钟铭拓片 | res.bell_rubbing | RARE | archive, rubbing | 老钟铭文的拓片，纸面全是炭黑。 | 黑色拓片钟铭 |
| 纪念碑拓片 | res.monument_rubbing | UNCOMMON | archive, rubbing | 操场边的碑，除了名字没别的字了。 | 残缺碑文拓片 |
| 旧校服样片 | res.old_uniform | RARE | archive, cloth | 只有领口和袖口两块布，颜色难认。 | 褪色旧校服袖口 |
| 旧校址地图残片 | res.campus_map_fragment | RARE | archive, map | 被裁掉一半，剩下的部分里没有湖。 | 残破老地图一角 |
| 登记簿 | res.register_ledger | UNCOMMON | archive, ledger | 借阅登记簿，本子厚得摊不平。 | 厚牛皮纸登记簿 |

## 2.4 观察与实验日志（8）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 气象记录本 | res.weather_log | COMMON | weather, log | 气温栏永远缺一天，那天写的是"忘了"。 | 翻开的气象记录本 |
| 水位记录 | res.tide_note | UNCOMMON | lake, data | 湖岸刻度上的数字，一个月涨一次。 | 笔记本上的水位折线图 |
| 观鸟记录表 | res.bird_watch_sheet | UNCOMMON | bird, log | 表格里画着潦草的火柴人。 | 观鸟记录表带简笔鸟 |
| 昆虫名录 | res.insect_survey | UNCOMMON | insect, log | 学名一个都拼错，但画得很像。 | 昆虫名录手稿本 |
| 水质检测卡 | res.water_quality_card | UNCOMMON | lab, water | 试纸比色卡，能对上九格颜色。 | 水质比色卡 |
| 广播值日记录 | res.radio_log | COMMON | radio, log | 播音社的播出表，字是圆珠笔写的。 | 播出排期表 |
| 巡查日志 | res.patrol_log | UNCOMMON | patrol, log | 保安室的记录，凌晨三点的字特别大。 | 手写巡查日志本 |
| 失物登记 | res.lost_and_found_record | COMMON | lost, log | "拾于操场看台"后面画了个箭头。 | 失物招领登记页 |

## 2.5 技艺与手记（8）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 手写配方 | res.cooking_recipe | COMMON | cooking, recipe | 配料表上有"适量"，适量是多少全凭心情。 | 手写菜谱卡片 |
| 编织图样 | res.craft_pattern | COMMON | craft, pattern | 钩针符号画成一团，但照着能编出来。 | 钩针编织符号图 |
| 修补要领 | res.repair_manual | UNCOMMON | repair, manual | 缝纫社墙上抄的，一共七条。 | 折页的修补说明 |
| 抄近道的记号 | res.shortcut_note | COMMON | route, note | 墙角的粉笔箭头，画的人不同、方向相同。 | 墙上粉笔箭头 |
| 考试时间表 | res.exam_timetable | COMMON | exam, schedule | 打印版，边角卷起来，压了一学期。 | 打印的考试时间表 |
| 留种笔记 | res.seed_saving_note | UNCOMMON | farm, note | 哪个季节留种、怎么防霉，写得极细。 | 种子保存笔记页 |
| 记账薄 | res.budget_ledger | COMMON | money, ledger | 校园卡充值记录，最后一行是负数。 | 小本子记着数字 |
| 校训释义 | res.motto_gloss | RARE | culture, note | 把校训每个字都解释了一遍的那张纸。 | 逐字注释的校训纸 |

---

# 三、文化资源 CULTURE（52）

## 3.1 节庆与仪式（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 节庆灯笼 | res.festival_lantern | COMMON | festival, lantern | 挂起来一排，风一吹整条路都晃。 | 红灯笼挂串 |
| 粽叶 | res.zongzi_leaf | COMMON | festival, bamboo | 端午节前宿舍楼下的竹叶一大堆。 | 宽大粽叶叠放 |
| 月饼模子 | res.mooncake_mold | UNCOMMON | festival, mold | 木模内壁刻着花纹，边缘磨圆了。 | 雕花木月饼模 |
| 七夕香囊 | res.qixiu_sachet | RARE | festival, sachet | 挂穗子的香囊，里面是晒干的艾草。 | 蓝布香囊带穗 |
| 红春联 | res.spring_couplet | COMMON | festival, paper | 贴在门框上的红纸，一年换一张。 | 手写红春联 |
| 爆竹纸屑 | res.firecracker_ash | COMMON | festival, debris | 初一早上满地红色碎纸，扫不完。 | 地上红色纸屑 |
| 灯谜条 | res.lantern_riddle | COMMON | festival, paper | 挂在灯笼下面的纸条，答案在灯背面。 | 窄长灯谜纸条 |
| 窗花 | res.paper_cut | COMMON | festival, paper | 剪出来的红纸，贴在玻璃内侧。 | 对称剪纸窗花 |
| 腊梅枝 | res.wintersweet | RARE | winter, flower | 期末考试前开花，算是鼓励。 | 蜡黄腊梅枝 |
| 青明花束 | res.qingming_bundle | COMMON | festival, flower | 白色小花扎成一把，露水还没干。 | 白花小束 |
| 鼓槌布 | res.drum_stick_wrap | UNCOMMON | festival, drum | 鼓队用，缠着红布。 | 缠红布的鼓槌 |
| 毕业帽 | res.graduation_cap | UNCOMMON | graduation, cloth | 扔上天的那些，多半被人捡回来。 | 黑色学士帽 |

## 3.2 食物饮品（12）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 今日菜签 | res.menu_ticket | COMMON | canteen, food | 食堂窗口挂着的小菜签，每天都不一样。 | 手写小菜签 |
| 食堂餐碗 | res.canteen_bowl | COMMON | canteen, dish | 缺口的老碗，谁用都会顺手拿它。 | 带缺口的白瓷碗 |
| 包子 | res.steamed_bun | COMMON | canteen, food | 一屉蒸笼的开盖瞬间，白汽糊住眼镜。 | 白胖包子一个 |
| 腌菜坛 | res.pickled_jar | COMMON | canteen, food | 后厨角落的坛子，闻着酸得清醒。 | 陶制腌菜坛 |
| 面碗 | res.noodle_bowl | COMMON | canteen, food | 汤凉了就不好吃了，所以要快。 | 大碗热汤面 |
| 豆浆 | res.soy_milk | COMMON | canteen, drink | 塑料杯装的那种，上层会结一层膜。 | 白色豆浆杯 |
| 面包 | res.campus_bread | COMMON | bakery, food | 便利店的下午折扣，标签会写"当天"。 | 圆面包切片 |
| 牛奶瓶 | res.milk_bottle | COMMON | drink, bottle | 早读课的配给，一瓶顶十分钟。 | 玻璃牛奶瓶 |
| 果子汽水 | res.fruit_soda | UNCOMMON | drink, soda | 玻璃瓶汽水，开盖要用手掌拍。 | 橘色汽水玻璃瓶 |
| 珍珠奶茶 | res.bubble_tea | COMMON | drink, boba | 吸管插到底得先攒一口气。 | 珍珠奶茶杯 |
| 冰淇淋球 | res.ice_cream_scoop | UNCOMMON | summer, dessert | 食堂冰淇淋机出来的第一勺总是最满。 | 球状冰淇淋 |
| 糖果粒 | res.candy_grain | COMMON | candy, snack | 便利店散装糖，透明罐子。 | 三颗彩色硬糖 |

## 3.3 艺术与作品（8）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 水墨残幅 | res.ink_painting | UNCOMMON | art, ink | 只剩右下角一小块，山还看得出。 | 墨色山水残片 |
| 速写本一页 | res.sketch_page | COMMON | art, sketch | 画的是走廊透视，线条很果断。 | 铅笔走廊速写 |
| 冲印照片 | res.photo_print | COMMON | photo, culture | 社团合影，边缘有齿孔和白边。 | 白边冲印照片 |
| 小陶偶 | res.clay_figurine | COMMON | pottery, art | 捏得像个人，同学都说像。 | 手捏小陶人偶 |
| 刺绣贴布 | res.embroidered_patch | UNCOMMON | craft, cloth | 社徽形状，线头还在外面。 | 刺绣徽章贴布 |
| 纸雕 | res.paper_sculpture | RARE | art, paper | 立体纸雕，灯光下影子很好看。 | 立体剪纸雕 |
| 宣传画颜料 | res.poster_paint | COMMON | paint, art | 海报剩的浆糊味和颜料味混在一起。 | 一罐白胶加颜料 |
| 社戏面具 | res.theater_mask | UNCOMMON | theater, art | 面具边缘被手汗磨得发亮。 | 彩绘戏曲面具 |

## 3.4 歌与口号（6）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 校歌歌词纸 | res.school_song_sheet | COMMON | song, music | 第三段副歌部分被很多人唱错。 | 油印校歌词纸 |
| 班级口号 | res.class_slogan | COMMON | class, wall | 横幅上的字，有一半后来被裁掉了。 | 手写红色横幅字 |
| 板报合影 | res.blackboard_photo | COMMON | photo, wall | 拍板报的照片，拍的人只拍到半块黑板。 | 教室黑板前一角 |
| 加油喇叭 | res.cheer_megaphone | UNCOMMON | sport, cheer | 举起来喊一声，整排人跟着吼。 | 单臂举起的扩音喇叭 |
| 鼓队鼓棒 | res.drum_stick | UNCOMMON | drum, sport | 橡胶头磨破了一个小口。 | 一对鼓棒 |
| 哨子 | res.referee_whistle | COMMON | sport, tool | 体育老师的哨子链子断了，用绳接着。 | 银色口哨带细绳 |

## 3.5 社团活动（6）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 社团传单 | res.club_flyer | COMMON | plaza, event | 广场角落落下的传单，写着某场社团活动。 | 双面折页传单 |
| 社团徽章 | res.club_pin | COMMON | club, pin | 搪瓷的，别针断了一根。 | 圆形搪瓷社徽 |
| 排练记录 | res.rehearsal_note | COMMON | rehearsal, note | 谱子背面写的站位图，箭头一直指向出口。 | 标满箭头的排练谱 |
| 社团旗 | res.scouting_flag | UNCOMMON | club, flag | 布褪色了，字还认得出。 | 褪色三角社旗 |
| 绶带 | res.badge_ribbon | COMMON | award, cloth | 颁奖用的缎带，一人一条。 | 金边绶带 |
| 纸飞机 | res.paper_plane | COMMON | craft, childhood | 从三楼走廊扔出去的，姿态其实很差。 | 折痕分明的纸飞机 |

## 3.6 日常民俗（7）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 宿舍挂件 | res.dorm_charm | COMMON | dorm, charm | 挂在床头的、褪色的小挂件。 | 褪色布艺小挂件 |
| 共用伞 | res.shared_umbrella | COMMON | umbrella, rain | 伞柄上贴满名字贴纸，有点挤。 | 贴满贴纸的长柄伞 |
| 棋盘残局 | res.chess_position | UNCOMMON | game, board | 下到一半人走了，黑白子还留着。 | 棋盘残局俯视 |
| 红绳结 | res.lucky_knot | COMMON | knot, wish | 系在树上或栏杆上，愿望都不外传。 | 中国结红绳 |
| 拍立得条 | res.photo_strip | UNCOMMON | photo, friend | 相纸连成一串，四个歪头的人。 | 连排拍立得相纸 |
| 手写贺卡 | res.hand_card | COMMON | card, festival | 封面画得很丑，里面认真。 | 涂鸦封面贺卡 |
| 钥匙扣 | res.keychain | COMMON | daily, charm | 挂在钥匙串上，磨得看不出原色。 | 磨旧的钥匙扣 |
| 传呼纸条 | res.pager_note | RARE | pager, memory | 座机响完会留一行数字，后来没人接了。 | 数字寻呼机纸条 |

---

# 四、记忆资源 MEMORY（28）

## 4.1 照片与影像（7）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 底片条 | res.negative_strip | RARE | photo, film | 一条十二格，多出来的那格是空的。 | 黑色胶片底片条 |
| 旧照片 | res.old_photo | UNCOMMON | photo, past | 背面写着日期，比人还老一点。 | 微黄泛银的旧照片 |
| 拍立得 | res.polaroid | UNCOMMON | photo, friend | 相纸偏色，人脸是橘的。 | 白框拍立得照片 |
| 集体合影 | res.group_photo | RARE | photo, class | 人很多，后排的人只露出眼睛。 | 百人大合影 |
| 教室快照 | res.classroom_snapshot | UNCOMMON | photo, classroom | 抓拍的，焦点在窗外的树。 | 模糊的教室抓拍 |
| 显影残液 | res.developer_residue | RARE | photo, chemistry | 旧暗房清掉的药水，盆底一层灰。 | 显影液残渣 |
| 暗房备忘 | res.darkroom_note | RARE | photo, note | 写给下一个人看的，字很急。 | 暗房手写备忘条 |

## 4.2 文字手记（7）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 手写日记 | res.diary_page | UNCOMMON | diary, writing | 只写了半页，最后一句没写完。 | 翻开的手写日记本 |
| 撕下的信纸 | res.torn_letter | UNCOMMON | letter, writing | 从信封里抽出来的那一张。 | 折叠的信纸 |
| 课表涂鸦 | res.timetable_scrawl | COMMON | note, doodle | 课程格子里画满了小人。 | 涂满小人的课表 |
| 贴纸集 | res.sticker_sheet | COMMON | sticker, note | 奖励贴纸，舍不得用。 | 一版彩色贴纸 |
| 票根 | res.stub_ticket | UNCOMMON | ticket, event | 半张票，撕口毛毛的。 | 撕半的旧票根 |
| 歌词草稿 | res.lyric_scratch | UNCOMMON | song, writing | 写在练习本最后一页，改了很多次。 | 涂改的歌词草稿 |
| 备忘纸条 | res.memo_slip | COMMON | memo, daily | "买电池、还书、打电话"，三行。 | 三行字的折角便条 |

## 4.3 声音（6）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 晚自习铃 | res.bell_sound | UNCOMMON | sound, bell | 一响，整层的椅子声一起响。 | 旧式铁皮铃声 |
| 雨打铁皮 | res.rain_on_roof | COMMON | sound, rain | 顶楼雨棚的声音，密而整齐。 | 雨点打在铁皮上 |
| 空教室回声 | res.classroom_echo | UNCOMMON | sound, echo | 关灯后拖一下椅子，回声会慢半拍。 | 空教室声波纹 |
| 广播电流音 | res.radio_static | COMMON | sound, radio | 早读前那段滋滋的调频声。 | 老式喇叭电流纹 |
| 走廊脚步 | res.corridor_steps | COMMON | sound, corridor | 有人跑过整条走廊，拖鞋声。 | 走廊地面脚步涟漪 |
| 远处施工 | res.construction_sound | COMMON | sound, city | 后山工地，传过来只剩闷响。 | 远处塔吊剪影 |

## 4.4 情绪残留（4）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 平静感 | res.calm_residue | RARE | mood, feeling | 某种"终于可以歇一会儿"的余味。 | 淡青色柔光圆片 |
| 心口发空 | res.hollowness | RARE | mood, feeling | 说不上难受，就是空。 | 空心淡影圆环 |
| 少年气 | res.youth_spark | RARE | mood, feeling | 想到要上台，手心是热的。 | 跳跃的小火苗色块 |
| 没说出口 | res.unspoken_regret | RARE | mood, feeling | 一句话在心里排练了很多遍。 | 空白对话气泡 |

## 4.5 地点痕迹（4）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 旧座位刻痕 | res.seat_carving | RARE | place, memory | 桌面上一道道刻痕，不知道是谁的。 | 木桌上一排刻痕 |
| 贴纸墙 | res.sticker_wall | UNCOMMON | place, wall | 楼梯拐角那面墙，被贴满又撕掉。 | 撕剩贴片的墙面 |
| 湖边石阶 | res.lake_steps | UNCOMMON | place, lake | 第三级缺了个角，坐着看水刚好。 | 缺角石阶与水面 |
| 跑道边缘 | res.track_edge | COMMON | place, sport | 塑胶跑道白线外侧，鞋底会粘起一层。 | 跑道白线与起跑线 |

---

# 五、异常资源 ANOMALY（24）

| 名称 | id | 稀有度 | tags | 描述 | 图标提示词 |
|------|-----|--------|------|------|-----------|
| 镜月鱼影 | res.mirror_moon_fish_shadow | ANOMALY | lake, night, rain, anomaly | 雨夜里湖面反光里多出来的那条鱼影。说不清是什么。 | 雨夜湖面多出的鱼影 |
| 霜纹 | res.frost_pattern | ANOMALY | winter, snow, anomaly | 雪天窗面上结出的花纹，形状像是被人画上去的。 | 窗玻璃上的规则霜纹 |
| 多出的影子 | res.extra_shadow | ANOMALY | shadow, anomaly | 下午的影子比人数多一个。 | 一串人影末尾多一个 |
| 多出的学号 | res.wrong_number | ANOMALY | id, anomaly | 名册最后一行的学号比今年多两位数。 | 花名册上多出的学号 |
| 打不开的门牌 | res.door_708 | ANOMALY | door, anomaly | 门牌是 708，可这栋楼只到 7 层。 | 走廊尽头的 708 门牌 |
| 重复的教室 | res.duplicate_room | ANOMALY | room, anomaly | 同一层的两间教室，桌椅摆得一模一样。 | 前后两个相同的教室 |
| 无名花 | res.unnamed_flower | ANOMALY | flower, anomaly | 花圃里多出来的一株，谁也叫不出名字。 | 无人认识的浅色花 |
| 手背上的表痕 | res.watch_mark | ANOMALY | body, anomaly | 手背上一圈印子，像戴过很多年的表。 | 手背上的圆形表痕 |
| 回声版的自己 | res.echo_self | ANOMALY | sound, anomaly | 关着门的空教室里，有人跟着你重复。 | 走廊里的双重人影 |
| 空座签到册 | res.empty_seat_roll | ANOMALY | record, anomaly | 点名册上有一个名字，从来没举手过。 | 有一行的点名册 |
| 倒流的雨 | res.reversed_rain | ANOMALY | rain, anomaly | 雨往上落，落在屋檐内侧。 | 向上飘的雨滴 |
| 失物处的自己 | res.lost_self | ANOMALY | lost, anomaly | 失物招领台上放着"你的东西"，拿起来是温的。 | 温热的小物件在台上 |
| 镜中发丝 | res.mirror_hair | ANOMALY | mirror, anomaly | 镜子里那缕头发，在你转身时还在。 | 镜面中一缕黑发 |
| 多亮的一盏灯 | res.extra_lamp | ANOMALY | light, anomaly | 空教室里最后一盏没关的灯，不在任何一间。 | 黑暗中独自亮的窗 |
| 录音里的陌生人 | res.stranger_voice | ANOMALY | sound, anomaly | 磁带尾端多出一段呼吸和一句"喂"。 | 老磁带尾端标签 |
| 多出的楼层 | res.extra_floor | ANOMALY | stairs, anomaly | 楼梯上的数字在三层和五层之间多了一个四。 | 楼梯扶手上的楼层数字 |
| 空白的下半身 | res.blank_lower_shadow | ANOMALY | shadow, anomaly | 影子从腰以下开始透明。 | 上半身实、下半身虚的影 |
| 会写字的笔 | res.writing_pen | ANOMALY | pen, anomaly | 笔帽没盖，第二天纸上多了半行字。 | 自己立在笔筒里的笔 |
| 课表空白格 | res.blank_slot | ANOMALY | schedule, anomaly | 课表有一格永远空着，但不占行数。 | 表格上一格空白 |
| 雨里的味道 | res.rain_smell | ANOMALY | smell, rain, anomaly | 下过雨的操场，有一种从没闻过的味道。 | 雨后空气中的异味涡旋 |
| 同一只鸟 | res.same_bird | ANOMALY | bird, anomaly | 数到第七次，还是那只鸟，同一个动作。 | 同一姿态的鸟重复 |
| 测不到底 | res.unbottomable_lake | ANOMALY | lake, depth, anomaly | 测深绳放下去，数字每次都不一样。 | 放不底的测深绳 |
| 找不到出处 | res.untraceable_memo | ANOMALY | memo, anomaly | 备忘录上写着任务，但没有人认领过。 | 字迹陌生的备忘录 |
| 慢一拍的手 | res.slow_wave_hand | ANOMALY | window, anomaly | 窗外有只手贴上来，比动作晚半拍。 | 窗外玻璃上贴着的掌印 |

---

# 六、制造资源 CRAFT（68）

## 6.1 基础加工（12）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 草绳 | res.rope | COMMON | rope, craft | 纤维和苇叶搓成的绳子，粗糙但结实。 | 20 | 盘起的一圈草绳 |
| 木牌 | res.wood_tag | UNCOMMON | wood, tag, craft | 系着草绳的小木牌，写点什么挂起来正好。 | 20 | 挂草绳的小木牌 |
| 松木板 | res.plank | COMMON | wood, craft | 锯得还算平，边上有点毛刺。 | 50 | 一块平整松木板 |
| 晒干药草 | res.dried_herb | COMMON | herb, craft | 挂起来阴干的，整间屋子都是味道。 | 30 | 倒挂晾干的草药束 |
| 压花 | res.pressed_flower | COMMON | flower, craft | 压平的野花，脉络清清楚楚。 | 40 | 压平的干花 |
| 染布 | res.dyed_fabric | COMMON | cloth, dye | 草木染的，颜色会慢慢掉。 | 20 | 一段扎染布 |
| 手抄纸 | res.paper_sheet | COMMON | paper, craft | 抄书用的纸，边缘还带毛。 | 40 | 微黄手抄纸一张 |
| 陶碗 | res.ceramic_bowl | UNCOMMON | pottery, craft | 拉坯歪了一点，但能装东西。 | 20 | 素烧陶碗 |
| 编绳结 | res.braided_ring | COMMON | knot, craft | 一小截多余的编绳，编得很紧。 | 30 | 编紧的绳结圈 |
| 粉笔 | res.chalk | COMMON | chalk, tool | 短得握不住的时候最顺手。 | 30 | 磨短的粉笔 |
| 蜡烛 | res.candle | COMMON | wax, light | 停电那晚全宿舍点它。 | 20 | 点着的小蜡烛 |
| 小玻璃灯 | res.glass_lamp | UNCOMMON | glass, lamp | 玻璃瓶做的灯，光是暖的。 | 10 | 瓶身小灯，暖光 |

## 6.2 零件元件（12）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 铜线 | res.copper_wire | COMMON | wire, craft | 从旧电器里拆出来的，剪不断。 | 30 | 卷起的一段铜线 |
| 铁钉 | res.iron_nail | COMMON | nail, tool | 一盒混着长短不一的钉子。 | 50 | 散落的旧铁钉 |
| 螺丝 | res.screw | COMMON | screw, part | 螺帽有时找不到，就先垫着。 | 50 | 一枚金属螺丝 |
| 小齿轮 | res.gear_small | UNCOMMON | gear, part | 机工房地上捡的，齿磨圆了一点。 | 20 | 金属小齿轮 |
| 拨动开关 | res.toggle_switch | UNCOMMON | switch, part | 咔哒声很脆，接缝要垫纸。 | 10 | 拨动式开关 |
| 小灯泡 | res.led_bulb | UNCOMMON | led, part | 亮起来有一点延迟。 | 20 | 小玻璃灯泡 |
| 电阻包 | res.resistor_pack | UNCOMMON | resistor, part | 色环看不懂，只能照抄。 | 20 | 一包色环电阻 |
| 小电机 | res.motor_tiny | RARE | motor, part | 拆下来的时候还转了一下。 | 10 | 微型直流电机 |
| 焊锡条 | res.solder_bar | UNCOMMON | solder, part | 机工房味道的来源。 | 10 | 银灰焊锡条 |
| 轴承 | res.bearing | UNCOMMON | bearing, part | 自行车上换下来的，滚得很顺。 | 10 | 金属滚珠轴承 |
| 铝壳 | res.alu_case | UNCOMMON | metal, part | 压平的易拉罐底，剪成小方块。 | 30 | 银色铝片 |
| 线束 | res.wire_bundle | UNCOMMON | wire, part | 按颜色分好的一小捆。 | 10 | 捆好的彩色线束 |

## 6.3 工具（8）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 小锤 | res.hand_hammer | UNCOMMON | hammer, tool | 木柄缠了胶布，手不容易滑。 | 1 | 木柄小铁锤 |
| 螺丝刀 | res.screwdriver | UNCOMMON | screwdriver, tool | 十字批，杆上有牙印。 | 1 | 红柄螺丝刀 |
| 修枝剪 | res.pruning_shears | UNCOMMON | shears, garden | 剪枝的春天人手一把。 | 1 | 弹簧修枝剪 |
| 钓竿 | res.fishing_rod | RARE | fishing, tool | 竿梢有点软，甩不远。 | 1 | 细长钓竿带线轮 |
| 放大镜 | res.magnifier | UNCOMMON | magnifier, lab | 看标本、看字、看不清的题。 | 1 | 圆框放大镜 |
| 铁夹子 | res.binder_clip | COMMON | clip, tool | 夹纸也夹零食袋。 | 30 | 黑色长尾夹 |
| 洒水壶 | res.watering_can | UNCOMMON | watering, garden | 壶嘴的莲蓬头是后来换的。 | 1 | 绿漆洒水壶 |
| 折梯 | res.folding_ladder | UNCOMMON | ladder, tool | 上旧体育馆屋顶用的。 | 1 | 半开的折梯 |

## 6.4 家具与装饰（12）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 木箱 | res.crate | COMMON | crate, furniture | 装设备的旧木箱，侧面有箭头。 | 10 | 带箭头标记的木箱 |
| 小桌 | res.small_table | UNCOMMON | table, furniture | 摆一张床边的桌子，高度刚好。 | 5 | 木质小方桌 |
| 折叠椅 | res.folding_chair | COMMON | chair, furniture | 搬宿舍时的老朋友。 | 5 | 折叠金属椅 |
| 纸灯笼 | res.paper_lantern | UNCOMMON | lantern, decor | 里面塞了暖光贴。 | 20 | 鼓起的纸灯笼 |
| 挂画 | res.framed_picture | UNCOMMON | decor, wall | 画的是谁的海，题目被裁掉了。 | 5 | 装框的风景挂画 |
| 地毯 | res.rug | UNCOMMON | rug, decor | 宿舍门口那块，颜色被洗淡了。 | 5 | 素色编织地毯 |
| 壁挂小架 | res.wall_shelf | UNCOMMON | shelf, decor | 挂钥匙和耳机的地方。 | 5 | 壁挂小木架 |
| 收纳盒 | res.storage_box | COMMON | box, storage | 标签全部褪色，靠猜。 | 10 | 带标签的收纳盒 |
| 门帘 | res.door_curtain | UNCOMMON | curtain, decor | 掀开前要先拨一下。 | 5 | 布质门帘 |
| 抱枕 | res.cushion | COMMON | cushion, decor | 洗过一次就回不去了。 | 10 | 方形抱枕 |
| 花瓶 | res.vase | UNCOMMON | vase, decor | 插什么都好看，插什么都不活。 | 10 | 细颈玻璃花瓶 |
| 串灯 | res.string_lights | UNCOMMON | light, decor | 宿舍阳台挂一排，晚上像星空。 | 5 | 暖色小串灯 |

## 6.5 服饰配件（6）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 围裙 | res.apron | COMMON | apron, work | 后厨和陶艺教室的通用款。 | 5 | 帆布围裙 |
| 草帽 | res.straw_hat | COMMON | hat, summer | 园艺课人手一顶，边沿断了再补。 | 5 | 编草帽 |
| 帆布包 | res.canvas_bag | COMMON | bag, cloth | 装书，也装别的。 | 5 | 印字帆布包 |
| 腕带 | res.wrist_band | COMMON | band, cloth | 运动会发的，一天就松了。 | 20 | 编织布腕带 |
| 珐琅徽章 | res.enamel_badge | UNCOMMON | badge, pin | 背面写着年份和一个小图案。 | 30 | 亮面珐琅徽章 |
| 手套 | res.gloves | COMMON | gloves, work | 冬天两只不一样暖。 | 5 | 厚织手套一双 |

## 6.6 药剂染色调味（8）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 果酱 | res.jam | COMMON | food, jam | 熬过头一次，第二次就好多了。 | 10 | 玻璃罐红果酱 |
| 糖浆 | res.syrup | COMMON | food, syrup | 淋在饼上会亮一下。 | 10 | 琥珀色糖浆瓶 |
| 茶饮 | res.tea_drink | COMMON | drink, tea | 保温杯里泡到第三遍正好。 | 20 | 保温杯泡的茶 |
| 墨水 | res.ink | COMMON | ink, craft | 钢笔用的，甩出去会溅一桌。 | 10 | 小玻璃墨水瓶 |
| 颜料液 | res.pigment | UNCOMMON | paint, color | 三种颜色调出的灰，比黑的暖。 | 20 | 三色颜料滴瓶 |
| 明胶 | res.gelatin | UNCOMMON | gelatin, craft | 温水里化开，再倒进模具。 | 20 | 半透明明胶片 |
| 香精油 | res.essential_oil | RARE | oil, scent | 三滴就够香一整晚。 | 10 | 深琥珀精油小瓶 |
| 肥料 | res.fertilizer | COMMON | farm, fertilizer | 味道大，效果稳。 | 20 | 颗粒状肥料袋 |

## 6.7 加工食品（10）

| 名称 | id | 稀有度 | tags | 描述 | 堆叠 | 图标提示词 |
|------|-----|--------|------|------|------|-----------|
| 果干 | res.dried_fruit | COMMON | food, dried | 挂在窗台上晒三天。 | 20 | 一串半透明果干 |
| 腌萝卜 | res.pickled_radish | COMMON | food, pickle | 脆得能听见响。 | 20 | 玻璃罐腌萝卜 |
| 麦芽糖 | res.maltose | UNCOMMON | food, syrup | 黏得能拉出丝。 | 10 | 金黄黏稠麦芽糖 |
| 豆腐 | res.tofu | COMMON | food, tofu | 食堂后厨凌晨四点开始点卤。 | 20 | 白色方块豆腐 |
| 煎蛋饼 | res.egg_pancake | COMMON | food, breakfast | 边缘焦脆，中间还软。 | 20 | 金黄煎蛋饼 |
| 茶叶蛋 | res.tea_egg | COMMON | food, snack | 卤到起花，壳上全是裂纹。 | 20 | 裂纹茶叶蛋 |
| 笋干 | res.dried_bamboo_shoot | COMMON | food, dried | 春天挖的，能存到冬天。 | 20 | 卷曲笋干片 |
| 米酒 | res.rice_wine | UNCOMMON | food, drink | 坛口那圈泡沫是活的。 | 10 | 陶坛米酒 |
| 坚果饼干 | res.nut_biscuit | COMMON | snack, baked | 烤箱时间总是差一分钟。 | 20 | 坚果曲奇饼干 |
| 糖渍果 | res.candied_fruit | UNCOMMON | snack, sweet | 咬开外面脆，里面还软。 | 20 | 糖霜腌渍水果 |

---

## 七、后续转 JSON 的约定

1. `id` 已全局去重，可直接作为主键；`removedIds` 目前为空。
2. `description` 直接取表格「描述」列原文，不做改写。
3. `category` / `rarity` / `tags` 按表格映射为小写枚举。
4. `stackLimit` 仅 CRAFT 类使用（表格「堆叠」列），其余类型省略该字段。
5. 写入前会补全 `name` 的中文标点校对与同一物品多来源说明（如 `sources` 扩展字段）。
