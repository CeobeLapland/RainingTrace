# -*- coding: utf-8 -*-
"""把 resources_design.md / recipes_design.md 转成 resources.json / recipes.json。

一次性转换脚本。运行前会自动备份示例 json，转换后校验 id 唯一性与引用完整性。
"""
import io, json, re, collections, os, shutil, time

BASE = os.path.dirname(os.path.abspath(__file__))
RES_MD = os.path.join(BASE, 'resources_design.md')
REC_MD = os.path.join(BASE, 'recipes_design.md')
RES_JSON = os.path.join(BASE, 'resources.json')
REC_JSON = os.path.join(BASE, 'recipes.json')


def read(path):
    return io.open(path, encoding='utf-8').read()


def split_row(line):
    return [x.strip().replace('**', '') for x in line.split('|')[1:-1]]


# ---------- 备份示例 json ----------
stamp = time.strftime('%Y%m%d_%H%M%S')
for p in (RES_JSON, REC_JSON):
    if os.path.exists(p):
        shutil.copy2(p, '%s.bak_%s' % (p, stamp))
        print('已备份 %s -> %s.bak_%s' % (os.path.basename(p), os.path.basename(p), stamp))

# ---------- 1. 解析 resources_design.md ----------
res_lines = read(RES_MD).split('\n')
CAT_OF = {
    'NATURE': 'NATURE',
    'KNOWLEDGE': 'KNOWLEDGE',
    'CULTURE': 'CULTURE',
    'MEMORY': 'MEMORY',
    'ANOMALY': 'ANOMALY',
    'CRAFT': 'CRAFT',
}

main_entries = []
cur_cat = None
for line in res_lines:
    # 第七节「配套新增」由 1b 单独解析，此处必须跳过，否则会重复收录且列数不同
    if re.match(r'^## 七、配套新增', line):
        break
    m = re.match(r'^# [一二三四五六]、.*?\b(NATURE|KNOWLEDGE|CULTURE|MEMORY|ANOMALY|CRAFT)\b（\d+）\s*$', line)
    if m:
        cur_cat = CAT_OF[m.group(1)]
        continue
    if not line.startswith('|') or ' res.' not in line:
        continue
    c = split_row(line)
    if cur_cat == 'CRAFT':
        name, rid, rarity, tags, desc, stack = c[0], c[1], c[2], c[3], c[4], int(c[5])
    else:
        name, rid, rarity, tags, desc, stack = c[0], c[1], c[2], c[3], c[4], None
    main_entries.append({
        'id': rid, 'name': name, 'category': cur_cat, 'rarity': rarity,
        'tags': [t.strip() for t in tags.split(',') if t.strip()],
        'description': desc, 'stackLimit': stack,
    })

# ---------- 1b. 解析 resources_design.md 第七节「配套新增（地点表引入）」 ----------
# 该节标题为 "## 七、配套新增（... · 41 项）"，分类写在 "### 7.x NATURE 类（13）" 小标题里。
place_new_entries = []
cur_cat = None
in_sec7 = False
for line in res_lines:
    if re.match(r'^## 七、配套新增', line):
        in_sec7 = True
        continue
    if in_sec7 and re.match(r'^## ', line):
        break
    if not in_sec7:
        continue
    m = re.match(r'^### 7\.\d+\s+.*?\b(NATURE|KNOWLEDGE|CULTURE|MEMORY|ANOMALY|CRAFT)\b', line)
    if m:
        cur_cat = CAT_OF[m.group(1)]
        continue
    # 表头与分隔行跳过：资源行的第 2 列以 res. 开头
    if not re.match(r'^\|[^|]+\|\s*res\.[a-z_0-9]+\s*\|', line):
        continue
    if cur_cat is None:
        raise SystemExit('配套新增节缺少分类上下文: %r' % line)
    c = split_row(line)
    if len(c) != 7:
        raise SystemExit('配套新增资源行列数异常: %r' % line)
    name, rid, rarity, tags, desc, stack_s, _prompt = c
    # stackLimit 列：仅 CRAFT 类有值，其余类写 "-" 占位
    stack = None
    if cur_cat == 'CRAFT':
        stack = int(stack_s)
    elif stack_s.strip() != '-':
        raise SystemExit('非 CRAFT 类不应带 stackLimit: %s' % rid)
    place_new_entries.append({
        'id': rid, 'name': name, 'category': cur_cat, 'rarity': rarity,
        'tags': [t.strip() for t in tags.split(',') if t.strip()],
        'description': desc, 'stackLimit': stack,
    })

# ---------- 2. 解析 recipes_design.md 附录里的新增资源 ----------
rec_lines = read(REC_MD).split('\n')
new_entries = []
for line in rec_lines:
    if not re.match(r'^\| res\.[a-z_0-9]+ \|', line):
        continue
    c = split_row(line)
    if len(c) != 7:
        raise SystemExit('新增资源行列数异常: %r' % line)
    rid, name, cat, rarity, tags, desc, _prompt = c
    # 附录未标注堆叠：工具/家具/乐器类给 1，其余制造类给 20
    stack = None
    if cat == 'CRAFT':
        toolish = ('tool' in tags or 'furniture' in tags or 'instrument' in tags
                   or 'station' in tags)
        stack = 1 if toolish else 20
    new_entries.append({
        'id': rid, 'name': name, 'category': cat, 'rarity': rarity,
        'tags': [t.strip() for t in tags.split(',') if t.strip()],
        'description': desc, 'stackLimit': stack,
    })

