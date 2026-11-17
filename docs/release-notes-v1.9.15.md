# 相册滑选 v1.9.15 · 独家记忆地点编辑

## 中文

- 原片确认无 GPS 时，独家记忆地点行可点击填写，保存后再次打开保留，可修改、清空或取消；手写地点只存本地，不改原图或 GPS。
- 照片时间去掉“文件时间”后缀，保留日期和时分秒。
- 无 GPS 且未填写地点时，下载 PNG 不显示无 GPS 提示，也不保留地址空行；填写后输出手写地点。有 GPS 的照片保留原有地名／坐标逻辑。
- 地点编辑仅针对已授权照片位置且 EXIF 读取成功的无 GPS 原片；未授权／读取失败仍提供授权或重试。
- versionCode 25 / versionName 1.9.15。同包同签名覆盖安装；不要卸载或清数据。

验证：构建、验签和 16 项主机 JVM 地点存储测试通过；UI／真实相册下载专项编译但未运行，模拟器被本机虚拟化权限阻止。本版手机编辑、实际下载与完整回归仍待验收。

## English

- Edit a memory page's location when the original is confirmed to have no GPS. Saved places survive reopening; revise, clear, or cancel. Captions are local and do not change the original or GPS metadata.
- Remove the “文件时间” (file time) suffix, keeping the date and time to the second.
- A no-GPS photo with no manual place exports without GPS-missing text or an empty address line. Written places are included; existing GPS place names / coordinates remain unchanged.
- Manual editing requires granted photo-location permission and successful EXIF reading. Permission / read failures keep their permission or retry behavior.
- versionCode 25 / versionName 1.9.15. Upgrade over the existing installation with the same package and signature; do not uninstall or clear app data.

Verification: build, signature verification, and 16 host-JVM manual-location storage checks passed. UI / real-gallery download instrumentation compiled but did not run because virtualization permissions blocked the emulator. Real-device editing, downloads, and the complete regression suite remain unverified for this version.

## APK

- File: `相册滑选-v1.9.15.apk`
- Size: `119,832 bytes`
- SHA-256: `2b32bdd132a8f437250dd7a41c88fc8e38f3cbfaf743b39a0ceba60cfdf50ee5`

删除是永久删除，重要照片请先备份。Deletion is permanent; back up important photos first.
