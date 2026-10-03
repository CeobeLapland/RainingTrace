# -*- coding: utf-8 -*-
"""把 npcs_design.md / places_design.md 转成 npcs.json / places.json。

用法：python convert_content_to_json.py
可重复运行；运行前自动把已有 json 备份为 .bak_<时间戳>。
"""
import json
import os
import re
import shutil
from collections import Counter
from datetime import datetime

BASE = os.path.dirname(os.path.abspath(__file__))
NPC_MD = os.path.join(BASE, 'npcs_design.md')
NPC_JSON = os.path.join(BASE, 'npcs.json')
PLC_MD = os.path.join(BASE, 'places_design.md')
PLC_JSON = os.path.join(BASE, 'places.json')


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def split_row(line):
    return [c.strip() for c in line.strip().strip('|').split('|')]


def backup(path):
    if not os.path.exists(path):
        return
    stamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    dst = '%s.bak_%s' % (path, stamp)
    shutil.copy2(path, dst)
    print('已备份 %s -> %s' % (os.path.basename(path), os.path.basename(dst)))


# ============================================================
# 补充数据：oneLiner（示例 npcs.json 有此字段，设计稿未单列）
# 风格参照示例：第三人称、一句、有画面感、≤ 24 字
# ============================================================
ONELINER = {
    'npc.bit.lin': '总在图书馆靠窗那排的人，桌上永远摊着两本书。',
    'npc.bit.zhou': '每天绕广场跑两圈，然后去食堂帮忙收餐盘。',
    'npc.bit.xu': '在湖边支着画架，一坐就是一下午。',
    'npc.bit.he': '习惯很晚还在外面走，说夜里安静。',
    'npc.bit.qi': '总背着布袋在果林里转，说是在看果子熟没��。',
    'npc.bit.shen_yan': '修东西的时候会跟机器说话，修完就走。',
    'npc.bit.su_li': '图书馆四楼最靠里的座位，一坐一整天。',
    'npc.bit.chen_qiu': '戴白手套取照片的人，说话很慢，从不打断别人。',
    'npc.bit.gao_jian': '一开口就是在分配任务，听的人才发现自己已经答应了。',
    'npc.bit.bai_yi': '改过一百多件外套，理由都是"袖子太长了"。',
    'npc.bit.luo_xiaoman': '社团只有她一个人，但她坚持按期排练。',
    'npc.bit.jiang_chengyi': '蹲在苗边记编号，问的问题追着人跑不掉。',
    'npc.bit.wei_he': '夜班保温杯永远是满的，见人会先转过身子。',
    'npc.bit.miao_xiaodie': '同一棵树她拍了一年，编号排到了三百多。',
    'npc.bit.ouyang_rang': '能把"现在几点"答成"数据还没稳定"。',
    'npc.bit.tang_tang': '做出来的东西永远先分给旁边的人。',
    'npc.bit.jiang_yu': '先拆对方的前提，再回答问题，从不提高音量。',
    'npc.bit.a_wu': '球场上永远最后一个走，答应的事一定做到。',
    'npc.bit.wen_ning': '只帮你把话说完，从不给建议。',
    'npc.bit.gu_qing': '声音给全校听，人却很少被谁看见。',
    'npc.bit.liao_yi': '自习室第三个座位，刻着字，搬了三次还在。',
    'npc.bit.mi_ya': '把不会的词写在袖子上，录音记下每天听到的声音。',
    'npc.bit.luo_shifu': '工具车上有把永远借不出去的扳手。',
    'npc.bit.jiang_xiaoyu': '能一动不动站四十分钟，等一只鸟落下来。',
    'npc.bit.zhang_shu': '修车收费随心情，说话时一直看着你的鞋。',
    'npc.bit.chen_jiu': '旧体育馆地下室的看夜人，锁门是"两响半"。',
    'npc.bit.san_geng': '只在雨夜出现的乌鸦，会用人的语气说话。',
    'npc.bit.shen_mo': '只在真正下雨时出现，伞总偏向别人那侧。',
    'npc.bit.xia_xing': '出现在 2008 年那张合照里，但没人记得她。',
    'npc.bit.lou_qi': '失物招领处的东西都记得回家的路，她也是。',
}