# ---------- 3. 名称 -> id 反查表 ----------
name2id = {}
for e in main_entries + new_entries + place_new_entries:
    if e['name'] in name2id and name2id[e['name']] != e['id']:
        raise SystemExit('名称重名: %s' % e['name'])
    name2id[e['name']] = e['id']

# ---------- 4. 保留示例 json 中 md 未收录的条目 ----------
sample = json.loads(read(RES_JSON))
kept = []
kept_stack = []
for e in sample['entries']:
    if e['id'] in {x['id'] for x in main_entries + new_entries + place_new_entries}:
        continue
    kept.append(e)
    name2id.setdefault(e['name'], e['id'])
    kept_stack.append(e.get('stackLimit'))

# ---------- 5. 组装 resources.json ----------
all_res = main_entries + new_entries + place_new_entries
for e, st in zip(kept, kept_stack):
    all_res.append({
        'id': e['id'], 'name': e['name'], 'category': e['category'],
        'rarity': e['rarity'], 'tags': e['tags'],
        'description': e['description'], 'stackLimit': st,
    })
dup = [k for k, v in collections.Counter(e['id'] for e in all_res).items() if v > 1]
if dup:
    raise SystemExit('资源 id 重复: %s' % dup)

CAT_ORDER = {'NATURE': 0, 'KNOWLEDGE': 1, 'CULTURE': 2, 'MEMORY': 3, 'ANOMALY': 4, 'CRAFT': 5}
RAR_ORDER = {'COMMON': 0, 'UNCOMMON': 1, 'RARE': 2, 'ANOMALY': 3}
all_res.sort(key=lambda e: (CAT_ORDER[e['category']], RAR_ORDER.get(e['rarity'], 9), e['id']))

res_doc = {'schemaVersion': 1, 'removedIds': [], 'entries': []}
for e in all_res:
    item = {
        'id': e['id'],
        'name': e['name'],
        'category': e['category'],
        'rarity': e['rarity'],
        'tags': e['tags'],
        'description': e['description'],
    }
    if e['stackLimit'] is not None:
        item['stackLimit'] = e['stackLimit']
    res_doc['entries'].append(item)

# ---------- 6. 解析配方 ----------
recipes = []
for line in rec_lines:
    if not line.startswith('| recipe.'):
        continue
    c = split_row(line)
    rid, out_cell, in_cell = c[0], c[1], c[2]
    m = re.match(r'^(.*?)\s*×\s*(\d+)$', out_cell)
    if not m:
        raise SystemExit('产物格解析失败: %r' % out_cell)
    out_name, out_amount = m.group(1), int(m.group(2))
    inputs = []
    for part in in_cell.split('、'):
        part = part.strip()
        if not part:
            continue
        m = re.match(r'^(.*?)\s*×\s*(\d+)$', part)
        if not m:
            raise SystemExit('材料格解析失败: %r' % part)
        nm, amt = m.group(1), int(m.group(2))
        if nm not in name2id:
            raise SystemExit('材料未定义: %s（配方 %s）' % (nm, rid))
        inputs.append({'resourceId': name2id[nm], 'amount': amt})
    if out_name not in name2id:
        raise SystemExit('产物未定义: %s（配方 %s）' % (out_name, rid))
    recipes.append({
        'id': rid,
        'inputs': inputs,
        'output': {'resourceId': name2id[out_name], 'amount': out_amount},
    })

rdup = [k for k, v in collections.Counter(r['id'] for r in recipes).items() if v > 1]
if rdup:
    raise SystemExit('配方 id 重复: %s' % rdup)

rec_doc = {'schemaVersion': 1, 'removedIds': [], 'entries': recipes}

# ---------- 7. 引用完整性校验 ----------
res_ids = {e['id'] for e in res_doc['entries']}
dangling = set()
for r in recipes:
    for i in r['inputs']:
        if i['resourceId'] not in res_ids:
            dangling.add(i['resourceId'])
    if r['output']['resourceId'] not in res_ids:
        dangling.add(r['output']['resourceId'])
if dangling:
    raise SystemExit('悬空引用: %s' % sorted(dangling))

# ---------- 8. 写文件 ----------
for path, doc in ((RES_JSON, res_doc), (REC_JSON, rec_doc)):
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(json.dumps(doc, ensure_ascii=False, indent=2))
        f.write('\n')

# ---------- 9. 报告 ----------
print()
print('资源 resources.json：主表 %d + 新增 %d + 示例保留 %d = %d 条' % (
    len(main_entries), len(new_entries), len(kept), len(res_doc['entries'])))
print('  唯一 id %d ｜ 类别 %s' % (
    len({e['id'] for e in res_doc['entries']}),
    dict(collections.Counter(e['category'] for e in res_doc['entries']))))
print('  稀有度 %s' % dict(collections.Counter(e['rarity'] for e in res_doc['entries'])))
print('  含 stackLimit %d 条' % sum(1 for e in res_doc['entries'] if 'stackLimit' in e))
print('配方 recipes.json：%d 条 ｜ 唯一 id %d ｜ 悬空引用 无' % (
    len(recipes), len({r['id'] for r in recipes})))
print('  示例保留条目：%s' % [e['id'] for e in kept])
