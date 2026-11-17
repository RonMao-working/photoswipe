# 相册滑选 · 产品概述（v1.9.15）

## 1. 定位与页面

原生 Android 单机照片整理 App，包名 `com.maodun.photoswipe`，versionCode 25，minSdk 26 / targetSdk 34。常驻顶部 Tab 从左到右是“我的／滑选／结算”。无账号、照片上传、分享或云同步。

- **我的**：App 内收藏相册、36 个月日期导航、设置与独家记忆。
- **滑选**：左滑待删、右滑保留、上滑待星标，左右含义可在设置中互换；单击图片不弹信息，点击图下时间／地点／设备行进入详情。
- **结算**：查看待删、待星标与保留清单，分别确认永久删除和收藏；未确认待办保留，不因切页自动执行或丢弃。

## 2. 滑选与收藏规则

沿用 v1.9.14 手势：横向阈值 18% 卡宽、纵向阈值 12% 卡高，支持快甩判定、1:1 拖动、备选卡预载与 60ms 淡入、撤销入口。已保留和已收藏照片从滑选中排除；取消收藏允许照片重新进入候选。

收藏相册每页两张，上下排列，保持纸框、角贴与叠层拟物感。左滑翻向更近日期，右滑翻向更久远日期；日期窗口覆盖 36 个自然月，不是照片保留期限。点照片进入独家记忆，原片等比显示，可缩放查看并编写最多 40 码点的故事。

## 3. v1.9.15 独家记忆更新

- 照片位置权限已授权、EXIF 成功读取且确认无 GPS 的照片，地点行显示“地点未填写 · 点击填写地点”。点击打开编辑框，可通过手机键盘／手写输入法填写，点击保存后本地持久保留。
- 已填写地点可再编辑；取消不改旧值；清空保存后恢复填写提示。手写地点与故事、GPS 地名缓存分开存储，不修改原图、EXIF 或 GPS。
- 照片时间保留日期与时分秒，去掉“文件时间”后缀。
- 右上下载图标导出 1080px 宽 PNG 到 `Pictures/相册滑选`，只包含照片、时间、有效地点、真实故事与纸框，不含按钮、字数或填写提示。
- 无 GPS 且未填写地点时，导出不显示“照片无GPS信息”等提示，也不保留地址空行；填写后输出手写地点。
- 已有 GPS 的照片仍显示原有地名／经纬度；未授权或读取失败保持授权／重试逻辑，不把它们误判为无 GPS。

## 4. 数据、安全与实现

原生 Java，单 Activity，14 个产品模块，无 AndroidX、Gradle 或 native 库；构建使用 `aapt2 → javac → d8 → zipalign → apksigner`。

删除为永久删除，必须明确确认，App 无法恢复；收藏只保存在 App 内，不修改系统相册收藏字段。URI、稳定签名和全文件 SHA-256 用于去重，不是视觉相似识别。SQLite 账本及 SharedPreferences 保存记录、故事、手写地点和设置，`allowBackup=false`；请同包同签名覆盖安装，不卸载或清数据。

GPS 地名解析是唯一联网例外，交给系统 Geocoder 并本地缓存，不读取手机实时位置。没有 GPS 不能生成真实经纬度；本版允许用户补充地点文字，不伪造坐标。

## 5. 验证状态与边界

v1.9.15 APK 119,832 字节，签名与 v1.9.14 一致。构建、验签和 16 项主机 JVM 地点存储检查通过；UI／下载专项编译通过但未运行，本机模拟器被虚拟化权限阻止且无连接真机。本版地点编辑、实际公共相册下载和完整手势回归仍待验收，不能把旧版通过数量视为本版结果。

县区级地名取决于系统服务，不保证全球覆盖；更多 Android 版本、荣耀厂商图库和手感未全面验证。iOS 与 2.x 联网分享未实现。

详细验证状态见 `app/测试记录-v1.9.15.md`，发布说明见 `docs/release-notes-v1.9.15.md`。

