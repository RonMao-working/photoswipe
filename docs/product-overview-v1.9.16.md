# 相册滑选 · 产品概述（v1.9.16）

## 1. 产品与页面

原生 Android 单机照片整理 App，包名 `com.maodun.photoswipe`，versionCode26，minSdk26 / targetSdk34。顶部常驻“我的／滑选／结算”，分别承载收藏相册、逐张照片决策与待办确认。无账号、照片上传、分享或云同步。

滑选左滑待删、右滑保留、上滑待星标，左右方向可互换；单击图片不弹信息，点击图下时间／地点／设备文字行查看详情。永久删除与收藏分别在结算确认；切页不会自动执行或丢弃待办。已处理和已收藏照片排除出滑选，取消收藏后可重新进入候选。

## 2. 收藏与独家记忆

收藏相册每页两张、上下排列，保留纸框、角贴与叠层拟物感。左滑看更近的日期，右滑看更久远的日期，36个自然月时间窗口用于导航，不是保留期限。

独家记忆页显示等比原片、日期时间、拍摄地点／坐标和最多40个Unicode码点的故事。无GPS且位置权限已授予、EXIF读取成功时，地点行可点击手写，保存后本地保留，可修改、清空或取消，不改变原图或GPS。时间不附加“文件时间”。

## 3. v1.9.16 紧凑排版

- 未填写故事且不在编辑时，纸框内不再保留空输入框或字数行，照片信息靠近纸框底部，只留12dp内边距。纸框外保留一行点击填写入口，填写后故事仍在纸框内显示并自动保存。
- 页面时间、地点、坐标统一11sp字体与4dp行间距；坐标通常为同一行经纬度，时间／地名／坐标通常三行。
- 下载图使用同一文本布局绘制元信息，统一29px文字与12px额外行间距。长地址自然换行，不裁文字。
- 导出1080px宽PNG到 `Pictures/相册滑选`，只含原片、时间、有效地点、真实故事与纸框，不包含操作控件、字数或填写提示。
- 导出高度由实际照片和文本决定；没有故事或只有空白字符，不生成故事布局、分隔线或故事空位，底部只留36px纸边。有故事时按实际高度扩展并完整保留。
- 无GPS且地点未填写时，导出不含无GPS提示或地址空行；有填写则输出手写地点。有GPS仍使用原有地名／坐标，未授权或读取失败不误判为无GPS。

## 4. 实现与安全

原生Java、单Activity、15个产品模块；新增 `MemoryLayout` 统一元信息分行和导出高度。无AndroidX、Gradle或native库，构建链为 `aapt2 → javac → d8 → zipalign → apksigner`。本轮不改变v1.9.14滑选手势阈值、备选卡与去重逻辑。

SQLite账本和SharedPreferences存本地记录、故事、手写地点和设置。`allowBackup=false`，卸载／清数据会丢失记录，请同包同签名覆盖安装。收藏只是App内引用，不是原图备份；永久删除无法恢复，重要照片请先备份。唯一联网例外为照片GPS反向地理编码，经系统Geocoder服务，本地缓存，不读取手机实时位置。

## 5. 验证与边界

v1.9.16 APK为119,832字节，与旧版同签名。构建、验签、45项主机JVM排版检查和16项地点存储检查通过；真实UI/PNG专项编译但未运行，无连接真机且模拟器虚拟化受限。实际屏幕基线间距、故事展开／收起与相册PNG下载仍待运行验收。

全球县区地名依赖系统服务，不保证覆盖；更多Android版本与厂商图库行为未全面验证。iOS、2.x联网分享未实现。详细验证状态见 `app/测试记录-v1.9.16.md`，更新摘要见 `docs/release-notes-v1.9.16.md`。

---

# PhotoSwipe · Product Overview (v1.9.16)

## 1. Product and screens

A native Android photo triage app: package `com.maodun.photoswipe`, versionCode26, minSdk26 / targetSdk34. Persistent My / Swipe / Settlement tabs provide collection albums, individual photo decisions, and action confirmation. No accounts, photo uploads, sharing, or cloud sync.

Swipe left to queue deletion, right to keep, and up to queue a favorite; left/right meanings can be swapped. Tapping the photo does not open information; the time / place / device text rows do. Deletion and collection require separate settlement confirmation; changing screens neither executes nor discards pending records. Reviewed and collected photos are excluded from Swipe, while removing a favorite makes it eligible again.

## 2. Collection and memory pages

Two photos per album page, stacked vertically, retain the paper-frame, corner-tab and layered-shadow appearance. Swipe left for newer dates and right for older dates. The 36-calendar-month window is navigation, not a retention limit.

Memory pages show an aspect-fit original, timestamp, place / coordinates, and up to40 Unicode code points of written story. When photo-location permission is granted, EXIF is successfully read and GPS is absent, the location row supports manual captions saved locally; revise, clear, or cancel without altering the original or GPS. Timestamps have no file-time suffix.

## 3. v1.9.16 compact layout

- Empty, inactive stories no longer reserve an editor or character counter inside the paper. Metadata ends near its bottom with12dp padding. A compact tap-to-write entry remains outside; written stories stay on the paper and auto-save.
- Screen timestamps, place names and coordinates share11sp type and4dp line spacing. Latitude and longitude share one coordinate row, normally producing three rows: time / place / coordinates.
- Exported metadata uses one text layout with29px type and12px extra line spacing. Long addresses wrap instead of being cropped.
- The download icon exports a1080px-wide PNG to `Pictures/相册滑选`, containing only the photo, time, meaningful location, written story and frame; no controls, counter or editing prompts.
- Output height follows real image and text content. Empty or whitespace-only stories generate no story layout, separator or reserved space; bottom paper padding is36px. Written stories expand the image by their actual height.
- No-GPS photos with no manual place export without GPS-missing text or an empty location row. Written places are included. GPS, permission and read-failure handling stays unchanged.

## 4. Implementation and safety

Native Java, one Activity,15 product modules; `MemoryLayout` centralizes metadata rows and output-height calculations. No AndroidX, Gradle or native libraries; build chain: `aapt2 → javac → d8 → zipalign → apksigner`. This update does not change v1.9.14 gesture thresholds, candidate cards or deduplication.

SQLite and SharedPreferences store records, stories, places and settings locally. With `allowBackup=false`, uninstalling or clearing data loses them; upgrade over the existing installation with the same package and signature. Collection is a reference, not an original-photo backup. Deletion is permanent; back up important photos. Photo-GPS reverse geocoding via the system Geocoder is the sole network exception, cached locally, with no live device location access.

## 5. Verification and limits

The v1.9.16 APK is119,832bytes and retains the previous signing certificate. Build, signature verification,45 host-JVM layout checks and16 place-storage checks passed. Actual UI/PNG instrumentation compiled but did not run: no real device is connected and emulator virtualization is unavailable. Screen baseline spacing, editor expansion/collapse and gallery PNG downloads remain unverified on-device.

District-level geocoding depends on the system service and is not guaranteed worldwide. Other Android versions and vendor galleries are not comprehensively verified. iOS and2.x online sharing are unimplemented. See `app/测试记录-v1.9.16.md` and `docs/release-notes-v1.9.16.md`.