# 性格中文 -> traits 英文枚举（枚举定义见 npcs_design.md 第一节）
TRAIT_MAP = [
    ('观察力强', 'OBSERVANT'), ('观察', 'OBSERVANT'),
    ('寡言', 'TACITURN'), ('守时', 'PUNCTUAL'), ('温和', 'WARM'),
    ('爱聊', 'TALKATIVE'), ('稳', 'STEADY'), ('发呆', 'DREAMY'),
    ('疏离', 'RESERVED'), ('好奇', 'CURIOUS'), ('务实', 'PRACTICAL'),
    ('认真', 'SERIOUS'), ('沉得住气', 'STEADY'), ('沉得住', 'STEADY'),
    ('怯场', 'SHY'), ('嘴碎', 'CHATTY'), ('慢性子', 'GENTLE'),
    ('讲究', 'MANNERED'), ('讲义气', 'GENEROUS'), ('锋利', 'SHARP'),
    ('不歇', 'TIRELESS'), ('挑剔', 'EXACTING'), ('明亮', 'BRIGHT'),
    ('不起眼', 'PLAIN'), ('忘我', 'ABSORBED'), ('倔', 'STUBBORN'),
    ('会听', 'LISTENER'), ('神秘', 'MYSTERIOUS'), ('慢热', 'GENTLE'),
]

# 总表里的 favoriteTopic（英文，直接取值更可靠）
FAVORITE = {
    'npc.bit.lin': 'BOOKS', 'npc.bit.zhou': 'RUNNING', 'npc.bit.xu': 'ART',
    'npc.bit.he': 'NIGHT', 'npc.bit.qi': 'PLANTS', 'npc.bit.shen_yan': 'TECH',
    'npc.bit.su_li': 'BOOKS', 'npc.bit.chen_qiu': 'MEMORY', 'npc.bit.gao_jian': 'PEOPLE',
    'npc.bit.bai_yi': 'SEWING', 'npc.bit.luo_xiaoman': 'SOUND', 'npc.bit.jiang_chengyi': 'PLANTS',
    'npc.bit.wei_he': 'PEOPLE', 'npc.bit.miao_xiaodie': 'PHOTO', 'npc.bit.ouyang_rang': 'TECH',
    'npc.bit.tang_tang': 'FOOD', 'npc.bit.jiang_yu': 'LAW', 'npc.bit.a_wu': 'SPORT',
    'npc.bit.wen_ning': 'PEOPLE', 'npc.bit.gu_qing': 'SOUND', 'npc.bit.liao_yi': 'STUDY',
    'npc.bit.mi_ya': 'FOOD', 'npc.bit.luo_shifu': 'MONEY', 'npc.bit.jiang_xiaoyu': 'PLANTS',
    'npc.bit.zhang_shu': 'PLANTS', 'npc.bit.chen_jiu': 'NIGHT', 'npc.bit.san_geng': 'NIGHT',
    'npc.bit.shen_mo': 'RAIN', 'npc.bit.xia_xing': 'MEMORY', 'npc.bit.lou_qi': 'MEMORY',
}


def to_traits(text):
    """把中文性格描述转成英文 traits 列表。取首个句号前的分项。"""
    head = re.split(r'[。；]', text)[0]
    out = []
    for zh, en in TRAIT_MAP:
        if zh in head and en not in out:
            out.append(en)
    return out


def to_topics(text):
    """从「爱好 / 话题」行取括号内的英文枚举。"""
    m = re.search(r'[（(]([^）)]*[A-Z][^）)]*)[）)]', text)
    if not m:
        return []
    return [t.strip() for t in re.split(r'[/,、]', m.group(1)) if t.strip()]


# ============================================================
# 1. 解析 npcs_design.md —— 角色详设（姓名/身份/背景）
# ============================================================
npc_text = read(NPC_MD)
detail = re.search(r'## 四、角色详设[\s\S]*?(?=\n---\n\n## 五、)', npc_text).group(0)
sections = re.split(r'\n### \d+\. ', detail)[1:]

profiles = {}
for sec in sections:
    head = re.match(r'(\S+)\s*·\s*(npc\.bit\.[a-z_0-9]+)', sec)
    if not head:
        raise SystemExit('角色标题解析失败: %r' % sec[:60])
    name, rid = head.group(1), head.group(2)

    role = re.search(r'^- \*\*身份\*\*：(.+)$', sec, re.M).group(1).strip()
    pers = re.search(r'^- \*\*性格\*\*：(.+)$', sec, re.M).group(1).strip()
    hobby = re.search(r'^- \*\*爱好 / 话题\*\*：(.+)$', sec, re.M).group(1).strip()

    # 背景：有序列表 1. 2. 3.
    bg_block = re.search(r'^- \*\*背景\*\*：\n((?:  \d+\. .+\n?)+)', sec, re.M).group(1)
    backstory = [re.sub(r'^\s*\d+\.\s*', '', l).strip() for l in bg_block.strip().split('\n')]

    profiles[rid] = {
        'name': name,
        'role': role,
        'traits': to_traits(pers),
        'topics': to_topics(hobby),
        'favoriteTopic': FAVORITE.get(rid, ''),
        'backstory': backstory,
    }

