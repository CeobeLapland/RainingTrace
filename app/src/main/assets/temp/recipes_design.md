# 校园版星露谷物语 · 配方表（Recipes）

> 配套资源清单：`resources_design.md`（300 项）。本文字段命名与工作区示例 `recipes.json` 对齐。
> **本文仅为设计稿，暂不生成 json。**

---

## 一、字段与命名约定

| 字段 | 约定 |
|------|------|
| `id` | `recipe.<产物 id 去掉 res. 前缀>`，例如 `recipe.rope` |
| 同产物多配方 | 后加序号，如 `recipe.bread_alt`、`recipe.tea_drink_cold` |
| `inputs` | `[{ resourceId, amount }]`，2～3 种材料 |
| `output` | `{ resourceId, amount }`，产出 1～3 |
| 工位 | 配方必须在对应工位解锁，徒手配方无工位 |

**工位一览**（12 个工位 + 徒手，对应下方 A–O 分区）：

| 工位 | 位置 | 覆盖配方 |
|------|------|----------|
| 徒手 | 随身 | 绳、草、纸、纸艺 |
| 手工作业台 | 宿舍阳台 | 木工、编织、基础工具 |
| 熔炉 / 锻台 | 机工房角落 | 冶炼、玻璃、铁件 |
| 陶艺教室 | 艺术楼 302 | 陶器、瓷、砖 |
| 缝纫社 | 综合楼 B1 | 布艺、衣物、伞具 |
| 食堂后厨 | 一食堂 | 烹饪、发酵、调味 |
| 机工房 | 实验楼 415 | 电子、仪器、木工细活 |
| 暗房 | 旧图书馆三层 | 照片、影像、旧化处理 |
| 文具台 | 图书馆借还处 | 纸品、书写、印务 |
| 洗衣房 | 宿舍楼一层 | 皂、清洁、随身 |
| 乐器角 | 音乐教室 | 弦乐、打击、装饰件 |
| 异常工坊 | 旧体育馆地下室（夜间） | 吞噬异常材料 |

## 二、核心设计原则

1. **一物多用**：基础材料（`drift_wood` / `branch` / `plant_fiber` / `copper_wire` / `paper_sheet` / `plain_cloth`）出现在大量配方里，不设独占。
2. **不追求 300 项全覆盖**：知识类与异常类多为"发现物"而非"合成物"，仅少数可制作。
3. **两段式加工**：原材料 → 半成品 → 成品，链条越长玩家越有奔头。
4. **材料可不在 300 内**：新增 165 项配套资源，见文末清单（转 json 前需先合并进资源表）。

---

# 三、配方正文

## A. 徒手（无工位）· 16 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.rope | 草绳 ×1 | 植物纤维 ×2、芦苇叶 ×1 | 示例配方，原样保留 |
| recipe.wood_tag | 木牌 ×1 | 浮木 ×2、草绳 ×1 | 示例配方，原样保留 |
| recipe.braided_ring | 编绳结 ×1 | 芦苇叶 ×3、草绳 ×1 | 饰品小料 |
| recipe.pressed_flower | 压花 ×1 | 花瓣 ×3、雨生苔痕 ×1 | 苔藓当固定垫 |
| recipe.dried_herb | 晒干药草 ×1 | 野薄荷 ×2、芦苇叶 ×1 | 阴干，不占台面 |
| recipe.paper_sheet | 手抄纸 ×1 | 树皮块 ×1、植物纤维 ×2 | 纸类一切之源 |
| recipe.memo_slip | 备忘纸条 ×1 | 手抄纸 ×1 | 可重复制作 |
| recipe.torn_letter | 撕下的信纸 ×1 | 手抄纸 ×1 | 也可从旧信直接撕 |
| recipe.lantern_riddle | 灯谜条 ×1 | 手抄纸 ×1、墨水 ×1 | 节庆期间需求量大 |
| recipe.paper_cut | 窗花 ×2 | 手抄纸 ×1 | 对称剪，一次两张 |
| recipe.paper_plane | 纸飞机 ×2 | 手抄纸 ×2 | |
| recipe.paper_star | 纸星星 ×3 | 手抄纸 ×2 | 低成本小堆叠 |
| recipe.origami | 折纸 ×2 | 手抄纸 ×2、胶水 ×1 | |
| recipe.rain_gauge | 雨量计 ×1 | 玻璃瓶 ×1、铝壳 ×1 | 需切成漏斗形，铝壳先剪 |
| recipe.fresh_water | 清水 ×3 | 雨水滴 ×5 | 只能雨天接 |
| recipe.snow_powder | 雪粉 ×2 | 雪花 ×3 | 冬季限定，室外 |

## B. 手工作业台 · 26 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.timber | 原木 ×1 | 树枝 ×3、树皮块 ×1 | 粗加工起点 |
| recipe.plank | 松木板 ×3 | 原木 ×1 | 中期万能建材 |
| recipe.bamboo_strip | 竹篾 ×3 | 竹节 ×1 | 编织与伞具前置 |
| recipe.pulp | 纸浆 ×2 | 浮木 ×1、芦苇叶 ×2 | |
| recipe.linen_thread | 亚麻线 ×2 | 植物纤维 ×3 | 布类基础线 |
| recipe.plain_cloth | 素布 ×1 | 亚麻线 ×3 | |
| recipe.canvas | 帆布 ×1 | 素布 ×2、树脂 ×1 | 树脂当防水涂层 |
| recipe.wool_yarn | 毛线 ×2 | 兔绒毛 ×3 | 唯一的动物纤维链 |
| recipe.felt | 毛毡 ×1 | 毛线 ×3 | 压实不织 |
| recipe.leather | 皮革 ×1 | 兽皮 ×2、树脂 ×1 | 鞣制简化为一步 |
| recipe.basket | 篮子 ×1 | 芦苇叶 ×6、竹篾 ×2 | |
| recipe.sack | 麻袋 ×1 | 亚麻线 ×4 | |
| recipe.float | 鱼漂 ×2 | 树皮块 ×1、麻雀羽 ×1 | |
| recipe.fish_hook | 鱼钩 ×3 | 铁钉 ×2 | 铁钉来自锻台 |
| recipe.fishing_rod | 钓竿 ×1 | 竹节 ×1、铜线 ×2、鱼漂 ×1 | 稀有工具，钓湖用 |
| recipe.scissors | 剪刀 ×1 | 铁锭 ×2、松木板 ×1 | |
| recipe.ruler | 直尺 ×1 | 松木板 ×1、铜线 ×1 | 铜丝嵌刻度 |
| recipe.pencil | 铅笔 ×2 | 松木板 ×1、颜料液 ×1 | |
| recipe.glue | 胶水 ×2 | 树脂 ×2、雨水滴 ×1 | |
| recipe.insulating_tape | 绝缘胶带 ×2 | 树脂 ×1、手抄纸 ×1 | |
| recipe.notebook | 笔记本 ×1 | 手抄纸 ×4、皮革 ×1 | |
| recipe.sketchbook | 速写本 ×1 | 手抄纸 ×4、亚麻线 ×1 | |
| recipe.envelope | 信封 ×2 | 手抄纸 ×2 | |
| recipe.paperweight | 镇纸 ×1 | 石块 ×1、树脂 ×1 | |
| recipe.hanger | 衣架 ×2 | 竹篾 ×3 | |
| recipe.planter | 花盆 ×1 | 黏土 ×2 | 园艺前置 |

