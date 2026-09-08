# EasyMarkSync（易码同步）v0.3.0
> 练手项目
> 易码 APK 优化方案的产物：新建的现代 Kotlin + Compose 精简笔记应用，
> 内置 智能增量 WebDAV 同步。

## 0. 版本历史

| 版本         | 内容                                        |
| ---------- | ----------------------------------------- |
| v0.1.0     | 初版：功能完整，debug 构建 18.6MB（未裁剪）              |
| v0.2.0     | 代码审查修复 14 项（编辑器丢数据 / 长按删除 / 预览白屏 / 密码加密等） |
| **v0.3.0** | **体积优化：release + R8 裁剪 → 2.1MB（-89%）**    |

### v0.3.0 体积优化说明

原版 APK 4.7MB（2016 年，纯 Java + XML + 极简依赖）；本工程 debug 构建 18.6MB，原因与对策：

| 体积来源                              | 说明                                                                                    | 对策                                                |
| --------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------- |
| Kotlin/Compose 全家桶代码（dex 解压 63MB） | Compose UI/Material3/Navigation/ViewModel、Room、OkHttp、WorkManager、flexmark、coroutines | **R8 裁剪（release 构建）**                             |
| debug 构建不开裁剪                      | 全部依赖原样打包                                                                              | `isMinifyEnabled=true` + `isShrinkResources=true` |
| 资源冗余                              | 依赖库多语言资源                                                                              | shrinkResources（arsc 408KB → 144KB）               |
| highlight.js 472KB                | 渲染资产（复用原版）                                                                            | 保留（功能必需）                                          |

结果：**release 2.1MB**（classes.dex 3.6MB 解压 → 压缩后～1.4MB；assets 492KB；res 124KB），比原版还小 55%。

> 注意：release 用 debug 证书签名，便于直接安装验证；正式发布需替换为正式 keystore。
> R8 会对业务类混淆重命名（SyncEngine → 短名），属正常现象，不影响运行。

## 1. 任务背景

| #  | 级别 | 问题                                             | 修复                                          |
| -- | -- | ---------------------------------------------- | ------------------------------------------- |
| 1  | 高危 | 编辑返回 / 系统返回时保存与导航并行，composition scope 取消导致内容丢失 | 返回前先 `persist()` 完成后导航；`BackHandler` 拦截系统返回 |
| 2  | 高危 | 笔记未加载完成时输入内容被 `persist()` 静默丢弃                 | `init` 兜底创建实体 + `persist` 空实体兜底             |
| 3  | 高危 | 列表页长按删除从未生效（`onLongClick` 未绑定手势）               | `combinedClickable` 绑定长按 + 删除确认对话框          |
| 4  | 中危 | 预览页首次创建时内容未加载完 → 白屏且不重载                        | `AndroidView.update` 重载 HTML                |
| 5  | 中危 | 预览页 WebView 内链接可跳出应用                           | WebViewClient 拦截，外部链接转系统浏览器                 |
| 6  | 中危 | 修改自动同步开关后不重新调度 WorkManager                     | `applyAutoSyncSchedule` 幂等调度 / 取消           |
| 7  | 中危 | 设置页 "立即同步" 使用未保存的旧配置                           | `syncNow` 前先 `save()`                       |
| 8  | 中危 | 测试连接无任何反馈                                      | Toast 展示连接成功 / 失败                           |
| 9  | 中危 | WebDAV 密码明文存储                                  | EncryptedSharedPreferences 加密               |
| 10 | 中危 | 下载的笔记丢失原始创建时间                                  | 信封新增 `createdAt`（兼容旧文件）                     |
| 11 | 低危 | 编辑器删除无确认                                       | 删除确认对话框                                     |
| 12 | 低危 | 密码输入明文显示                                       | `PasswordVisualTransformation` 遮罩           |
| 13 | 低危 | WebDAV 请求无 User-Agent（部分服务器拒绝）                 | 统一 User-Agent                               |
| 14 | 低危 | 应用无版本展示                                        | 设置页显示版本号                                    |

## 1. 任务背景

