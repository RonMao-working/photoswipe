# 相册滑选 · PhotoSwipe

原生 Android 单机照片整理 App：左滑待删、右滑保留、上滑待星，滑动只暂存，结算确认才生效。无账号、无上传、无分享。

A native Android, fully offline photo triage app: swipe left to queue a delete, right to keep, up to favorite. Swipes only record intent — nothing is committed until you confirm in settlement. No account, no upload, no sharing.

- 版本 / Version：**v1.9.14**（versionCode 24），minSdk 26 / target34，竖屏 / portrait
- 安装包 / APK：115,736 字节，签名覆盖安装保留数据 / 115,736 bytes, install-over keeps data

## 产品概述 / Product overview

- 中文：[docs/product-overview-v1.9.14.md](docs/product-overview-v1.9.14.md)（上半部分）
- English：same file, second half

## 三件事 / Three things it does

1. **滑选 / Swipe** —— 一次一张卡，左滑待删、右滑保留、上滑待星，18% 卡宽或一次快甩即成交，背后下一张已预载。
2. **结算 / Settle** —— 待删、待星标、保留三张清单；永久删除与收录收藏都要单独确认，未确认退出即本轮作废。
3. **收藏 / Collection** —— App 内的拟物收藏相册，每页两张，支持 36 个月时间轴、独家记忆故事与整页导出。

## 目录 / Layout

```
app/src/com/maodun/photoswipe/   14 个产品模块 / 14 product modules
app/res/                         图标、字符串与资源 / icons, strings, resources
app/tests/                       仪器测试源码 / instrumentation test source
app/build.sh                     aapt2 → javac → d8 → zipalign → apksigner
app/测试记录-v1.9.14.md           本版验证记录 / verification log for this version
docs/product-overview-v1.9.14.md 中英双语产品概述 / bilingual overview
```

## 构建 / Build

需要本地 Android SDK（`platforms/android-34` 的 `android.jar`、`build-tools`）与 JDK 8；脚本默认读 `$HOME/android-dev/sdk`、`$HOME/android-dev/jdk/Contents/Home`，换机器请先改 `build.sh` 顶部路径。

Requires a local Android SDK (`android.jar` from `platforms/android-34`, plus `build-tools`) and JDK 8. The script defaults to `$HOME/android-dev/sdk` and `$HOME/android-dev/jdk/Contents/Home`; adjust the paths at the top of `build.sh` on another machine.

```bash
bash app/build.sh
```

这不是通用 Gradle 工程，没有 `build.gradle`，也没有 AndroidX 依赖。

This is not a Gradle project: there is no `build.gradle` and no AndroidX dependency.

## 数据与风险 / Data and risk

- 删除是**永久删除**，不进系统「最近删除」，App 内无法恢复；重要照片请先备份。
- 收藏只存在 App 内，不动系统相册的收藏标记。
- 全部数据在本地（SQLite 账本 + SharedPreferences），`allowBackup=false`，卸载即丢失。
- 仅一处联网：照片 GPS 反向地理编码，交给系统 Geocoder。

Deletion is permanent — it does not land in the system "Recently Deleted" and cannot be restored by the app; back up what matters first. Collection lives inside the app only. All state is local (`allowBackup=false`). The only network use is GPS reverse geocoding via the system Geocoder.

## 验证 / Verification

API 34 模拟器 43 项专项与 9 项真实 UI 通过；真机（荣耀 Magic7）未接入 adb，甩动成交只有判定层断言。详见 [app/测试记录-v1.9.14.md](app/测试记录-v1.9.14.md)。

43 targeted checks and 9 real-UI checks pass on the API 34 emulator. The Honor Magic7 device is not reachable over adb, and flick-to-commit is asserted only at the decision layer. See [app/测试记录-v1.9.14.md](app/测试记录-v1.9.14.md).

## 许可 / License

未附加开源许可证，默认保留所有权利；如需公开复用请先联系作者。

No open-source license is attached; all rights reserved by default. Contact the author before reuse.