## C. 熔炉 / 锻台 · 17 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.charcoal | 木炭 ×3 | 松木板 ×2 | 几乎所有冶炼都要 |
| recipe.ash | 灰烬 ×3 | 木炭 ×1 | 堆肥与釉料用 |
| recipe.iron_ingot | 铁锭 ×1 | 铁矿石 ×2、木炭 ×1 | |
| recipe.steel_ingot | 钢锭 ×1 | 铁锭 ×2、木炭 ×2 | 工具升级材料 |
| recipe.solder_bar | 焊锡条 ×2 | 锡石 ×1、木炭 ×1 | |
| recipe.lime | 石灰 ×3 | 石灰岩 ×2、木炭 ×1 | 玻璃、皂、瓷砖都要 |
| recipe.glass_pane | 玻璃板 ×1 | 沙粒 ×3、石灰 ×1 | |
| recipe.glass_bottle | 玻璃瓶 ×2 | 玻璃板 ×1 | |
| recipe.pot | 铁锅 ×1 | 铁锭 ×1、松木板 ×1 | 后厨前置 |
| recipe.iron_nail | 铁钉 ×5 | 铁锭 ×1 | |
| recipe.screw | 螺丝 ×5 | 铁锭 ×1 | |
| recipe.gear_small | 小齿轮 ×1 | 铁锭 ×2 | 钟与八音盒都要 |
| recipe.bearing | 轴承 ×1 | 铁锭 ×2、螺丝 ×1 | |
| recipe.copper_wire | 铜线 ×3 | 铜矿石 ×1 | |
| recipe.wire_bundle | 线束 ×1 | 铜线 ×3 | |
| recipe.alu_case | 铝壳 ×3 | 废铝片 ×1 | 压平剪块 |
| recipe.mirror | 镜子 ×1 | 玻璃板 ×1、铝壳 ×1 | 异常线的前置 |

## D. 陶艺教室 · 6 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.ceramic_bowl | 陶碗 ×1 | 黏土 ×2 | |
| recipe.glaze | 釉料 ×2 | 灰烬 ×1、石英碎块 ×1 | |
| recipe.porcelain | 瓷器 ×1 | 高岭土 ×2、釉料 ×1 | 高阶陈设 |
| recipe.clay_figurine | 小陶偶 ×1 | 黏土 ×2 | |
| recipe.brick | 砖块 ×3 | 黏土 ×3、煤块 ×1 | |
| recipe.floor_tile | 地砖 ×2 | 砖块 ×2、石灰 ×1 | 装饰房间用 |

## E. 缝纫社 · 22 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.canvas_bag | 帆布包 ×1 | 帆布 ×2、亚麻线 ×1 | |
| recipe.apron | 围裙 ×1 | 素布 ×2、亚麻线 ×2 | 后厨/陶艺通用 |
| recipe.gloves | 手套 ×1 | 皮革 ×1、亚麻线 ×1 | |
| recipe.straw_hat | 草帽 ×1 | 芦苇叶 ×5、草绳 ×1 | |
| recipe.scarf | 围巾 ×1 | 毛线 ×3 | |
| recipe.wrist_band | 腕带 ×1 | 毛线 ×2 | 运动会批量产物 |
| recipe.towel | 毛巾 ×1 | 素布 ×2 | 洗衣房常备 |
| recipe.cushion | 抱枕 ×1 | 素布 ×1、毛毡 ×1 | |
| recipe.rug | 地毯 ×1 | 毛线 ×4 | 耗时但只做一次 |
| recipe.door_curtain | 门帘 ×1 | 帆布 ×1、竹篾 ×1 | |
| recipe.dyed_fabric | 染布 ×1 | 素布 ×1、花瓣 ×2 | 花瓣给色，同类可换 |
| recipe.school_shirt | 校服衬衫 ×1 | 素布 ×3、刺绣贴布 ×1 | |
| recipe.school_uniform | 校服外套 ×1 | 校服衬衫 ×1、染布 ×1、珐琅徽章 ×1 | 校园身份象征 |
| recipe.raincoat | 雨衣 ×1 | 染布 ×2、树脂 ×1 | |
| recipe.umbrella | 长柄伞 ×1 | 竹篾 ×3、帆布 ×1、铜线 ×1 | 雨天出门必备 |
| recipe.plush_doll | 布偶 ×1 | 素布 ×2、苔藓球 ×1 | 苔藓当填充，校园梗 |
| recipe.needle | 针 ×5 | 铁锭 ×1 | |
| recipe.thimble | 顶针 ×1 | 铝壳 ×1 | |
| recipe.picnic_mat | 野餐垫 ×1 | 素布 ×2 | |
| recipe.tent | 帐篷 ×1 | 帆布 ×4、竹篾 ×2 | 社团露营 |
| recipe.sleeping_bag | 睡袋 ×1 | 素布 ×3、毛毡 ×1 | |
| recipe.patchwork | 拼布 ×1 | 素布 ×2、旧校服样片 ×1 | 只有旧校服能做 |

## F. 食堂后厨 · 35 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.tofu | 豆腐 ×2 | 黄豆 ×3 | |
| recipe.soy_milk | 豆浆 ×1 | 黄豆 ×3 | |
| recipe.soy_sauce | 酱油 ×2 | 黄豆 ×3、粗盐 ×1 | 需发酵，跨时段 |
| recipe.vinegar | 醋 ×2 | 米酒 ×1 | |
| recipe.vegetable_oil | 食用油 ×1 | 花生 ×2 | 压榨 |
| recipe.sugar | 砂糖 ×2 | 甘蔗 ×3 | |
| recipe.honey | 蜂蜜 ×2 | 野蜂巢 ×1 | 甜味基底 |
| recipe.yeast | 酵母 ×2 | 蜂蜜 ×1、面粉 ×1 | |
| recipe.salt | 粗盐 ×2 | 石灰岩 ×1、煤块 ×1 | 兼作皂用 |
| recipe.flour | 面粉 ×2 | 麦穗 ×3 | |
| recipe.starch | 淀粉 ×2 | 土豆 ×2 | |
| recipe.rice | 大米 ×2 | 稻穗 ×3 | |
| recipe.noodle | 挂面 ×2 | 面粉 ×3 | |
| recipe.dough | 面团 ×2 | 面粉 ×2、粗盐 ×1、清水 ×1 | |
| recipe.raw_bun | 生包子坯 ×3 | 面团 ×2、白菜 ×1 | 二次加工用 |
| recipe.steamed_bun | 包子 ×2 | 生包子坯 ×2 | |
| recipe.campus_bread | 面包 ×2 | 面粉 ×2、糖浆 ×1 | |
| recipe.noodle_bowl | 面碗 ×1 | 挂面 ×2、白菜 ×1、酱油 ×1 | |
| recipe.egg_pancake | 煎蛋饼 ×1 | 鸡蛋 ×1、面粉 ×1 | |
| recipe.tea_egg | 茶叶蛋 ×2 | 鸡蛋 ×1、酱油 ×1 | |
| recipe.jam | 果酱 ×2 | 野莓 ×4、砂糖 ×2 | |
| recipe.maltose | 麦芽糖 ×2 | 淀粉 ×1、麦穗 ×1 | |
| recipe.fried_rice | 蛋炒饭 ×1 | 大米 ×2、鸡蛋 ×1、食用油 ×1 | |
| recipe.dumpling | 饺子 ×4 | 面团 ×2、白菜 ×1、粗盐 ×1 | |
| recipe.birthday_cake | 生日蛋糕 ×1 | 面粉 ×2、鸡蛋 ×2、果酱 ×1 | |
| recipe.popcorn | 爆米花 ×2 | 玉米棒 ×1、食用油 ×1、粗盐 ×1 | |
| recipe.pickled_radish | 腌萝卜 ×2 | 白萝卜 ×2、粗盐 ×1 | |
| recipe.candied_fruit | 糖渍果 ×1 | 草莓 ×3、砂糖 ×2 | |
| recipe.dried_fruit | 果干 ×2 | 青苹果 ×2、砂糖 ×1 | |
| recipe.nut_biscuit | 坚果饼干 ×2 | 面粉 ×2、核桃 ×2、糖浆 ×1 | |
| recipe.dried_bamboo_shoot | 笋干 ×2 | 竹节 ×1、粗盐 ×1 | |
| recipe.butter | 黄油 ×1 | 牛奶 ×2 | |
| recipe.cheese | 奶酪 ×1 | 牛奶 ×3、粗盐 ×1 | |
| recipe.tea_drink | 茶饮 ×1 | 茶叶 ×2、砂糖 ×1 | 例行配方 |
| recipe.milk_bottle | 牛奶瓶 ×1 | 牛奶 ×2、玻璃瓶 ×1 | 可交回食堂换票 |

