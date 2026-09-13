# easymark — 易码优化版

基于**原版易码 2.2.0beta3**（包名 `me.tshine.easymark`，versionCode 79）的自用优化版本：**功能与同步逻辑保持原样**，仅做现代 Android 兼容与使用体验优化。

原版源码 ProGuard 混淆不可编译，因此全部优化通过 **smali 级修改**完成：`apktool d` 解包 → 修改 manifest/smali → `apktool b` 重打包 → uber-apk-signer 签名（v1+v2+v3）+ zipalign。

---

## 仓库内容

| 内容 | 说明 |
|---|---|
| `EasyMarkSync/` | Kotlin + Compose + Room + WebDAV 源码工程（自研，独立 App） |
| `易码_2.2.0beta3_优化版_v5.apk` | 原版优化 v5（Android 9+ 兼容） |
| `易码_2.2.0beta3_优化版_v6.apk` | 原版优化 v6（v5 + 新建笔记默认日期标题），见 GitHub Release |
| `易码_2.2.0beta3_优化版_v7.apk` | 原版优化 v7（v6 + 返回自动保存），见 GitHub Release |

---

## 优化方案总览（原版 → v5 → v6 → v7）

| 版本 | 时间 | 内容 | 状态 |
|---|---|---|---|
| 原版 2.2.0beta3 | — | 基线：minSdk 16 / targetSdk 25，Android 9+ 明文拦截、分区存储不可写 | — |
| **v5** | 2026-09-08 | 明文 HTTP 放行；targetSdk 25→28；保存路径分区存储适配 | ✅ 已交付 |
| **v6** | 2026-09-12 | 新建文字笔记默认标题/文件名 = 当天日期（可修改） | ✅ 已交付 |
| **v7** | 2026-09-13 | 编辑返回自动保存（Obsidian 式，不再弹"丢弃"框） | ✅ 已交付 |

---

## 原版基线

- 包名 `me.tshine.easymark`，versionCode 79，versionName 2.2.0beta3
- minSdk 16 / targetSdk 25（Android 7.0 时代）
- 3425 类 ProGuard 混淆 → **源码不可编译**，可安装产物的唯一路径是 smali 级修补
- 主要问题：①Android 9+ 默认拦截明文 HTTP（更新检查、HTTP WebDAV）；②targetSdk <28 无分区存储限制但升级后需适配；③Android 10+ 分区存储下 `getExternalStorageDirectory()` 不可写

---

## v5：Android 9+ 兼容优化（2026-09-08）

| # | 项 | 实施内容 | 状态 |
|---|---|---|---|
| P0-1 | **明文 HTTP 放行** | manifest `<application>` 加 `usesCleartextTraffic="true"` → Android 9+ 恢复更新检查 + HTTP WebDAV 连接 | ✅ |
| P0-2 | **targetSdk 25→28** | 升级评估：不升 33（Android 13 媒体权限细分会损坏原版图片选择，smali 层无授权 UI，风险高）；28 兼容面最大（Android 9-12 全功能 + 13+ 不受权限细分影响） | ✅ |
| P1-3 | **保存路径分区存储适配** | 6 处 `Environment.getExternalStorageDirectory()` → `getExternalFilesDir("Download")`（应用专属目录，分区存储下永远可写）：含捕获 Context 字段直取、静态方法经 Application 静态缓存注入（新增 `setAppContext/getDownloadDir`，含空值回退） | ✅ |
| P1-4 | **手势密码 Hash 化** | **降级未做**：混淆映射无法可靠定位存储/校验点，改错会锁死笔记（比明文更糟），建议源码级工程实施 | ⚠️ 降级 |
| P2-6 | **arm64-v8a so** | **放弃**：旧版 gpuimage 的 arm64 库无可靠来源（jcenter 已关）；改名替代会导致 native 崩溃。实际仅影响上架合规，arm64 设备兼容模式运行正常 | ⚠️ 放弃 |

### v5 验证

- `apktool b` 零错误；aapt 确认 `targetSdkVersion 28` + `usesCleartextTraffic=true`
- apksigner v1+v2+v3 通过 + zipalign 通过
- 反编译复核：注入代码真实落入 APK（`setAppContext`/`getDownloadDir`/6 处路径替换）
- 真机：建议验证 WebDAV 同步（含 http://）、预览保存、图片选择

---

## v6：新建笔记默认日期标题（2026-09-12）

**需求**：新建文字笔记时，默认标题/文件名 = 当天日期（如 `2026-09-12`），进入编辑器后可修改。

**注入点**：`o0/O/o0.m13814`（新建笔记唯一汇聚点，smali 方法 `ʻ(String,String,String,List)` 入口）。

```text
新建按钮 → m14081(null, null) → mode=2 启动编辑器
  → 保存时 m13814(repo, title, content, tags)  ← 注入点
    → if (title == null || title.isEmpty()) title = SimpleDateFormat("yyyy-MM-dd").format(new Date())
    → 标题框显示 title（可直接修改）→ NoteMeta.name = title
    → 同步文件名 m14308(title) = "2026-09-12.md"
```