| 项            | 内容                                                     |
| ------------ | ------------------------------------------------------ |
| 输入 APK       | `易码_2.2.0beta3.apk`（me.tshine.easymark，versionCode 79） |
| 需求演进         | 逆向重建 → 增加 WebDAV 同步（参考dav\_diary）→ 现代技术栈重构（方案 B）      |
| 方案 A         | 移植开源载体 —— 已验证走不通（无公开源码仓库）                              |
| **方案 B（选定）** | **新建 Kotlin + Compose 精简工程，可编译、可安装、可维护**               |
| 方案 C         | 存在不可编译的反编译工程（仅参考，否决）                                   |

## 2. 技术栈

* Kotlin 2.0.21 + Compose（BOM 2024.10.01）+ Material3

* AGP 8.7.3 / Gradle 8.10.2 / JDK 17

* Room 2.6.1（KSP）+ OkHttp 4.12.0（自封装 WebDAV）+ WorkManager 2.10.0

* Navigation Compose 2.8.4 + flexmark 0.64.8（Markdown 解析，原版同款）

* minSdk 26 / targetSdk 35

## 3. 功能范围

**已实现**



* 本地 Markdown 笔记：新建 / 编辑（600ms 防抖自动保存）/ 删除 / 搜索

* WebView 预览：flexmark 解析 + 原版渲染资源（highlight.js/markdown.css/ MathJax）

* WebDAV 增量同步（设置页配置 → 立即同步 / 每 6 小时后台任务）


  * PROPFIND / MKCOL / GET / PUT / DELETE / MOVE，Basic Auth（坚果云 / Nextcloud 通用）

  * dav\_diary 算法：三方状态（本地 / 远端 / SyncState）+ SHA-256 指纹比对

  * 变更集：localChanged /remoteChanged/conflict/removeRemote

  * 三阶段：下载 → 上传 → 冲突解决（LAST\_WRITE\_WINS / KEEP\_BOTH）

* 冲突策略可选；同步统计（上传 / 下载 / 冲突 / 删除）

**明确砍掉**（相对原版 3425 类）：Dropbox、Google Drive、Bugly、手势密码、多仓库体系等非核心功能。

## 4. 目录结构



```
app/src/main/java/me/tshine/easymarksync/

├── EasyMarkApp.kt            # Application：注册/回收周期同步

├── MainActivity.kt           # 导航（notes / editor / preview / settings）

├── data/

│   ├── db/                   # Room：NoteEntity / SyncStateEntity / DAO / AppDatabase

│   └── repo/                 # NoteRepository / SyncRepository

├── webdav/                   # WebDavConfig / WebDavClient / NoteSerializer / SyncEngine / SyncResult

├── sync/SyncWorker.kt        # WorkManager 每 6 小时自动同步

├── ui/

│   ├── notes/  editor/  preview/  settings/  theme/

└── util/MarkdownRenderer.kt  # flexmark → HTML → WebView

app/src/main/assets/          # markdown.css / highlight.js / highlight-init.js / mathjax-config.js（取自原 APK）
```

## 5. 构建

```
export ANDROID\\\_HOME=\\\~/Library/Android/sdk

export JAVA\\\_HOME=\\\<JDK17>

cd EasyMarkSync

./gradlew assembleDebug

\\# 产物：app/build/outputs/apk/debug/app-debug.apk（已签名，可直接安装）
```

## 6. WebDAV 配置（坚果云示例）



1. 设置 → WebDAV 云备份

2. 服务器地址：`https://dav.jianguoyun.com/dav/`

3. 账号：坚果云登录邮箱；密码：**应用专用密码**（安全选项 → 添加应用密码，非登录密码）

4. 远端目录：`/EasyMarkSync/`（自动创建）

5. 测试连接 → 立即同步

远端文件格式：每条笔记独立 `.md`，头部 YAML 信封（id /updatedAt/title），文件名 `<sha256前16位>.md`。

## 7. 验证情况

* [x] Gradle 构建：BUILD SUCCESSFUL（AGP/KSP/Kotlin 全链路）

* [x] APK 产物：app-debug.apk，18.5MB，SHA256 `892f0306…`

* [x] aapt 校验：包名 `me.tshine.easymarksync` /minSdk 26 /targetSdk 35 / INTERNET 权限

* [ ] 真机安装 + 坚果云实测（本机无设备，待用户验证）

## 8. 已知限制

* Basic Auth 仅（不支持 Digest）；如遇 401 请确认应用专用密码

* 首次同步采用全量（无远端时）；增量基于 SyncState 指纹

* 删除为软删除 + 远端孤儿清理（Phase 4）