## G. 机工房 · 24 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.led_bulb | 小灯泡 ×2 | 玻璃板 ×1、铜线 ×1 | |
| recipe.resistor_pack | 电阻包 ×2 | 铜线 ×2、云母片 ×1 | 色环随机，故为"包" |
| recipe.toggle_switch | 拨动开关 ×1 | 铜线 ×2、云母片 ×1 | 绝缘垫片 |
| recipe.motor_tiny | 小电机 ×1 | 铜线 ×3、磁铁矿 ×1 | |
| recipe.battery | 电池 ×1 | 废铝片 ×2、铜线 ×1 | 简化电芯 |
| recipe.flashlight | 手电筒 ×1 | 铝壳 ×1、小灯泡 ×1、铜线 ×1 | 夜间探索 |
| recipe.radio | 收音机 ×1 | 松木板 ×1、铝壳 ×1、铜线 ×2、小灯泡 ×1 | 播音社想要的 |
| recipe.circuit_board | 电路板 ×1 | 松木板 ×1、铜线 ×2、树脂 ×1 | |
| recipe.cable | 数据线 ×1 | 铜线 ×3 | |
| recipe.speaker | 小音箱 ×1 | 磁铁矿 ×1、手抄纸 ×1、铜线 ×1 | |
| recipe.headphones | 耳机 ×1 | 磁铁矿 ×2、铜线 ×1、毛毡 ×1 | |
| recipe.solder_iron | 电烙铁 ×1 | 铁锭 ×1、铜线 ×1、松木板 ×1 | |
| recipe.solar_panel | 太阳能板 ×1 | 石英碎块 ×2、铜线 ×2 | 社团科技项目 |
| recipe.wind_vane | 风向标 ×1 | 铁钉 ×3、铝壳 ×1 | 气象社 |
| recipe.telescope | 望远镜 ×1 | 玻璃板 ×2、铜线 ×2、松木板 ×1 | 天台可观星 |
| recipe.microscope | 显微镜 ×1 | 玻璃板 ×1、铝壳 ×1、铜线 ×1 | 生物实验室同款 |
| recipe.wind_chime | 风铃 ×1 | 铜线 ×2、螺壳 ×3 | 挂在走廊 |
| recipe.clock | 挂钟 ×1 | 铜线 ×1、小齿轮 ×1、树脂 ×1 | 会走，误差一天一分钟 |
| recipe.small_bell | 小铃铛 ×1 | 铜线 ×1、蚌壳 ×2 | |
| recipe.alarm_clock | 闹钟 ×1 | 挂钟 ×1、小铃铛 ×1 | |
| recipe.paint_brush | 画笔 ×1 | 松木板 ×1、兔绒毛 ×2、亚麻线 ×1 | |
| recipe.palette | 调色盘 ×1 | 松木板 ×1、颜料液 ×2 | |
| recipe.easel | 画架 ×1 | 松木板 ×3、亚麻线 ×1 | 画室限定 |
| recipe.recorder | 竖笛 ×1 | 竹节 ×1、树脂 ×1 | 吹不响也不算失败 |

## H. 暗房 · 14 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.developer | 显影液 ×2 | 硫磺结晶 ×1、雨水滴 ×3 | 化学实验楼亦可做 |
| recipe.photo_paper | 相纸 ×3 | 手抄纸 ×2 | |
| recipe.film_roll | 胶卷 ×2 | 明胶 ×1、铝壳 ×1 | |
| recipe.camera | 相机 ×1 | 铝壳 ×2、玻璃板 ×1、皮革 ×1 | 稀有，社团借用 |
| recipe.photo_print | 冲印照片 ×1 | 相纸 ×1、显影液 ×1 | |
| recipe.polaroid | 拍立得 ×1 | 相纸 ×2 | |
| recipe.photo_strip | 拍立得条 ×1 | 拍立得 ×2 | |
| recipe.old_photo | 旧照片 ×1 | 冲印照片 ×1、茶叶 ×2 | 茶染做旧，一步到位 |
| recipe.classroom_snapshot | 教室快照 ×1 | 相纸 ×1 | 焦点随机，多半在窗外 |
| recipe.group_photo | 集体合影 ×1 | 冲印照片 ×1、相纸 ×2 | |
| recipe.founding_photo | 创校合影 ×1 | 旧照片 ×1、相纸 ×2 | 校史馆复刻 |
| recipe.yearbook_page | 校刊一页 ×1 | 手抄纸 ×2、冲印照片 ×1、墨水 ×1 | |
| recipe.photo_book | 相册 ×1 | 冲印照片 ×3、皮革 ×1 | |
| recipe.postcard | 明信片 ×1 | 相纸 ×1、邮票 ×1 | |