**覆盖范围**：UI 新建按钮（title=null）、桌面快捷方式（title=""）、文本/图片分享导入（空标题兜底），一处覆盖全部新建路径。

**文件名联动**：原版同步文件名由标题生成（`m14308` = 标题 + `.md`），因此标题为日期时，本地笔记名、同步文件均为 `2026-09-12.md`；改名后三者同步跟随。

**保留逻辑**：原版 `untitled-` 前缀清空逻辑不受影响（日期不以该前缀开头）；非空标题（如图片导入文件名）保留原名。

### v6 验证

- `apktool b` 零错误；apksigner v1+v2+v3 通过
- 反编译复核：注入代码真实落入 APK（`SimpleDateFormat` / `yyyy-MM-dd` / null+isEmpty 双判空）
- aapt：`me.tshine.easymark` / versionName 2.2.0beta3 / targetSdk 28（v5 优化保持）
- 真机：建议验证 新建 → 标题显示日期 → 改名 → 保存 → 同步

---

## v7：编辑返回自动保存（2026-09-13）

**需求**：新建/修改笔记时，若有改动未保存，**返回时自动保存**（Obsidian 式），不再弹"丢弃"提醒框；无改动（含新建空笔记）则不保存。

**原版行为**：`EditActivity.onBackPressed`（系统返回键）在有改动时弹「警告」框，且**只有"丢弃"按钮**（无保存选项）；标题栏返回箭头则直接自动保存。

**注入点**：`EditActivity.smali` 的 `onBackPressed` 弹框分支。

```text
系统返回键
  ├─ 有改动（isDirty=ˈ()Z 为 true）→ 原：弹丢弃框（ᴵᴵ()）→ 改：调保存方法（ˉ()V = m13829 保存并关闭）
  └─ 无改动 → super.onBackPressed()（不保存直接返回）
```

**复用原版语义**：
- `m13828()`（ˈ()Z）= 改动检测（标题/内容/标签/仓库任一变化即 true）→ 天然满足"无改动不保存"
- `m13829()`（ˉ()V）= 保存并关闭（内部含标题非法字符校验、仓库切换、草稿清理，结尾 `super.finish()`）
- 新建空笔记（标题=日期、内容空）→ isDirty=false → 不入库；输入内容后返回 → 自动保存入库

**保留逻辑**：抽屉/搜索框状态优先处理不变；标题栏返回箭头（home）原本就是自动保存，不受影响。

### v7 验证

- `apktool b` 零错误；apksigner v1+v2+v3 通过 + zipalign 通过
- 反编译复核：`ˉ()V`（保存）真实落入 `onBackPressed` 的 dirty 分支，替代 `ᴵᴵ()V`（弹框）
- `classes.dex` md5 与 v6 不同 → 补丁已编译进 APK
- 真机：建议验证 修改内容 → 返回 → 自动保存；新建空笔记 → 返回 → 列表不新增；新建输入内容 → 返回 → 入库

---

## 经验沉淀

1. **smali 层修改是老 App 现代化的可行路径**：逐条指令注入（字段读取 → invoke 方法）可控；关键是**寄存器冲突检查**（注入的临时寄存器在方法后续复用前必须消费完）。
2. **混淆映射坑**：jadx 类名 ≠ smali 类名，定位必须用方法签名/字符串引用反查，不能拿 jadx 路径直接找 smali。
3. **"能改"与"该改"分开判断**：低风险高收益的改（明文放行/路径适配）；改错比不改进糟的（密码 Hash/64 位 so）主动降级，诚实交付。
4. **targetSdk 升级要评估升级自身引入的新故障**：28 vs 33 的选择基于 Android 13 权限细分的实际影响，不是越高越好。
5. **找唯一汇聚点**：新建逻辑的标题默认值放在 `m13814`（全部新建路径交汇处），一处注入覆盖 UI 新建/快捷方式/分享导入。
6. **每条修改必须反向验证**：重打包后 `apktool d` 复核 smali 真实落入 APK，而非只看工作目录。
7. **jadx 方法名 ≠ smali 方法名**：jadx 把不可打印字符重命名为 m13525/m13829，smali 里实际是 `ᴵᴵ`/`ˉ`；必须解包看真实 smali，用真实字符替换（`\u` 转义不匹配）。
8. **改调用点而非改逻辑**：把弹框方法替换为保存方法，复用原版 isDirty/保存链路，最小侵入、零回归风险。
9. **寄存器陷阱**：`move-result` 会覆盖 v0（presenter 引用被 dirty 布尔值顶掉），新分支必须重新 load 字段，不能复用旧寄存器。

---

## 后续

- **P1-4 手势密码 Hash** → 建议在可编译源码工程实施
- **P2-6 64 位** → 需 gpuimage 库源码级替换（2.1.0 API 兼容验证）或移除滤镜依赖
- **真机回归**：WebDAV 同步、预览保存、图片选择、新建日期标题、返回自动保存五项核心路径
