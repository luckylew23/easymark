# easymark
易码优化版
# 易码原版优化实施记录 v5
* 版本：v5（2026-09-08）

* 产物：`易码_2.2.0beta3_优化版_v5.apk`（4.74MB）
***

## 一、本次任务
smali 级实施：明文放行、targetSdk 28、保存路径适配；密码 Hash 降级、64 位放弃；重打包签名验证；交付 v5 |

## 二、实施方式说明

原版源码不可编译（ProGuard 混淆），因此采用 **smali 层修改**：`apktool d` 解包 → 改 manifest/smali → `apktool b` 重打包 → uber-apk-signer 签名（v1+v2+v3 通过 + zipalign 通过）。

## 三、实施明细（全部不动功能与同步逻辑）

| #    | 项                   | 实施内容                                                                                                                                                                                                                                                        | 状态    |
| ---- | ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----- |
| P0-1 | **明文 HTTP 放行**      | manifest `<application>` 加 `usesCleartextTraffic="true"` → Android 9+ 恢复更新检查 + HTTP WebDAV 连接                                                                                                                                                               | ✅ 完成  |
| P0-2 | **targetSdk 25→28** | manifest `uses-sdk` + apktool.yml 同步升级；评估结论：不升 33（Android 13 媒体权限细分会损坏原版图片选择，smali 层无授权适配 UI，风险高；28 兼容面最大：Android 9-12 全功能 + 13+ 图片权限不受细分影响）                                                                                                                | ✅ 完成  |
| P1-3 | **保存路径分区存储适配**      | 6 处 `Environment.getExternalStorageDirectory()` → `getExternalFilesDir("Download")`（应用专属目录，分区存储下永远可写）：PreviewActivity$7（预览保存）、lii$4 /ii\$5（捕获 Context 字段）、lii×2 + oO（静态方法经 Application 静态缓存 `lii.sAppContext` + 新增 `setAppContext/getDownloadDir` 注入，含空值回退） | ✅ 完成  |
| P1-4 | **手势密码 Hash 化**     | **降级未实施**：混淆映射（jadx 类名 ≠ smali 类名）无法可靠定位存储 / 验证点；安全逻辑改错会锁死笔记（比明文更糟）。**建议在源码级工程实施**                                                                                                                                                                          | ⚠️ 降级 |
| P2-6 | **arm64-v8a so**    | **放弃**：原版 `libgpuimage-library.so` 仅 jcenter 有旧版（已关闭），中央仓库 / GitHub/jitpack 均无 1.4.1 的 arm64 资产；用新版 `libyuv-decoder.so` 改名会导致 native 符号缺失崩溃（绝不可用）。实际影响仅上架合规，arm64 设备以兼容模式运行功能完全正常                                                                           | ⚠️ 放弃 |

## 四、验证情况

* [x] `apktool b` 重打包成功（smali 编译零错误）
* [x] aapt：包名 `me.tshine.easymark` / versionCode 79 / **targetSdkVersion 28** / usesCleartextTraffic=true
* [x] apksigner：v1+v2+v3 全部通过；zipalign 4 字节对齐通过
* [x] 反编译复核：`setAppContext` 注入 Application.onCreate ✅；6 处保存路径全部替换（lii 残留 2 处为回退逻辑）✅；`getDownloadDir` 方法存在 ✅
* [ ] 真机验证：①设置 → WebDAV 同步（含 http:// 服务器）②预览保存图片 / 导出文件 ③原全部功能回归（功能未动，理论无回归）

- APK：SHA256 `4af7aa931c75155604d52d442d09314932e5a41943dfac4dd763e348e995fa24`（4.74MB）

## 五、经验

1. **smali 层修改是 "老 App 现代化" 的唯一路径**：源码不可编译时，逐条指令注入（iget 字段 → invoke 方法）可行且可控；关键原则是**寄存器冲突检查**（注入的 v6/v7 等在方法后续复用前必须消费完）—— 本次 4 处注入全部核验通过。
2. **混淆映射坑**：jadx 输出的类名（Oo/ill.java）与 apktool smali 类名（aa.smali…）**不一致**，不能用 jadx 路径直接找 smali；必须用字符串引用（R\$string 字段名）反查。
3. **"能改" 与 "该改" 分开判断**：密码 Hash（定位风险高、改错锁死数据）与 64 位 so（无可靠源）都主动降级 / 放弃，**诚实比硬凑重要**—— 用错误 so 改名会制造崩溃，比不补更糟。
4. **targetSdk 升级要评估 "升级本身引入的新故障"**：28 vs 33 的选择基于 Android 13 媒体权限细分的实际影响，不是 "越高越好"。
5. **分区存储适配的务实解**：应用专属目录（getExternalFilesDir）在 smali 层比 MediaStore 简单 10 倍且永远可写，代价是文件管理器可见性略差 —— 优先恢复功能，再谈可见性。
6. **每条修改必须反向验证**：重打包后再 `apktool d` 复核 smali 真实落入 APK，而非只看工作目录。

## 六、未完成项与后续
1. **P1-4 密码 Hash** → 建议在可编译源码工程（如 EasyMarkSync 演进版或源码级重建）实施
2. **P2-6 64 位** → 需 gpuimage 库源码级替换（2.1.0 API 兼容验证）或移除滤镜依赖