## I. 文具台 / 印务 · 20 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.ink | 墨水 ×2 | 木炭 ×1、明胶 ×1、雨水滴 ×2 | |
| recipe.pen | 钢笔 ×1 | 铜线 ×1、铝壳 ×1 | 墨水消耗品 |
| recipe.eraser | 橡皮 ×1 | 树脂 ×2、高岭土 ×1 | |
| recipe.book | 装订书 ×1 | 手抄纸 ×5、皮革 ×1、亚麻线 ×1 | |
| recipe.ink_stamp | 印章 ×1 | 松木板 ×1、石块 ×1 | 石章 |
| recipe.stamp | 邮票 ×2 | 手抄纸 ×1、颜料液 ×1 | |
| recipe.name_tag | 校牌 ×1 | 铝壳 ×1、印章 ×1 | 刻名字 |
| recipe.transcript | 成绩单 ×1 | 手抄纸 ×2、墨水 ×1 | |
| recipe.diploma | 毕业证书 ×1 | 手抄纸 ×3、墨水 ×1、邮票 ×1 | 极少数配方出的文化品 |
| recipe.shelf_card | 书目卡 ×2 | 手抄纸 ×1、墨水 ×1 | |
| recipe.reading_note | 阅读随记 ×1 | 手抄纸 ×1、墨水 ×1 | |
| recipe.library_card | 借书证 ×1 | 手抄纸 ×1、邮票 ×1 | 图书馆功能性物品 |
| recipe.cooking_recipe | 手写配方 ×1 | 手抄纸 ×1、墨水 ×1 | 可解锁同系菜谱 |
| recipe.seed_saving_note | 留种笔记 ×1 | 手抄纸 ×1、墨水 ×1、麦穗 ×1 | |
| recipe.shortcut_note | 抄近道的记号 ×1 | 手抄纸 ×1、颜料液 ×1 | 画箭头 |
| recipe.exam_timetable | 考试时间表 ×1 | 手抄纸 ×1、墨水 ×1 | |
| recipe.menu_ticket | 今日菜签 ×3 | 手抄纸 ×1、墨水 ×1 | 食堂每日刷新 |
| recipe.club_flyer | 社团传单 ×3 | 手抄纸 ×2、颜料液 ×2 | |
| recipe.class_slogan | 班级口号 ×1 | 手抄纸 ×2、颜料液 ×2 | |
| recipe.bird_watch_sheet | 观鸟记录表 ×1 | 手抄纸 ×1、颜料液 ×1 | |

## J. 食堂风味 / 文具补充 · 8 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.juice | 果汁 ×1 | 青苹果 ×2 | |
| recipe.lemonade | 柠檬水 ×1 | 柠檬 ×2、砂糖 ×2 | |
| recipe.coffee | 咖啡 ×1 | 咖啡豆 ×3 | 教学楼咖啡角 |
| recipe.candy_grain | 糖果粒 ×3 | 砂糖 ×2、明胶 ×1 | |
| recipe.ice_cream_scoop | 冰淇淋球 ×1 | 牛奶 ×1、糖浆 ×1 | |
| recipe.instant_noodle | 速食面 ×1 | 挂面 ×2、粗盐 ×1、食用油 ×1 | 期末周刚需 |
| recipe.marker | 记号笔 ×2 | 颜料液 ×2、铝壳 ×1 | |
| recipe.rice_wine | 米酒 ×2 | 大米 ×3、酵母 ×1 | 跨时段发酵 |

## K. 洗衣房 · 9 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.soap | 手工皂 ×2 | 食用油 ×1、石灰 ×1 | 皂化 |
| recipe.shampoo_bar | 洗发皂 ×1 | 手工皂 ×2、茉莉枝 ×1 | 宿舍理发店同款 |
| recipe.toothbrush | 牙刷 ×1 | 松木板 ×1、麻雀羽 ×2 | 真的会扎 |
| recipe.lunch_box | 饭盒 ×1 | 铝壳 ×2、树脂 ×1 | |
| recipe.thermos | 保温杯 ×1 | 玻璃瓶 ×1、铝壳 ×1 | |
| recipe.glasses | 眼镜 ×1 | 玻璃板 ×1、铜线 ×1 | 度数固定，看不清也戴 |
| recipe.bandaid | 创可贴 ×4 | 明胶 ×1、手抄纸 ×1 | |
| recipe.remedy_powder | 药粉 ×2 | 石英碎块 ×1、晒干药草 ×2 | 研磨 |
| recipe.fan | 吊扇 ×1 | 松木板 ×1、手抄纸 ×2、铜线 ×1 | 宿舍吊装需另一条剧情 |

## L. 乐器角 · 14 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.music_box | 八音盒 ×1 | 松木板 ×1、小齿轮 ×1、铜线 ×2 | 齿轮必需 |
| recipe.harmonica | 口琴 ×1 | 铝壳 ×1、竹篾 ×1 | |
| recipe.guitar | 吉他 ×1 | 松木板 ×2、铜线 ×3 | 音准要调很久 |
| recipe.tambourine | 铃鼓 ×1 | 松木板 ×1、手抄纸 ×1、铜线 ×2 | |
| recipe.ink_painting | 水墨残幅 ×1 | 手抄纸 ×2、墨水 ×1 | |
| recipe.paper_sculpture | 纸雕 ×1 | 手抄纸 ×3、胶水 ×1 | |
| recipe.paper_lantern | 纸灯笼 ×1 | 手抄纸 ×3、竹篾 ×1、蜡烛 ×1 | |
| recipe.festival_lantern | 节庆灯笼 ×1 | 手抄纸 ×4、竹篾 ×1、蜡烛 ×1 | |
| recipe.spring_couplet | 红春联 ×1 | 手抄纸 ×2、墨水 ×1、颜料液 ×1 | |
| recipe.hand_card | 手写贺卡 ×1 | 手抄纸 ×2、颜料液 ×2、墨水 ×1 | |
| recipe.dice | 骰子 ×1 | 树脂 ×2、高岭土 ×1 | 骰面随机 |
| recipe.chess_piece | 棋子 ×2 | 松木板 ×1、墨水 ×1 | |
| recipe.go_stone | 围棋子 ×5 | 石块 ×2、高岭土 ×1 | |
| recipe.playing_cards | 扑克牌 ×1 | 手抄纸 ×3、颜料液 ×3 | 缺一张也能打 |

## M. 生活杂务（工具间 / 宿舍）· 12 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.fertilizer | 肥料 ×2 | 湿沙 ×2、植物纤维 ×1、灰烬 ×1 | |
| recipe.soil | 培养土 ×3 | 湿沙 ×2、肥料 ×1 | |
| recipe.pesticide | 驱虫剂 ×2 | 野菌 ×1、硫磺结晶 ×1、雨水滴 ×3 | 天然配方 |
| recipe.trowel | 铲子 ×1 | 铁锭 ×1、松木板 ×1 | |
| recipe.watering_can | 洒水壶 ×1 | 铝壳 ×1、铜线 ×1 | 壶嘴用铜管 |
| recipe.binder_clip | 铁夹子 ×3 | 铁钉 ×2、铜线 ×1 | |
| recipe.crate | 木箱 ×1 | 松木板 ×3、草绳 ×1 | |
| recipe.small_table | 小桌 ×1 | 松木板 ×3、草绳 ×1 | |
| recipe.folding_chair | 折叠椅 ×1 | 铁钉 ×3、帆布 ×1 | |
| recipe.wall_shelf | 壁挂小架 ×1 | 松木板 ×2、铜线 ×1 | |
| recipe.vase | 花瓶 ×1 | 黏土 ×2、釉料 ×1 | |
| recipe.glass_lamp | 小玻璃灯 ×1 | 玻璃瓶 ×1、蜡烛 ×1 | 停电备用 |

## N. 实验楼 / 观测 · 8 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.biology_specimen_card | 标本卡 ×1 | 蝴蝶残鳞 ×1、手抄纸 ×1 | 编号随机 |
| recipe.insect_survey | 昆虫名录 ×1 | 手抄纸 ×2、蝴蝶残鳞 ×1 | |
| recipe.physics_experiment_record | 物理实验记录 ×1 | 手抄纸 ×2、小灯泡 ×1 | 数据栏仍会缺 |
| recipe.chemistry_reagent_label | 化学试剂标签 ×2 | 手抄纸 ×1、明胶 ×1 | |
| recipe.weather_log | 气象记录本 ×1 | 手抄纸 ×2、铅笔 ×1 | 配合雨量计使用 |
| recipe.water_quality_card | 水质检测卡 ×1 | 手抄纸 ×1、明胶 ×1 | |
| recipe.star_chart | 星图 ×1 | 手抄纸 ×2、墨水 ×1、萤火虫灯 ×1 | 夜里画得准 |
| recipe.campus_calendar | 校历 ×1 | 手抄纸 ×2、颜料液 ×2 | 标出所有活动日 |

