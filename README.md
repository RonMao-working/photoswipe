# 相册滑选（单机版）· PhotoSwipe — Offline Edition

## 中文

原生Android单机照片整理App。左滑待删、右滑保留、上滑待星标；删除与收藏需在结算页确认。无账号、照片上传、公开评分或云同步，只有照片GPS地名解析可能使用系统网络服务。

**当前版本v1.9.17，versionCode27。** 本次只更名为“相册滑选（单机版）”，包名com.maodun.photoswipe与旧签名保持，功能沿用v1.9.16。

- [下载v1.9.17单机版APK](https://github.com/RonMao-working/photoswipe/releases/tag/v1.9.17)：photoswipe-offline-v1.9.17.apk，119,832字节。
- [产品概述（中英双语）](docs/product-overview-v1.9.17.md) · [发布说明（中英双语）](docs/release-notes-v1.9.17.md)
- [旧版v1.9.16](https://github.com/RonMao-working/photoswipe/releases/tag/v1.9.16)与历史文档保留。本次不上传V2开发版。

直接覆盖安装旧V1，不卸载、不清数据。私人记录、故事、手写地点保存在本地；收藏只是引用，不是原图备份。永久删除不能恢复，重要照片先备份。

三个常驻Tab：我的（两图/页收藏与独家记忆）、滑选（逐张决策与撤销）、结算（待删与待星标分别确认）。独家记忆支持无GPS手写地点，无故事导出不留大片空白，时间地点坐标等距靠底。

代码在app/src/com/maodun/photoswipe，资源app/res，测试app/tests。执行bash app/build.sh，需Android SDK34/build-tools34.0.0和支持Java8源代码的JDK。默认SDK/JDK路径在$HOME/android-dev，可在脚本中调整。构建输出安装包/相册滑选-单机版-v1.9.17.apk。无Gradle、AndroidX依赖。

源码不包含签名私钥。在其他机器创建新签名后不能覆盖现有签名的安装。allowBackup=false，卸载/清数据会丢失本地记录。

验证：已交付APK包名/名称/版本/签名核验通过；本次只改名称和版本。沿用v1.9.16的45项主机JVM排版和16项地点存储记录，真实UI/PNG、厂商图库与同机并存未在本版本新增实测。详见app/测试记录-v1.9.16.md。

开发者RonMao。未附加开源许可证，默认保留所有权利；复用请先联系作者。

---

## English

A native Android offline photo-triage app. Swipe left to queue deletion, right to keep and up to queue a favorite; deletion and collection require Settlement confirmation. No accounts, photo uploads, public ratings or cloud sync. Photo-GPS place-name lookup may use the system network service.

**Current release:v1.9.17, versionCode27.** This release only renames the app to “相册滑选（单机版）” (PhotoSwipe — Offline Edition). Package com.maodun.photoswipe and its signing certificate remain unchanged; features retain the v1.9.16 baseline.

- [Download the v1.9.17 offline APK](https://github.com/RonMao-working/photoswipe/releases/tag/v1.9.17):photoswipe-offline-v1.9.17.apk,119,832bytes.
- [Bilingual product overview](docs/product-overview-v1.9.17.md) · [Bilingual release notes](docs/release-notes-v1.9.17.md)
- [Previous v1.9.16](https://github.com/RonMao-working/photoswipe/releases/tag/v1.9.16) and historical documentation remain. No V2 development build is published in this update.

Install over existing V1; do not uninstall or clear data. Local records, stories and manual places are retained. Collection is a reference, not a backup. Permanent deletion cannot be restored; back up important photos.

Three persistent tabs:My(two-photo collection pages and memory notes), Swipe(individual decisions and undo), Settlement(separate deletion/favorite confirmation). Memory pages allow manual locations for no-GPS photos; empty stories export without large whitespace, and metadata is consistently spaced near the bottom.

Source:app/src/com/maodun/photoswipe; resources:app/res; tests:app/tests. Run bash app/build.sh with Android SDK34/build-tools34.0.0 and a Java8-source-compatible JDK. Default paths:$HOME/android-dev; edit the script for another machine. Output:安装包/相册滑选-单机版-v1.9.17.apk. No Gradle or AndroidX dependencies.

Signing private keys are not included. A new signing key on another machine cannot update the original signed installation. With allowBackup=false, uninstalling/clearing data loses local records.

Verification:the delivered APK's package, label, version and certificate were checked. Only naming/version metadata changed. The v1.9.16 baseline includes45 host-JVM layout and16 place-storage checks; no new on-device UI/PNG, vendor-gallery or side-by-side installation testing was performed. See app/测试记录-v1.9.16.md.

Developer:RonMao. No open-source license is attached; all rights reserved by default. Contact the author before reuse.
