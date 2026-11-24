# 相册滑选（单机版）· 产品概述 v1.9.17

原生Android照片整理App。顶部“我的／滑选／结算”三个常驻Tab分别承载收藏相册、逐张照片决策和待办确认。

- 滑选：左滑待删、右滑保留、上滑待星标，左右可互换；先标记再结算，支持撤销。点击照片下方的时间/地点/设备文字查看信息，不因单击照片弹出详情。已处理/已收藏照片不重复进入候选，取消收藏后可再次滑选。
- 我的：本地收藏相册每页两张、上下排列，36个月时间导航、纸框/角贴拟物效果。独家记忆最多40个Unicode码点，照片时间无“文件时间”后缀。确认原图无GPS且已授权、EXIF读取成功时可手写地点，不改变原片GPS。
- 独家记忆导出：保存1080px宽PNG，包含照片、有效时间地点坐标和故事。无故事不预留大块空白，无GPS且地点未填写时不输出缺GPS提示或空地址行。信息字体与行间距统一，内容靠近底边。
- 结算：永久删除和本地收藏分别确认。切页或关闭不自动执行待办。本地收藏是照片引用，不是原图备份；永久删除不可恢复。

v1.9.17只将软件名称及设置页改为“相册滑选（单机版）”。包名com.maodun.photoswipe，versionCode27，minSdk26 / targetSdk34，与旧V1同签名覆盖，保留本地数据。开发者RonMao。

实现为Java原生单Activity、15个产品模块，aapt2/javac/d8/zipalign/apksigner构建，不依赖Gradle或AndroidX。SQLite/SharedPreferences存账本、故事、地点和设置；allowBackup=false，卸载清数据会丢失。无账号、照片上传、公开投稿/评分或云同步。照片GPS反向地理编码可能使用系统网络服务，不读取实时位置。

APK包名/名称/版本/签名已核验，沿用v1.9.16的45项主机排版与16项地点存储检查。没有新真机UI/图库导出/同机并存验收，不把编译当作手机实测。

本仓库本次只发布V1.9.17，不包含尚未正式上线的V2开发包。旧Release和历史说明保留。

---

# PhotoSwipe — Offline Edition · Product Overview v1.9.17

A native Android photo-triage app with persistent My / Swipe / Settlement tabs for local albums, individual decisions and pending-action confirmation.

- Swipe: left queues deletion, right keeps and up queues a favorite. Left/right meanings can be swapped. Actions are staged before confirmation and can be undone. Metadata opens from the time/place/device text below the photo, not from a single photo tap. Reviewed/favorite photos do not re-enter the candidate queue; removing a favorite makes it eligible again.
- My: two vertically stacked photos per album page, a36-month navigation window and paper/corner-tab album styling. Memory notes accept up to40 Unicode code points. Timestamps have no file-time suffix. A manual place can be entered when location access is granted and EXIF confirms no GPS; original GPS is not modified.
- Memory export:1080px-wide PNG containing the photo, meaningful time/place/coordinates and note. No note means no large blank story area. No-GPS photos without a manual place omit missing-GPS text and empty location rows. Metadata shares consistent typography and spacing near the bottom.
- Settlement: permanent deletion and local collection are separately confirmed. Closing or switching screens does not execute pending actions. Collection is a reference rather than a backup; permanent deletion cannot be restored.

v1.9.17 only renames the app and Settings label to “相册滑选（单机版）” (PhotoSwipe — Offline Edition). Package com.maodun.photoswipe, versionCode27, minSdk26 / targetSdk34, same signing certificate as prior V1, retaining local data through an in-place upgrade. Developer:RonMao.

Implementation: native Java, one Activity and15 product modules; aapt2/javac/d8/zipalign/apksigner, without Gradle or AndroidX. SQLite and SharedPreferences store local records, notes, places and settings; allowBackup=false means uninstalling or clearing data loses them. No accounts, photo uploads, public submissions/ratings or cloud sync. Photo-GPS reverse geocoding may use the system network service, without live device location access.

APK package, label, version and certificate were verified. The v1.9.16 baseline has45 host-layout and16 place-storage checks; no new on-device UI/gallery-export or side-by-side installation validation was performed. Compilation is not device testing.

This update publishes V1.9.17 only, not the unlaunched V2 development app. Earlier Releases and documentation remain available.