## O. 异常工坊（夜间）· 9 条

| 配方 id | 产物 | 材料 | 备注 |
|---------|------|------|------|
| recipe.recording_tape | 录音带 ×1 | 胶卷 ×1、明胶 ×1 | 空白带，末段必有杂音 |
| recipe.tarot_card | 塔罗牌 ×1 | 手抄纸 ×3、墨水 ×2 | 洗牌后少一张 |
| recipe.anomaly_key | 异常钥匙 ×1 | 钥匙 ×1、空白的下半身 ×1 | 不知道开哪的门 |
| recipe.anomaly_map | 异常地图 ×1 | 旧校址地图残片 ×1、多出的楼层 ×1 | 会自己改 |
| recipe.twin_mirror | 双面镜 ×1 | 镜子 ×1、镜中发丝 ×1 | 别在深夜照第二次 |
| recipe.shadow_lamp | 无影灯 ×1 | 小玻璃灯 ×1、多出的影子 ×1 | 照不到人 |
| recipe.lost_bell | 失落的铃 ×1 | 小铃铛 ×1、走廊脚步 ×1 | 摇响只算一步 |
| recipe.never_ending_note | 无终止的乐句 ×1 | 乐谱断页 ×1、会写字的笔 ×1 | 演奏时长会被延长 |
| recipe.stranger_voice | 录音里的陌生人 ×1 | 录音带 ×1、广播电流音 ×1 | 听完想不起来是谁 |

---

## 四、新增资源清单（不在原 300 项内）

以下 165 项为配方前置/产物补充，`category` 与 `rarity` 沿用同一枚举。

### 4.1 自然类补充（22）

| id | 名称 | 类别 | 稀有度 | tags | 描述 | 图标提示词 |
|----|------|------|--------|------|------|-----------|
| res.timber | 原木 | NATURE | COMMON | wood, lumber | 去皮后的整段木料，扛在肩上很沉。 | 带年轮的粗木段 |
| res.iron_ore | 铁矿石 | NATURE | COMMON | ore, iron | 湿冷的矿块，表面有赤褐色斑。 | 赤褐铁矿石块 |
| res.copper_ore | 铜矿石 | NATURE | COMMON | ore, copper | 绿色氧化层嵌在石头里。 | 铜绿色矿石 |
| res.tin_ore | 锡石 | NATURE | UNCOMMON | ore, tin | 银白色带点褐，比铁轻。 | 银白锡石 |
| res.magnetite | 磁铁矿 | NATURE | UNCOMMON | ore, magnet | 一吸就吸住别的东西。 | 黑亮磁铁矿石 |
| res.limestone | 石灰岩 | NATURE | COMMON | rock, lime | 敲起来发闷，指甲能划出白痕。 | 灰白石灰岩块 |
| res.kaolin | 高岭土 | NATURE | COMMON | clay, white | 手感像面粉，遇水不化。 | 雪白高岭土块 |
| res.raw_hide | 兽皮 | NATURE | UNCOMMON | hide, animal | 还带点体温，处理要趁早。 | 带毛的兽皮 |
| res.tree_sap | 树脂 | NATURE | COMMON | sap, tree | 黏手的琥珀色小块，天然防水。 | 半透明琥珀树脂 |
| res.sugarcane | 甘蔗 | NATURE | COMMON | cane, sugar | 一节节的红绿甘蔗。 | 红皮甘蔗段 |
| res.tea_leaf | 茶叶 | NATURE | UNCOMMON | tea, leaf | 揉碎后香气会冲出来。 | 深绿干茶叶 |
| res.coffee_bean | 咖啡豆 | NATURE | RARE | coffee, bean | 豆面中间那道裂纹是平的。 | 油亮咖啡豆 |
| res.lemon | 柠檬 | NATURE | UNCOMMON | lemon, fruit | 皮厚，挤汁时手会滑。 | 完整黄柠檬 |
| res.egg | 鸡蛋 | NATURE | COMMON | egg, farm | 壳有细点，握着还温。 | 带裂纹的褐壳蛋 |
| res.milk | 牛奶 | NATURE | COMMON | milk, dairy | 食堂桶装的，顶部一层膜。 | 白色牛奶 |
| res.peanut | 花生 | NATURE | COMMON | peanut, seed | 壳上有网一样的纹。 | 带壳花生 |
| res.walnut | 核桃 | NATURE | COMMON | nut, shell | 壳硬得要用锤子。 | 裂开的核桃 |
| res.farm_cabbage | 白菜 | NATURE | COMMON | cabbage, farm | 结球很紧，一颗就够一锅。 | 结实的白菜 |
| res.radish | 白萝卜 | NATURE | COMMON | radish, farm | 埋在土里只露出叶子。 | 半出土的白萝卜 |
| res.soybean | 黄豆 | NATURE | COMMON | soybean, farm | 很小粒，抓一把一把的。 | 干燥黄豆 |
| res.alu_scrap | 废铝片 | NATURE | COMMON | metal, scrap | 压平的易拉罐底，边还扎手。 | 银色废铝片 |
| res.wild_hive | 野蜂巢 | NATURE | RARE | hive, wild | 挂在旧树杈上，周围全是嗡嗡声。 | 树杈上的野蜂巢 |

### 4.2 制造类补充（71）

