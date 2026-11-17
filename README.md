# 相册滑选 · PhotoSwipe

原生 Android 单机照片整理 App：左滑待删、右滑保留、上滑待星，删除与收藏均需结算确认。无账号、照片上传或分享；仅照片 GPS 地名解析可能使用系统网络服务。

A native Android photo triage app: swipe left to queue deletion, right to keep, and up to queue a favorite. Deletion and collection require confirmation in Settlement. No account, photo uploads, or sharing; GPS place-name lookup may use the system's network service.

- 版本 / Version：**v1.9.15**（versionCode 25），minSdk 26 / targetSdk 34，竖屏 / portrait。
- APK：**119,832 bytes**，与 v1.9.14 同签名 / same signing certificate as v1.9.14。
- 请覆盖安装，不卸载或清数据 / Install over the existing version; do not uninstall or clear app data.

## 本次更新 / What's new

1. **独家记忆地点可编辑**：照片位置权限已授权且 EXIF 读取成功，确认原片无 GPS 时，点击地点行可填写地点；保存后再次打开仍保留，可修改、清空或取消。手写地点仅存本地，不改原图或 GPS。
2. **时间文案简化**：时间后不再显示“文件时间”，日期与时分秒保留。
3. **下载内容更干净**：无 GPS 且地点未填写时，导出 PNG 不显示无 GPS 提示，也不留地点空行；填写后导出手写地点。有 GPS 的照片沿用原有地名／坐标。

1. **Editable memory location**: once photo-location permission is granted and EXIF has been read successfully, a photo confirmed to have no GPS offers a tappable location row. Enter and save a place, reopen it later, revise it, clear it, or cancel. The caption is local only and never changes the original or GPS metadata.
2. **Cleaner timestamp**: remove the “文件时间” (file time) suffix while keeping the date and time to the second.
3. **Cleaner downloads**: if a no-GPS photo has no written location, the exported PNG omits the GPS-missing message and the location line's space. Written locations are included; existing GPS place names / coordinates remain unchanged.

## 产品概述 / Product overview

- [中英双语 v1.9.15 产品概述 / Bilingual overview](docs/product-overview-v1.9.15.md)
- [中英双语发布说明 / Bilingual release notes](docs/release-notes-v1.9.15.md)
- [v1.9.14 历史概述 / Previous overview](docs/product-overview-v1.9.14.md)

## 三个常驻 Tab / Three persistent tabs

- **我的 / My**：收藏相册每页两张，36 个月时间轴，独家记忆与整页导出。
- **滑选 / Swipe**：一张一决策，预载备选卡，18% 卡宽横向阈值、12% 卡高上滑阈值，支持撤销与快甩判定。
- **结算 / Settlement**：查看待删、待星标和保留清单；永久删除和收藏分别确认。

未确认的待办不会执行，关闭或切换页面不等于放弃记录；已收藏照片退出滑选，取消收藏后可重新进入候选。

Unconfirmed actions are not executed. Closing or changing screens does not discard pending records. Collected photos are excluded from Swipe; removing a favorite makes it eligible again.

## 目录 / Layout

```text
app/src/com/maodun/photoswipe/   14 个产品模块 / 14 product modules
app/res/                       图标与资源 / icons and resources
app/tests/                     专项与回归测试 / targeted and regression tests
app/build.sh                   aapt2 → javac → d8 → zipalign → apksigner
app/测试记录-v1.9.15.md         本版验证状态 / verification status
app/run_instrumentation_v1915.py  API34专项入口 / API34 targeted test runner
docs/                          中英双语概述与发布说明 / bilingual overview and release notes
```

## 构建 / Build

需要 Android SDK `platforms/android-34`、`build-tools/34.0.0` 和可编译 Java 8 源码的 JDK。脚本默认路径为 `$HOME/android-dev/sdk`、`$HOME/android-dev/jdk/Contents/Home`；换机请调整路径。

Requires Android SDK `platforms/android-34`, `build-tools/34.0.0`, and a JDK capable of compiling Java 8 source. Default paths are `$HOME/android-dev/sdk` and `$HOME/android-dev/jdk/Contents/Home`; adjust them on another machine.

```bash
bash app/build.sh
```

输出 `安装包/相册滑选-v1.9.15.apk`。这不是 Gradle 工程，无 AndroidX 依赖。源码不含签名私钥；其他机器自动创建的签名不能覆盖原签名安装包。

Outputs `安装包/相册滑选-v1.9.15.apk`. This is not a Gradle project and has no AndroidX dependency. Signing keys are not included: a newly generated key on another machine cannot upgrade the original signed installation.

## 数据与风险 / Data and risk

- **永久删除无法恢复**，不进入系统“最近删除”；重要照片请先备份。
- 收藏仅 App 内记录，不修改系统相册收藏标记，也不是原图备份。
- SQLite 账本、故事、手写地点与设置存本地，`allowBackup=false`，卸载或清数据会丢失。
- 仅 GPS 反向地理编码使用系统 Geocoder，不读取手机实时位置。

**Deletion is permanent and cannot be restored**, and does not use the system's Recently Deleted. Back up important photos. Collection is app-local and is not an original-photo backup. The ledger, stories, manual locations and settings are local; `allowBackup=false`, so uninstalling or clearing data loses them. Only photo-GPS reverse geocoding uses the system Geocoder; live device location is not read.

## 验证 / Verification

**v1.9.15**：构建、验签和 16 项主机 JVM 地点存储测试通过。UI / 下载专项已编译但未运行：本机 Android 模拟器被虚拟化权限阻止，未接入真机。因此地点编辑、真实公共相册下载与本版完整回归仍待验收，不沿用旧版测试数作为本版通过结果。

**v1.9.15**: build, signature verification, and 16 host-JVM manual-location storage checks passed. UI / download instrumentation compiled but did not run: virtualization permissions blocked the local emulator and no real device was connected. Address editing, real public-gallery exports, and the full regression suite remain unverified for this version. Previous-version test counts are not treated as this version's results.

见 / See [app/测试记录-v1.9.15.md](app/测试记录-v1.9.15.md).

## 许可 / License

未附加开源许可证，默认保留所有权利；复用请先联系作者。

No open-source license is attached; all rights reserved by default. Contact the author before reuse.
