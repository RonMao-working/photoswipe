# 相册滑选 · PhotoSwipe

原生 Android 单机照片整理 App：左滑待删、右滑保留、上滑待星，删除与收藏均需结算确认。无账号、照片上传或分享；仅照片 GPS 地名解析可能使用系统网络服务。

A native Android photo triage app: swipe left to queue deletion, right to keep, and up to queue a favorite. Deletion and collection require confirmation in Settlement. No account, photo uploads, or sharing; GPS place-name lookup may use the system's network service.

- 版本 / Version：**v1.9.16**（versionCode 26），minSdk 26 / targetSdk 34，竖屏 / portrait。
- 安装包 / APK：[GitHub Release v1.9.16](https://github.com/RonMao-working/photoswipe/releases/tag/v1.9.16) → `photoswipe-v1.9.16.apk`。**119,832 bytes**，与 v1.9.14 同签名 / same signing certificate as v1.9.14。
- 请覆盖安装，不卸载或清数据 / Install over the existing version; do not uninstall or clear app data.

## 本次更新 / What's new

1. **收起空故事区域**：未填写独家记忆时，不在纸框内预留空输入框或字数行。保留一行点击填写入口，已有故事仍显示并自动保存。
2. **信息等距贴底**：时间、地点、经纬度统一字体与行距，靠近纸框下边缘，保留窄边距。
3. **下载无多余留白**：导出高度按实际照片、信息与故事计算；没有故事或只有空白字符，不留故事区域、分隔线或大片空白。保留v1.9.15手写地点和未填写地点不导出的规则。

1. **Collapse empty story space**: an unwritten memory reserves no editor or counter inside the paper. A compact tap-to-write entry remains; written stories stay visible and auto-saved.
2. **Consistent, bottom-aligned metadata**: time, place and coordinates share type and line spacing, ending close to the paper's bottom with narrow padding.
3. **No excess export whitespace**: image height follows the real photo, metadata and story. Empty or whitespace-only stories leave no story area, separator or large blank region. Retains v1.9.15 manual locations and omission of unwritten locations.

## 产品概述 / Product overview

- [中英双语 v1.9.16 产品概述 / Bilingual overview](docs/product-overview-v1.9.16.md)
- [中英双语发布说明 / Bilingual release notes](docs/release-notes-v1.9.16.md)
- [v1.9.14 历史概述 / Previous overview](docs/product-overview-v1.9.14.md)

## 三个常驻 Tab / Three persistent tabs

- **我的 / My**：收藏相册每页两张，36 个月时间轴，独家记忆与整页导出。
- **滑选 / Swipe**：一张一决策，预载备选卡，18% 卡宽横向阈值、12% 卡高上滑阈值，支持撤销与快甩判定。
- **结算 / Settlement**：查看待删、待星标和保留清单；永久删除和收藏分别确认。

未确认的待办不会执行，关闭或切换页面不等于放弃记录；已收藏照片退出滑选，取消收藏后可重新进入候选。

Unconfirmed actions are not executed. Closing or changing screens does not discard pending records. Collected photos are excluded from Swipe; removing a favorite makes it eligible again.

## 目录 / Layout

```text
app/src/com/maodun/photoswipe/   15 个产品模块 / 15 product modules
app/res/                       图标与资源 / icons and resources
app/tests/                     专项与回归测试 / targeted and regression tests
app/build.sh                   aapt2 → javac → d8 → zipalign → apksigner
app/测试记录-v1.9.16.md         本版验证状态 / verification status
app/tests/Regression1916Instrumentation.java  本版UI/PNG专项 / version-specific UI and PNG checks
docs/                          中英双语概述与发布说明 / bilingual overview and release notes
```

## 构建 / Build

需要 Android SDK `platforms/android-34`、`build-tools/34.0.0` 和可编译 Java 8 源码的 JDK。脚本默认路径为 `$HOME/android-dev/sdk`、`$HOME/android-dev/jdk/Contents/Home`；换机请调整路径。

Requires Android SDK `platforms/android-34`, `build-tools/34.0.0`, and a JDK capable of compiling Java 8 source. Default paths are `$HOME/android-dev/sdk` and `$HOME/android-dev/jdk/Contents/Home`; adjust them on another machine.

```bash
bash app/build.sh
```

输出 `安装包/相册滑选-v1.9.16.apk`。这不是 Gradle 工程，无 AndroidX 依赖。源码不含签名私钥；其他机器自动创建的签名不能覆盖原签名安装包。

Outputs `安装包/相册滑选-v1.9.16.apk`. This is not a Gradle project and has no AndroidX dependency. Signing keys are not included: a newly generated key on another machine cannot upgrade the original signed installation.

## 数据与风险 / Data and risk

- **永久删除无法恢复**，不进入系统“最近删除”；重要照片请先备份。
- 收藏仅 App 内记录，不修改系统相册收藏标记，也不是原图备份。
- SQLite 账本、故事、手写地点与设置存本地，`allowBackup=false`，卸载或清数据会丢失。
- 仅 GPS 反向地理编码使用系统 Geocoder，不读取手机实时位置。

**Deletion is permanent and cannot be restored**, and does not use the system's Recently Deleted. Back up important photos. Collection is app-local and is not an original-photo backup. The ledger, stories, manual locations and settings are local; `allowBackup=false`, so uninstalling or clearing data loses them. Only photo-GPS reverse geocoding uses the system Geocoder; live device location is not read.

## 验证 / Verification

**v1.9.16**：构建、验签、45项主机JVM排版检查和16项地点存储检查通过。真实UI/PNG专项已编译但未运行：没有连接真机，模拟器虚拟化受限。页面行距、故事展开／收起、实际公共相册下载与完整回归仍待验收。

**v1.9.16**: build, signature verification, 45 host-JVM layout checks and 16 place-storage checks passed. Actual UI/PNG instrumentation compiled but did not run: no real device is connected and emulator virtualization is unavailable. On-device spacing, story expansion/collapse, public-gallery downloads and full regression remain unverified.

见 / See [app/测试记录-v1.9.16.md](app/测试记录-v1.9.16.md).

## 许可 / License

未附加开源许可证，默认保留所有权利；复用请先联系作者。

No open-source license is attached; all rights reserved by default. Contact the author before reuse.