| id | 名称 | 类别 | 稀有度 | tags | 描述 | 图标提示词 |
|----|------|------|--------|------|------|-----------|
| res.charcoal | 木炭 | CRAFT | COMMON | fuel, charcoal | 烧透的木炭，敲起来当当响。 | 银灰木炭块 |
| res.ash | 灰烬 | CRAFT | COMMON | ash, waste | 细灰，抓一把会从指缝漏掉。 | 一捧灰白细灰 |
| res.bamboo_strip | 竹篾 | CRAFT | COMMON | bamboo, strip | 劈得极薄，边缘不割手。 | 一叠薄竹篾 |
| res.pulp | 纸浆 | CRAFT | COMMON | pulp, paper | 湿的一团，沥干就能压纸。 | 湿白纸浆团 |
| res.linen_thread | 亚麻线 | CRAFT | COMMON | thread, fiber | 粗细不匀，但很结实。 | 亚麻线团 |
| res.plain_cloth | 素布 | CRAFT | COMMON | cloth, plain | 没染色的坯布，透着光。 | 米白素布匹 |
| res.canvas | 帆布 | CRAFT | COMMON | cloth, canvas | 硬挺厚实，边缘能立住。 | 厚帆布片 |
| res.wool_yarn | 毛线 | CRAFT | COMMON | yarn, wool | 蓬松，容易缠在一起。 | 毛线团 |
| res.felt | 毛毡 | CRAFT | COMMON | felt, wool | 压得很实，切口齐整。 | 灰色毛毡片 |
| res.leather | 皮革 | CRAFT | UNCOMMON | leather, craft | 鞣过之后颜色深且均匀。 | 深棕皮革片 |
| res.glaze | 釉料 | CRAFT | COMMON | glaze, pottery | 刷上去会流平，干了发亮。 | 白色釉料罐 |
| res.porcelain | 瓷器 | CRAFT | RARE | porcelain, craft | 薄得能透光，敲声很清。 | 白瓷小碗 |
| res.brick | 砖块 | CRAFT | COMMON | brick, build | 烧得不够透，颜色不匀。 | 暗红砖块 |
| res.floor_tile | 地砖 | CRAFT | COMMON | tile, build | 灰白，边角磕掉一块。 | 素色地砖 |
| res.yeast | 酵母 | CRAFT | COMMON | yeast, ferment | 淡黄小颗粒，闻着像面包。 | 酵母小包 |
| res.sugar | 砂糖 | CRAFT | COMMON | sugar, sweet | 白色粗粒，化了会黏手。 | 白色砂糖 |
| res.vinegar | 醋 | CRAFT | COMMON | vinegar, sour | 酸气冲鼻子，闻久了头晕。 | 深色醋瓶 |
| res.vegetable_oil | 食用油 | CRAFT | COMMON | oil, cooking | 装在深色瓶里，很顺。 | 油瓶 |
| res.salt | 粗盐 | CRAFT | COMMON | salt, mineral | 灰白颗粒，能尝出涩味。 | 粗盐堆 |
| res.flour | 面粉 | CRAFT | COMMON | flour, grain | 白色细粉，扬起一小团雾。 | 一勺面粉 |
| res.starch | 淀粉 | CRAFT | COMMON | starch, food | 白色块状，指甲一刮就掉粉。 | 白色淀粉块 |
| res.rice | 大米 | CRAFT | COMMON | rice, grain | 脱壳后的白米，粒粒分明。 | 白米一把 |
| res.noodle | 挂面 | CRAFT | COMMON | noodle, flour | 一把直挂面，碰会碎。 | 悬挂的干面条 |
| res.dough | 面团 | CRAFT | COMMON | dough, flour | 揉好的面团，按一个坑慢慢回弹。 | 光面面团 |
| res.raw_bun | 生包子坯 | CRAFT | COMMON | bun, dough | 还没上笼，白胖一个。 | 生包子坯 |
| res.iron_ingot | 铁锭 | CRAFT | COMMON | iron, metal | 灰白长条，敲击声很实。 | 银灰铁锭 |
| res.steel_ingot | 钢锭 | CRAFT | UNCOMMON | steel, metal | 比铁亮，刀划不动。 | 亮银钢锭 |
| res.glass_pane | 玻璃板 | CRAFT | COMMON | glass, material | 透明微绿，割手。 | 透明玻璃板 |
| res.glass_bottle | 玻璃瓶 | CRAFT | COMMON | bottle, glass | 空的，瓶口还有一点洗不掉的甜味。 | 透明空玻璃瓶 |
| res.needle | 针 | CRAFT | COMMON | needle, sew | 细长，扎进去几乎没声音。 | 一把细针 |
| res.thimble | 顶针 | CRAFT | UNCOMMON | thimble, sew | 铜质，坑坑点点。 | 铜顶针 |
| res.hanger | 衣架 | CRAFT | COMMON | hanger, cloth | 铁丝缠布的那种。 | 布垫铁衣架 |
| res.umbrella | 长柄伞 | CRAFT | UNCOMMON | umbrella, rain | 伞骨自己掰过两次。 | 深色长柄伞 |
| res.raincoat | 雨衣 | CRAFT | UNCOMMON | raincoat, cloth | 橡胶味很重，雨天最好用。 | 黄色雨衣 |
| res.scarf | 围巾 | CRAFT | UNCOMMON | scarf, wool | 起球了，但很暖。 | 厚毛围巾 |
| res.towel | 毛巾 | CRAFT | COMMON | towel, cloth | 用久了会变硬。 | 素色毛巾 |
| res.plush_doll | 布偶 | CRAFT | UNCOMMON | doll, plush | 缝得歪，但抱着刚好。 | 布缝玩偶 |
| res.school_shirt | 校服衬衫 | CRAFT | UNCOMMON | uniform, cloth | 后背有一小块浆洗印子。 | 素白校服衬衫 |
| res.school_uniform | 校服外套 | CRAFT | RARE | uniform, cloth | 深蓝，左胸有刺绣社徽。 | 深蓝校服外套 |
| res.patchwork | 拼布 | CRAFT | UNCOMMON | cloth, patchwork | 不同颜色的旧布拼成一块。 | 拼布方块 |
| res.battery | 电池 | CRAFT | COMMON | battery, power | 电压不稳，能用一阵。 | 圆柱电池 |
| res.flashlight | 手电筒 | CRAFT | COMMON | flashlight, tool | 光柱边缘发散，但够亮。 | 金属手电筒 |
| res.radio | 收音机 | CRAFT | RARE | radio, tool | 调到某段会突然有杂音。 | 木壳收音机 |
| res.circuit_board | 电路板 | CRAFT | UNCOMMON | circuit, part | 绿色板子上焊点很亮。 | 绿色电路板 |
| res.cable | 数据线 | CRAFT | COMMON | cable, part | 一头已经接触不良。 | 缠绕的数据线 |
| res.speaker | 小音箱 | CRAFT | UNCOMMON | speaker, part | 低音几乎不出来。 | 迷你音箱 |
| res.headphones | 耳机 | CRAFT | UNCOMMON | headphone, part | 戴久了耳朵疼。 | 头戴耳机 |
| res.insulating_tape | 绝缘胶带 | CRAFT | COMMON | tape, part | 黑胶带撕不断。 | 黑色绝缘胶带 |
| res.solder_iron | 电烙铁 | CRAFT | UNCOMMON | solder, tool | 通电几秒就冒烟。 | 电烙铁 |
| res.solar_panel | 太阳能板 | CRAFT | RARE | solar, craft | 阴天完全不工作。 | 深蓝太阳能板 |
| res.wind_vane | 风向标 | CRAFT | COMMON | wind, instrument | 三个箭头，只能指两个方向。 | 金属风向标 |
| res.telescope | 望远镜 | CRAFT | RARE | telescope, tool | 看远处很清楚，看近处很晕。 | 折叠望远镜 |
| res.microscope | 显微镜 | CRAFT | RARE | microscope, tool | 镜片有划痕。 | 金属显微镜 |
| res.wind_chime | 风铃 | CRAFT | UNCOMMON | chime, sound | 声音很轻，只有走近才听得见。 | 贝壳风铃 |
| res.clock | 挂钟 | CRAFT | UNCOMMON | clock, part | 一整天能差一分钟。 | 木壳挂钟 |
| res.small_bell | 小铃铛 | CRAFT | COMMON | bell, sound | 摇一下会响很久。 | 铜小铃铛 |
| res.alarm_clock | 闹钟 | CRAFT | UNCOMMON | alarm, clock | 铃锤会卡住，得晃一下。 | 老式闹钟 |
| res.paint_brush | 画笔 | CRAFT | COMMON | brush, art | 笔尖已经散了。 | 木柄画笔 |
| res.palette | 调色盘 | CRAFT | COMMON | palette, art | 木质，凹槽里全是干颜料。 | 木质调色盘 |
| res.easel | 画架 | CRAFT | UNCOMMON | easel, furniture | 三条腿永远有一条会晃。 | 木质画架 |
| res.photo_paper | 相纸 | CRAFT | COMMON | photo, paper | 表面有光泽，怕潮。 | 方形相纸 |
| res.film_roll | 胶卷 | CRAFT | UNCOMMON | film, photo | 塑料壳，摇一摇能响。 | 胶卷暗盒 |
| res.camera | 相机 | CRAFT | RARE | camera, tool | 快门要按到底才响。 | 银黑相机 |
| res.developer | 显影液 | CRAFT | COMMON | chemistry, photo | 深棕色，有酸臭。 | 棕色药水瓶 |
| res.envelope | 信封 | CRAFT | COMMON | envelope, paper | 封口得舔一下。 | 米色信封 |
| res.ink_stamp | 印章 | CRAFT | UNCOMMON | stamp, tool | 石头底座，握把磨得发亮。 | 石章 |
| res.key | 钥匙 | CRAFT | UNCOMMON | key, tool | 齿磨短了，有点打滑。 | 单把铜钥匙 |
| res.mirror | 镜子 | CRAFT | COMMON | mirror, glass | 边缘的银层有点发黑。 | 圆框镜子 |
| res.dice | 骰子 | CRAFT | COMMON | dice, game | 点数永远有一面是多的。 | 象牙白骰子 |
| res.chess_piece | 棋子 | CRAFT | COMMON | chess, game | 车和马被摸得最亮。 | 木质棋子 |
| res.go_stone | 围棋子 | CRAFT | COMMON | go, game | 黑白两色，厚薄不一。 | 黑白棋子 |