# ============================================================
# 2. 解析礼物偏好（第八节表格）
# ============================================================
gift_sec = re.search(r'## 八、礼物偏好 giftPreferences[\s\S]*?(?=\n---\n\n## 九、)', npc_text).group(0)
gifts = {}
for line in gift_sec.split('\n'):
    m = re.match(r'^\| (\d+) \| (\S+) \| (.+?) \| (.+?) \|$', line)
    if not m:
        continue
    liked = re.findall(r'`(res\.[a-z_0-9]+)`', m.group(3))
    disliked = re.findall(r'`(res\.[a-z_0-9]+)`', m.group(4))
    if not liked:
        continue
    # 姓名反查 id
    rid = next((k for k, v in profiles.items() if v['name'] == m.group(2)), None)
    if rid is None:
        raise SystemExit('礼物表找不到角色: %s' % m.group(2))
    gifts[rid] = {'liked': liked, 'disliked': disliked}

# ============================================================
# 3. 解析作息（第九节 json 代码块）
# ============================================================
sched_sec = re.search(r'## 九、作息 schedule[\s\S]*?(?=\n---\n\n## 十、)', npc_text).group(0)
blocks = re.findall(r'```json\n(.*?)```', sched_sec, re.S)
schedules = []
for i, b in enumerate(blocks):
    # 代码块前紧邻的 "### N. 名字" 决定归属
    idx = sched_sec.find(b)
    before = sched_sec[:idx]
    names = re.findall(r'^### \d+\. (\S+)', before, re.M)
    if not names:
        raise SystemExit('作息块 %d 找不到角色名' % (i + 1))
    nm = names[-1]
    rid = next((k for k, v in profiles.items() if v['name'] == nm), None)
    if rid is None:
        raise SystemExit('作息块角色不在详设中: %s' % nm)
    schedules.append((rid, json.loads(b)))

# ============================================================
# 4. 组装 npcs.json
# ============================================================
for rid in profiles:
    if rid not in ONELINER:
        raise SystemExit('缺少 oneLiner: %s' % rid)
    if rid not in gifts:
        raise SystemExit('缺少礼物偏好: %s' % rid)
    if rid not in dict(schedules):
        raise SystemExit('缺少作息: %s' % rid)

npc_entries = []
for rid, p in profiles.items():
    e = {
        'id': rid,
        'name': p['name'],
        'oneLiner': ONELINER[rid],
        'role': p['role'],
        'traits': p['traits'],
        'topics': p['topics'],
        'favoriteTopic': p['favoriteTopic'],
        'backstory': p['backstory'],
        'giftPreferences': gifts[rid],
        'schedule': dict(schedules)[rid],
    }
    npc_entries.append(e)

# 校验
errs = []
dup = [k for k, v in Counter(e['id'] for e in npc_entries).items() if v > 1]
if dup:
    errs.append('id 重复: %s' % dup)
dup = [k for k, v in Counter(e['name'] for e in npc_entries).items() if v > 1]
if dup:
    errs.append('姓名重名: %s' % dup)
for e in npc_entries:
    if not e['traits']:
        errs.append('%s traits 为空' % e['id'])
    if not e['topics']:
        errs.append('%s topics 为空' % e['id'])
    if e['favoriteTopic'] not in e['topics']:
        errs.append('%s favoriteTopic 不在 topics 内' % e['id'])
    if not e['backstory']:
        errs.append('%s backstory 为空' % e['id'])
    for s in e['schedule']:
        if not (0 <= s['startMinute'] <= 1440):
            errs.append('%s startMinute 越界: %s' % (e['id'], s['startMinute']))
if errs:
    raise SystemExit('校验失败:\n  ' + '\n  '.join(errs))

npc_doc = {'schemaVersion': 1, 'removedIds': [], 'entries': npc_entries}
backup(NPC_JSON)
with open(NPC_JSON, 'w', encoding='utf-8') as f:
    json.dump(npc_doc, f, ensure_ascii=False, indent=2)

# ============================================================
# 5. 解析 places_design.md
# ============================================================
plc_text = read(PLC_MD)
plc_body = re.search(r'## 四、分区详设[\s\S]*?(?=\n---\n\n## 五、)', plc_text).group(0)