---

# PhotoSwipe · Product Overview (v1.9.15)

## 1. Positioning and screens

A native Android photo triage app: package `com.maodun.photoswipe`, versionCode 25, minSdk 26 / targetSdk 34. Three persistent top tabs are My, Swipe, and Settlement. No accounts, photo uploads, sharing, or cloud sync.

- **My**: app-local collection album, 36-month date navigation, settings, and memory pages.
- **Swipe**: left queues deletion, right keeps, and up queues a favorite; left/right meanings can be swapped. Tapping the image does not open information; tapping the time / location / device rows below it opens details.
- **Settlement**: view pending deletion, pending favorites, and kept lists. Deletion and collection require separate confirmation; pending records are neither executed nor discarded just by changing screens.

## 2. Swipe and collection rules

Retains v1.9.14 gestures: 18% card-width horizontal threshold, 12% card-height vertical threshold, flick recognition, 1:1 dragging, a preloaded candidate with a 60ms fade-in, and undo. Kept and collected photos are excluded from Swipe; removing a favorite makes it eligible again.

The collection album shows two photos per page, stacked vertically, with paper frames, corner tabs and layered shadows. Swipe left for newer dates and right for older dates. The 36-calendar-month navigation window is not a retention limit. A photo opens its memory page, with an aspect-fit original, zoom, and an editable story of up to 40 Unicode code points.

## 3. v1.9.15 memory-page changes

- For a photo with location permission granted, successfully read EXIF, and confirmed absence of GPS, the location row becomes a tap-to-edit prompt. Enter a place with the phone keyboard / handwriting input method and save it locally.
- A written place can be revised. Cancel preserves the previous value; clearing and saving restores the prompt. Manual locations are stored separately from stories and geocoder cache, never changing the original photo, EXIF, or GPS.
- The timestamp retains its date and time to the second, without the “文件时间” (file time) suffix.
- The top-right download icon exports a 1080px-wide PNG to `Pictures/相册滑选`, containing only the photo, time, meaningful location, written story, and paper frame; no buttons, character counters, or editing prompts.
- If a no-GPS photo has no manual location, the export omits GPS-missing text and the empty location line's space. If written, the manual caption is included.
- Photos with GPS retain their place names / coordinates. Missing permission and read failures retain permission / retry behavior rather than being misclassified as missing GPS.

## 4. Data, safety, and implementation

Native Java, one Activity, 14 product modules, no AndroidX, Gradle, or native libraries. Build chain: `aapt2 → javac → d8 → zipalign → apksigner`.

Deletion is permanent and requires explicit confirmation; the app cannot restore deleted originals. Collection is app-local and does not modify system-gallery favorite flags. Deduplication compares URI, stable signature, and full-file SHA-256 rather than visual similarity. SQLite and SharedPreferences store records, stories, manual places, and settings; `allowBackup=false`. Upgrade only over the existing app with the same package and signature; do not uninstall or clear data.

GPS reverse geocoding is the sole network exception, delegated to the system Geocoder and cached locally; live device location is not read. Missing GPS cannot yield real coordinates. This version lets the user provide a place caption, not fabricated coordinates.

## 5. Verification and limits

The v1.9.15 APK is 119,832 bytes and shares the v1.9.14 signing certificate. Build, signature verification, and 16 host-JVM manual-location storage checks passed. UI / download instrumentation compiled but did not run: virtualization permissions blocked the local emulator and no real device was connected. Address editing, actual public-gallery downloads, and full gesture regression remain unverified for this version; prior-version test counts are not this version's results.

District-level place names depend on the system service and are not guaranteed worldwide. Other Android versions, Honor gallery behavior, and real-device feel are not comprehensively verified. iOS and 2.x online sharing are not implemented.

See `app/测试记录-v1.9.15.md` for verification status and `docs/release-notes-v1.9.15.md` for release notes.