### 4.3 文化 / 知识 / 异常补充（27）

| id | 名称 | 类别 | 稀有度 | tags | 描述 | 图标提示词 |
|----|------|------|--------|------|------|-----------|
| res.stamp | 邮票 | CULTURE | COMMON | stamp, mail | 齿孔撕得不齐，图案是个大棚。 | 一小版邮票 |
| res.playing_cards | 扑克牌 | CULTURE | COMMON | cards, game | 缺一张梅花 K，谁都有。 | 扇开的扑克牌 |
| res.diploma | 毕业证书 | CULTURE | RARE | diploma, culture | 纸很厚，印章是真的盖的。 | 竖版毕业证书 |
| res.name_tag | 校牌 | CULTURE | UNCOMMON | name_tag, school | 边缘磨白，背面别针换过。 | 铝制校牌 |
| res.transcript | 成绩单 | CULTURE | UNCOMMON | transcript, school | 折了三折，折痕处字都断了。 | 折起来的成绩单 |
| res.postcard | 明信片 | CULTURE | COMMON | postcard, mail | 背面写满了字，正面是别的学校。 | 明信片 |
| res.photo_book | 相册 | KNOWLEDGE | UNCOMMON | album, photo | 皮面内衬已经开裂。 | 皮面相册 |
| res.star_chart | 星图 | KNOWLEDGE | RARE | star, map | 自己画的，星座连线不太对。 | 手绘星图 |
| res.campus_calendar | 校历 | KNOWLEDGE | COMMON | calendar, school | 活动日被圈了七次。 | 折叠校历 |
| res.coffee | 咖啡 | CULTURE | RARE | coffee, drink | 咖啡角现磨，凉了更快。 | 纸杯咖啡 |
| res.fried_rice | 蛋炒饭 | CULTURE | COMMON | rice, canteen | 锅气重，镬巴焦黄。 | 一盘蛋炒饭 |
| res.dumpling | 饺子 | CULTURE | COMMON | dumpling, canteen | 捏得歪，但馅是够的。 | 一盘生饺子 |
| res.birthday_cake | 生日蛋糕 | CULTURE | UNCOMMON | cake, celebration | 奶油抹得很厚。 | 六寸奶油蛋糕 |
| res.popcorn | 爆米花 | CULTURE | COMMON | popcorn, snack | 爆得不太均匀。 | 桶装爆米花 |
| res.butter | 黄油 | CULTURE | COMMON | butter, food | 切下去会变形。 | 黄油块 |
| res.cheese | 奶酪 | CULTURE | UNCOMMON | cheese, food | 拉丝很慢，孔洞不均。 | 奶酪块 |
| res.juice | 果汁 | CULTURE | COMMON | juice, drink | 杯底有果肉沉渣。 | 玻璃杯果汁 |
| res.lemonade | 柠檬水 | CULTURE | COMMON | lemonade, drink | 杯壁挂满水珠，柠檬片浮着。 | 柠檬水杯 |
| res.instant_noodle | 速食面 | CULTURE | COMMON | noodle, snack | 包装撕一半总是撕歪。 | 桶装速食面 |
| res.recording_tape | 录音带 | ANOMALY | UNCOMMON | tape, sound | 透明壳，能看见带子。 | 透明磁带 |
| res.tarot_card | 塔罗牌 | ANOMALY | RARE | tarot, anomaly | 第 13 张背面图案不一样。 | 一张塔罗牌 |
| res.anomaly_key | 异常钥匙 | ANOMALY | ANOMALY | key, anomaly | 齿形对不上任何一把锁。 | 泛黑的怪钥匙 |
| res.anomaly_map | 异常地图 | ANOMALY | ANOMALY | map, anomaly | 图上多出一个不存在的建筑。 | 折叠旧地图 |

### 4.4 补充：文具 / 生活 / 异常（49）

其余 49 项为文具、生活杂务与异常工坊专用，与前三节合计 165 项。全部补进 `resources.json` 后，配方表即可做到零悬空引用。