plc_entries = []
seen = set()
for line in plc_body.split('\n'):
    if not re.match(r'^\| `place\.bit\.[a-z_0-9]+` \|', line):
        continue
    c = split_row(line)
    if len(c) != 8:
        raise SystemExit('地点行列数异常: %r' % line)
    rid, name, ptype, actions, desc, _npc, _res, _prompt = c
    rid = rid.strip('`')  # 表格里 id / type 带反引号，json 里不带
    ptype = ptype.strip('`')
    if rid in seen:
        raise SystemExit('地点 id 重复: %s' % rid)
    seen.add(rid)
    plc_entries.append({
        'id': rid,
        'name': name,
        'type': ptype,
        'lat': 0,
        'lng': 0,
        'actions': [a.strip() for a in actions.split('/') if a.strip()],
        'description': desc,
    })

# 5.3 增补的纯场景点（列数不同：id/name/type/desc）
extra_sec = re.search(r'### 5\.3 增补[\s\S]*?(?=\n---\n)', plc_text).group(0)
for line in extra_sec.split('\n'):
    if not re.match(r'^\| `place\.bit\.[a-z_0-9]+` \|', line):
        continue
    c = split_row(line)
    if len(c) != 4:
        raise SystemExit('增补行列数异常: %r' % line)
    rid, name, ptype, desc = c
    rid = rid.strip('`')
    ptype = ptype.strip('`')
    if rid in seen:
        raise SystemExit('地点 id 重复: %s' % rid)
    seen.add(rid)
    plc_entries.append({
        'id': rid,
        'name': name,
        'type': ptype,
        'lat': 0,
        'lng': 0,
        'actions': ['OBSERVE'],
        'description': desc,
    })

perr = []
dup = [k for k, v in Counter(e['name'] for e in plc_entries).items() if v > 1]
if dup:
    perr.append('地点名称重名: %s' % dup)
if not all(0 <= e['lat'] <= 90 and 0 <= e['lng'] <= 180 for e in plc_entries):
    perr.append('lat/lng 非法')
if not all(e['actions'] for e in plc_entries):
    perr.append('存在 actions 为空的地点')
if perr:
    raise SystemExit('地点校验失败:\n  ' + '\n  '.join(perr))

plc_doc = {'schemaVersion': 1, 'removedIds': [], 'entries': plc_entries}
backup(PLC_JSON)
with open(PLC_JSON, 'w', encoding='utf-8') as f:
    json.dump(plc_doc, f, ensure_ascii=False, indent=2)

# ============================================================
# 6. 跨表引用校验
# ============================================================
place_ids = {e['id'] for e in plc_entries}
res_ids = {e['id'] for e in json.loads(read(os.path.join(BASE, 'resources.json')))['entries']}

missing = sorted({s['placeId'] for e in npc_entries for s in e['schedule']} - place_ids)
if missing:
    raise SystemExit('人物作息引用了不存在的地点: %s' % missing)
bad_gift = sorted({g for e in npc_entries for g in
                   e['giftPreferences']['liked'] + e['giftPreferences']['disliked']} - res_ids)
if bad_gift:
    raise SystemExit('礼物引用了不存在的资源: %s' % bad_gift)

# ============================================================
# 7. 报告
# ============================================================
print('')
print('人物 npcs.json：%d 条' % len(npc_entries))
print('  唯一 id %d ｜ 姓名唯一 %d' % (
    len({e["id"] for e in npc_entries}), len({e["name"] for e in npc_entries})))
print('  traits 分布 %s' % dict(Counter(t for e in npc_entries for t in e['traits']).most_common(8)))
print('  作息段总数 %d ｜ 平均每人 %.1f 段' % (
    sum(len(e['schedule']) for e in npc_entries),
    sum(len(e['schedule']) for e in npc_entries) / len(npc_entries)))
print('  礼物 liked %d / disliked %d ｜ 全部资源引用有效' % (
    sum(len(e['giftPreferences']['liked']) for e in npc_entries),
    sum(len(e['giftPreferences']['disliked']) for e in npc_entries)))
print('')
print('地点 places.json：%d 条' % len(plc_entries))
print('  唯一 id %d ｜ 名称唯一 %d' % (
    len({e["id"] for e in plc_entries}), len({e["name"] for e in plc_entries})))
print('  type 分布 %s' % dict(Counter(e['type'] for e in plc_entries).most_common()))
print('  唯一 placeId %d ｜ 人物作息引用全部命中' % len(place_ids))
print('')
print('跨表校验通过：人物 -> 地点 零悬空；人物 -> 资源 零悬空')
