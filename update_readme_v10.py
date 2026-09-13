# -*- coding: utf-8 -*-
p = 'README.md'
s = open(p, encoding='utf-8').read()

old = "| `易码_2.2.0beta3_优化版_v9.apk` | 原版优化 v9（v8 + 默认命名秒级时间戳 yyyy-MM-dd-HH-mm-ss），见 GitHub Release |"
new = old + "\n| `易码_2.2.0beta3_优化版_v10.apk` | 原版优化 v10（v9 + 删除后列表自动刷新、下拉刷新+同步），见 GitHub Release |"
assert old in s; s = s.replace(old, new)

old = "| **v9** | 2026-09-13 | 默认命名秒级时间戳 `yyyy-MM-dd-HH-mm-ss`（24h） | ✅ 已交付 |"
new = old + "\n| **v10** | 2026-09-13 | 修复删除后列表不刷新；下拉刷新=重载列表+触发同步 | ✅ 已交付 |"
assert old in s; s = s.replace(old, new)

old = "---\n\n## 经验沉淀"
new = '''---

## v10：删除后自动刷新 + 下拉刷新同步（2026-09-13）

**需求**：① 删除笔记后列表自动刷新（原残留条目点击报 `cant find noteID`）；② 所有笔记界面下拉刷新 + 同步。

**根因**：列表重载闸门是全局标志 `f12442`（`oO/ill.m14444/m14391`），只有设置页会置位；**删除笔记路径漏置** → 返回列表 onResume 不重载。下拉刷新（`m14095`）原有逻辑只触发同步、不重载列表。

**修复**（两处 smali）：
- 删除回调（`o0$1.ʻ()` 删除记录后）→ `oO/ill.ʼ(true)` 置刷新标志 → 返回列表自动重载
- 下拉刷新（`m14095` 开头）→ 置标志 + `m14079(null)` 重载列表，原同步逻辑照旧 → **下拉 = 刷新 + 同步**

### v10 验证

- `apktool b` 零错误；apksigner v1+v2+v3 通过 + zipalign verified
- `classes.dex` md5 与 v9 不同 → 补丁已编译进 APK
- smali 反查：删除回调 `oO/ill;->ʼ(Z)V`、`ˊ()V` 开头重载块均落位
- 真机：建议验证 删除笔记返回列表自动消失；所有笔记页下拉列表刷新且触发同步

---

## 经验沉淀'''
assert old in s; s = s.replace(old, new, 1)

old = "15. **汇聚点收敛**：默认命名逻辑始终收敛在 m13814 一处，v6→v9 同一注入点演进，改格式零成本。"
new = old + '''
16. **原版刷新通道是"标志位 + onResume 重载"**：f12442 是全局闸门，谁改了数据谁置位；删除路径漏置位是原版 bug，补上即可。
17. **下拉刷新的正确语义**：刷新（重载数据源）+ 同步（网络）是两件事，原版只做了同步；补上重载即闭环。
18. **反查混淆方法名用调用点交叉验证**：jadx 目录大小写会误导（Oo/ill 实际是 oO/ill），用调用方 smali 引用反查。'''
assert old in s; s = s.replace(old, new)

old = "- **真机回归**：WebDAV 同步、预览保存、图片选择、新建日期标题（秒级时间戳）、返回自动保存、空白新建/重名后缀六项核心路径"
new = "- **真机回归**：WebDAV 同步、预览保存、图片选择、新建日期标题（秒级时间戳）、返回自动保存、空白新建/重名后缀、删除刷新、下拉刷新八项核心路径"
assert old in s; s = s.replace(old, new)

open(p, 'w', encoding='utf-8').write(s)
print("README updated OK")