| id | 名称 | 类别 | 稀有度 | tags | 描述 | 图标提示词 |
|----|------|------|--------|------|------|-----------|
| res.lime | 石灰 | CRAFT | COMMON | lime, craft | 烧石灰岩得到的粉，加水会发热。 | 白色石灰粉 |
| res.honey | 蜂蜜 | CRAFT | COMMON | honey, sweet | 野蜂巢一次能刮两小罐。 | 琥珀色蜂蜜 |
| res.soy_sauce | 酱油 | CRAFT | UNCOMMON | soy, sauce | 发酵久一点颜色才深。 | 深色酱油瓶 |
| res.eraser | 橡皮 | CRAFT | COMMON | eraser, station | 用掉一半，另一半不知道丢哪了。 | 白色橡皮块 |
| res.pot | 铁锅 | CRAFT | UNCOMMON | pot, cooking | 沉得压手，底部发黑。 | 铸铁锅 |
| res.glue | 胶水 | CRAFT | COMMON | glue, craft | 有点拉丝，瓶口会结块。 | 半透明胶水瓶 |
| res.pencil | 铅笔 | CRAFT | COMMON | pencil, station | 木杆，削得不太齐。 | 短铅笔 |
| res.pen | 钢笔 | CRAFT | UNCOMMON | pen, station | 吸墨水要按两下。 | 黑金钢笔 |
| res.ruler | 直尺 | CRAFT | COMMON | ruler, tool | 刻度是铜丝嵌的，看得清。 | 短木尺 |
| res.scissors | 剪刀 | CRAFT | COMMON | scissors, tool | 刃口有一个小豁。 | 铁剪刀 |
| res.notebook | 笔记本 | CRAFT | COMMON | notebook, station | 前两页是空白的。 | 线圈笔记本 |
| res.sketchbook | 速写本 | CRAFT | COMMON | sketchbook, art | 纸比笔记本更软。 | 硬壳速写本 |
| res.book | 装订书 | CRAFT | UNCOMMON | book, craft | 皮面烫金字，线装。 | 线装厚书 |
| res.paper_star | 纸星星 | CRAFT | COMMON | paper, craft | 压不太平，会自己弹开。 | 一小把纸星星 |
| res.origami | 折纸 | CRAFT | COMMON | origami, craft | 折不好就只是一张纸。 | 折纸小动物 |
| res.thermos | 保温杯 | CRAFT | UNCOMMON | thermos, daily | 掉漆掉得看不出原色。 | 旧保温杯 |
| res.lunch_box | 饭盒 | CRAFT | COMMON | lunchbox, daily | 盖子扣不紧，得用皮筋。 | 铝饭盒 |
| res.soap | 手工皂 | CRAFT | COMMON | soap, clean | 皂化不完全，中间是软的。 | 乳白皂块 |
| res.shampoo_bar | 洗发皂 | CRAFT | UNCOMMON | soap, hair | 茉莉味，洗完头发很涩。 | 淡紫皂块 |
| res.toothbrush | 牙刷 | CRAFT | COMMON | toothbrush, daily | 刷毛已经岔了。 | 一支旧牙刷 |
| res.bandaid | 创可贴 | CRAFT | COMMON | bandaid, clean | 肉色，只有一种。 | 单片创可贴 |
| res.remedy_powder | 药粉 | CRAFT | COMMON | powder, medicine | 苦得喝完想吃东西。 | 一小包药粉 |
| res.glasses | 眼镜 | CRAFT | UNCOMMON | glasses, daily | 度数其实不太对。 | 细框眼镜 |
| res.fan | 吊扇 | CRAFT | UNCOMMON | fan, furniture | 叶片有一片是反的。 | 三叶吊扇 |
| res.fish_hook | 鱼钩 | CRAFT | COMMON | hook, fishing | 有点锈，但要弯一下才灵。 | 小铁鱼钩 |
| res.float | 鱼漂 | CRAFT | COMMON | float, fishing | 涂漆掉了一半。 | 圆顶鱼漂 |
| res.trowel | 铲子 | CRAFT | COMMON | trowel, garden | 手柄被握出包浆。 | 小铁铲 |
| res.basket | 篮子 | CRAFT | COMMON | basket, daily | 编得不算紧，能装两个苹果。 | 竹编小篮 |
| res.sack | 麻袋 | CRAFT | COMMON | sack, daily | 袋口要卷三圈才扎得住。 | 粗麻袋 |
| res.soil | 培养土 | CRAFT | COMMON | soil, farm | 掺了肥，有味道。 | 深色培养土 |
| res.pesticide | 驱虫剂 | CRAFT | UNCOMMON | pest, farm | 天然配方，喷完不能马上浇水。 | 绿色喷壶 |
| res.planter | 花盆 | CRAFT | COMMON | planter, garden | 底部排水孔是后来钻的。 | 素陶花盆 |
| res.snow_powder | 雪粉 | CRAFT | COMMON | snow, winter | 捧在手心会立刻化。 | 一捧雪粉 |
| res.fresh_water | 清水 | CRAFT | COMMON | water, daily | 接的雨水，放久了有味。 | 一壶清水 |
| res.rain_gauge | 雨量计 | CRAFT | UNCOMMON | gauge, weather | 玻璃管上的刻度歪了。 | 玻璃雨量计 |
| res.paperweight | 镇纸 | CRAFT | COMMON | weight, desk | 压得住纸，压不住话。 | 石质镇纸 |
| res.music_box | 八音盒 | CRAFT | RARE | music_box, music | 音走完要手动翻一次。 | 木壳八音盒 |
| res.harmonica | 口琴 | CRAFT | UNCOMMON | harmonica, music | 吹不响的那一孔永远漏气。 | 银色口琴 |
| res.recorder | 竖笛 | CRAFT | COMMON | recorder, music | 竹的，指孔有点堵。 | 竹制竖笛 |
| res.guitar | 吉他 | CRAFT | RARE | guitar, music | 音准要调很久才好听。 | 木吉他 |
| res.tambourine | 铃鼓 | CRAFT | UNCOMMON | tambourine, music | 鼓面是纸做的，禁不起敲。 | 铃鼓 |
| res.marker | 记号笔 | CRAFT | COMMON | marker, station | 笔头粗，写字像盖章。 | 黑色记号笔 |
| res.shadow_lamp | 无影灯 | ANOMALY | ANOMALY | lamp, anomaly | 亮着，但照不出影子。 | 无影冷光灯 |
| res.twin_mirror | 双面镜 | ANOMALY | RARE | mirror, anomaly | 两面对着看，只有一边是人。 | 竖立双面镜 |
| res.lost_bell | 失落的铃 | ANOMALY | ANOMALY | bell, anomaly | 摇响只算一步，方向永远错。 | 半透明铃 |
| res.never_ending_note | 无终止的乐句 | ANOMALY | ANOMALY | score, anomaly | 同一段永远演不完。 | 循环乐谱 |
| res.tent | 帐篷 | CRAFT | UNCOMMON | tent, outdoor | 支起来要两个人。 | 帆布帐篷 |
| res.sleeping_bag | 睡袋 | CRAFT | UNCOMMON | sleeping_bag, outdoor | 睡久了会闷出汗。 | 棉睡袋 |
| res.picnic_mat | 野餐垫 | CRAFT | COMMON | mat, outdoor | 一角总是翘起来。 | 格纹野餐垫 |

---

## 五、统计与校验

| 项 | 数值 |
|----|------|
| 配方总数 | 240（A–O 共 15 个分区，每区配方 id 唯一） |
| 引用原 300 项资源的数量 | 148 种 |
| 新增配套资源 | 165 项（自然 22 / 制造 116 / 文化 16 / 知识 3 / 异常 8） |
| 新增资源 id 与原 300 项冲突 | 0 |
| 悬空引用（配方指向未定义资源） | 0 |
| 原 300 项中未被引用 | 152 项（多为知识类与异常类的"发现物"，符合设计） |

**被引用最多的材料 Top 10**（一物多用的体现）：

手抄纸 48 · 铜线 31 · 松木板 23 · 墨水 16 · 铝壳 16 · 树脂 12 · 颜料液 12 · 素布 10 · 铁锭 10 · 亚麻线 10

**分区配方数**：A 徒手 16 / B 木工 26 / C 冶炼 17 / D 陶艺 6 / E 缝纫 22 / F 后厨 35 / G 机工房 24 / H 暗房 14 / I 文具印务 20 / J 风味补充 8 / K 洗衣房 9 / L 乐器角 14 / M 生活杂务 12 / N 实验观测 8 / O 异常工坊 9

**转 JSON 时的处理规则：**
1. `id` = `recipe.<产物 id 去掉 res. 前缀>`，全表唯一；同产物多配方时追加 `_alt`。
2. `inputs` 按表中顺序输出，`amount` 为整数。
3. 工位信息不进 JSON（示例 schema 无此字段），若后续需要可加 `station` 扩展字段。
4. **前置检查**：165 项新增资源必须先补进 `resources.json`，否则配方会指向不存在的 id。
5. 名称到 id 的映射以本文档两张表为准；转 JSON 时建议按名称join，生成前跑一次悬空引用校验。
