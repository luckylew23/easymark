# -*- coding: utf-8 -*-
p = 'README.md'
s = open(p, encoding='utf-8').read()

old = "| `易码_2.2.0beta3_优化版_v8.apk` | 原版优化 v8（v7 + 修复空白新建仍保存、重名后缀累积），见 GitHub Release |"
new = old + "\n| `易码_2.2.0beta3_优化版_v9.apk` | 原版优化 v9（v8 + 默认命名秒级时间戳 yyyy-MM-dd-HH-mm-ss），见 GitHub Release |"
assert old in s; s = s.replace(old, new)

old = "| **v8** | 2026-09-13 | 修复：①空白新建不再保存；②重名后缀 (1)(2)(3) 正确递增 | ✅ 已交付 |"
new = old + "\n| **v9** | 2026-09-13 | 默认命名秒级时间戳 `yyyy-MM-dd-HH-mm-ss`（24h） | ✅ 已交付 |"
assert old in s; s = s.replace(old, new)

old = "---\n\n## 经验沉淀"
new = '''---

## v9：默认命名秒级时间戳（2026-09-13）

**需求**：新建笔记默认命名统一为 **`yyyy-MM-dd-HH-mm-ss`**（如 `2026-09-13-10-30-45`），替换 v6 起的按天命名。

**改动**：v6 注入点 `o0/O/o0.ʻ`（m13814 新建汇聚点）日期格式字符串一处替换；`HH` = **24 小时制**（00-23）。

**效果**：秒级唯一 → 同日连建多条不再撞名，天然规避 (1)(2)(3) 后缀；v8 重名递增修复保留兜底。

### v9 验证

- `apktool b` 零错误；apksigner v1+v2+v3 通过 + zipalign verified
- `classes.dex` md5 与 v8 不同 → 改动已编译进 APK
- smali 反查：`yyyy-MM-dd-HH-mm-ss` 唯一落位
- 真机：建议验证 新建 → 标题/文件名为日期-时分秒 → 保存 → 同步

---

## 经验沉淀'''
assert old in s; s = s.replace(old, new, 1)

old = "12. **重名递增正确写法**：冲突循环必须保存基础名，每次 `base+(i)` 重算；在累计名上追加必然产生 (1)(2)(3)。"
new = old + '''
13. **时间戳命名是消除重名的最优解**：秒级唯一优于"检测-递增后缀"，改动面最小（一个字符串）。
14. **HH vs hh**：SimpleDateFormat 中 HH=24 小时制、hh=12 小时制，命名类时间戳必须用 HH。
15. **汇聚点收敛**：默认命名逻辑始终收敛在 m13814 一处，v6→v9 同一注入点演进，改格式零成本。'''
assert old in s; s = s.replace(old, new)

old = "- **真机回归**：WebDAV 同步、预览保存、图片选择、新建日期标题、返回自动保存、空白新建/重名后缀六项核心路径"
new = "- **真机回归**：WebDAV 同步、预览保存、图片选择、新建日期标题（秒级时间戳）、返回自动保存、空白新建/重名后缀六项核心路径"
assert old in s; s = s.replace(old, new)

open(p, 'w', encoding='utf-8').write(s)
print("README updated OK")
