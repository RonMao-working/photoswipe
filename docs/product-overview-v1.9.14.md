# 相册滑选 · 产品概述（v1.9.14）

原生 Android 单机照片整理 App。用“滑卡片”的方式把上万张照片过一遍：左滑待删、右滑保留、上滑待星，全部先暂存，结算确认才真正生效。

## 1. 它解决什么问题

手机相册越攒越多，逐张点开、勾选、删除很慢，而且一旦误删很难回头。相册滑选把这件事拆成两层：滑选时只做判断、不落刀，把风险留给结算页统一确认；同时用本地账本记住"这张已经看过"，下次打开接着滑，不重复。

## 2. 三个常驻 Tab

顶部常驻「我的 / 滑选 / 结算」三个等宽 Tab，任何页面都能一键切换，不再依赖页面内的返回按钮。

- **我的**：收藏相册入口、设置、版本与开发者信息。
- **滑选**：主战场，一次一张，背后预载下一张。
- **结算**：本轮待删、待星标、保留三张清单与确认动作。

## 3. 滑选手感

| 动作 | 结果 |
|---|---|
| 左滑 | 标记待删（可设置与右滑互换） |
| 右滑 | 标记保留 |
| 上滑 | 标记待星标 |
| 双指开合 | 1–4 倍缩放，放大后单指平移，轻点复位 |
| 单击画面 | 不弹信息，避免误触 |
| 点时间/地点/设备文字行 | 弹出照片详情 |

判定参数（v1.9.14）：横向位移过 18% 卡宽、纵向过 12% 卡高即成交；主方向判定 1.25；另有甩动成交——位移过 dp(24) 且速度过 dp(300)（约 790px/s）、方向与位移一致就成交，轻扫也能过。拖动纵向 1:1 跟手；正片沿释放方向 240ms 退场，背后备选卡 60ms 淡入且起点不透明度 0.72，连续滑动不停顿。

底部固定三个主按钮（删除 / 星标 / 保留）居中，左下角另有一个很小的回转箭头做撤销；每次操作后浮条提示「已标记待删」「已标记待星标」「已保留」，6 秒内可撤销。

## 4. 结算

结算页把本轮动作分成三张清单，缩略图统一用同一个 20dp 圆角框，红叉与五角星是同一套绘制。可以「永久删除 N 张」「确认星标 N 张」「继续滑选」「放弃本轮的标记」。

- 删除是**永久删除**，不进入系统「最近删除」，App 内也无法恢复；确认前会明确提示。
- 星标是**收录到 App 内的收藏相册**，不动系统相册的收藏标记。
- 保留只是记账，不改动原文件。
- 没确认就退出，本轮删除与星标都不生效，记录留到下次打开的这一页。

## 5. 我的：收藏相册与独家记忆

- 收藏相册每页固定两张、上下排列，按拍摄时间升序，默认落在最近一页；相册纸框、角贴、叠层阴影保持拟物感，图与框留白收得很紧以放大画面。
- 点开单张进入「独家记忆」：纸框内的等比原片、时间到秒、拍摄地点、最多 40 字的真实故事（自动保存）；支持双指 1–4 倍缩放；右下角是下载图标而不是文字按钮，点一下把这一页导出为 1080px 宽 PNG，落到 `Pictures/相册滑选`，只含原片、时间、地点、故事与纸框，不含任何操作控件。
- 日历与时间轴：日期窗口 36 个自然月，拖动时间轴时相册按目标日期快速翻页，松手直接落到当天；左右滑动翻页方向遵循"左滑更近、右滑更早"。
- 顶部支持黑白两套主题，设置页可切换手势方向、提示策略，并显示版本 `1.9.14` 与「开发者：RonMao」。

## 6. 数据与安全

- **只在本地**：无账号、无 UID、无上传、无分享。唯一联网例外是照片 GPS 反向地理编码，交给系统 Geocoder，结果本地缓存；不读取手机实时位置。
- **去重**：URI、稳定签名、全文件 SHA-256 三重比对；改名副本可跳过，压缩或编辑后的版本不保证判为同一张。
- **账本**：SQLite `review_ledger.db` 记录已看过与已删除，覆盖安装保留；`allowBackup=false`，卸载或清数据即丢失本地记录。
- **签名**：同包同签名覆盖安装才保留数据；换机自签包不能覆盖，切勿为升级卸载。

## 7. 技术实现

- 原生 Java，单 Activity（`MainActivity`），14 个产品模块；minSdk 26 / target34，竖屏。
- 不依赖 AndroidX、Gradle、任何 native 库；构建链是 `aapt2 → javac(Java 8) → d8 → zipalign → apksigner`，一条 `build.sh` 出包。
- 图像走系统 `ImageDecoder` 与缩略图接口，按 EXIF 1–8 处理旋转与镜像；Live Photo / 动图按系统能力播放。
- v1.9.14 签名包 115,736 字节；API34 模拟器 43 项专项（含手势 3 项）与 9 项真实 UI 通过。

## 8. 已知边界

- 只在 API34 模拟器做回归；荣耀 Magic7 / MagicOS 10 / Android 16 未接入 adb，真机手感与厂商图库行为未全面验证。
- 甩动成交只有 `decide` 层数值断言：adb 合成滑动算不出可信速度（实测出现 0 / 217 / -1232px/s 甚至反向），无法端到端复现手指甩动。
- 全球县区级地名覆盖取决于系统地理服务，不保证；无 GPS 时显示经纬度或明确状态。
- 2.x 的联网分享仅在规划中，未实现。

---

# PhotoSwipe · Product Overview (v1.9.14)

A native Android, fully offline photo triage app. You go through thousands of photos as a stack of cards: swipe left to queue a delete, swipe right to keep, swipe up to queue a favorite. Nothing happens until you confirm in the settlement screen.

## 1. What problem it solves

Phone albums grow faster than anyone can clean them. Tapping into each photo to select and delete is slow, and one wrong tap is hard to undo. PhotoSwipe separates judging from acting: swiping only records an intent, and the settlement screen is the single place where anything is committed. A local ledger remembers what you already reviewed, so the next session continues where you stopped instead of showing the same photos again.

## 2. Three persistent tabs

「My」「Swipe」「Settlement」(我的 / 滑选 / 结算) sit permanently at the top as equal-width tabs, so you can jump between screens at any time without relying on in-page back buttons.

- **My** — collection album entry, settings, version and developer info.
- **Swipe** — the main surface: one card at a time, next card preloaded behind it.
- **Settlement** — three lists (to delete, to star, kept) plus the confirm actions.

## 3. Swipe gestures

| Gesture | Result |
|---|---|
| Swipe left | Queue for deletion (direction can be swapped in settings) |
| Swipe right | Mark as kept |
| Swipe up | Queue as favorite |
| Pinch | 1–4× zoom, pan with one finger, tap to reset |
| Tap the photo | Nothing — prevents accidental info popups |
| Tap the time / place / device lines | Opens photo details |

Commit thresholds (v1.9.14): 18% of card width horizontally, 12% of card height vertically, direction dominance 1.25. A flick also commits — displacement past dp(24) with velocity past dp(300) (≈790 px/s) in the same direction. Vertical drag tracks the finger 1:1; the leaving card exits in 240 ms along the release direction while the card behind fades in over 60 ms starting at 0.72 opacity, so consecutive swipes never stall.

Three primary buttons (delete / star / keep) stay centered at the bottom, with a very small undo arrow at the bottom-left. After each action a bar shows「已标记待删」「已标记待星标」「已保留」and stays undoable for 6 seconds.

## 4. Settlement

This round's intents become three lists whose thumbnails share one 20 dp rounded frame, with the red cross and the star drawn by the same module. Actions: permanently delete N, confirm N favorites, continue swiping, or abandon this round's marks.

- Deletion is **permanent** — it does not go to the system "Recently Deleted", and the app cannot restore it. The confirmation says so explicitly.
- Starring **adds the photo to the app's own collection album** and does not touch the system album's favorite flag.
- Keeping only records a decision; the file itself is untouched.
- Leaving without confirming cancels both deletions and stars; the marks wait for your next visit to this page.

## 5. My: collection album and memory notes

- The collection album shows exactly two photos per page, stacked vertically, in ascending capture order and defaulting to the most recent page. Paper frames, corner tabs and layered shadows keep the physical-album feel, while the padding between frame and photo is kept tight so images render larger.
- Tapping a photo opens "your memory": the original at its own aspect ratio inside a paper frame, time to the second, capture location, and up to 40 characters of your own story (auto-saved). Pinch zooms 1–4×; the bottom-right control is a download icon rather than a text button, exporting this page as a 1080 px-wide PNG into `Pictures/相册滑选` — containing only the photo, time, place, story and frame, with no UI controls.
- Calendar and timeline cover 36 calendar months. Dragging the timeline flips the album rapidly toward the target date and lands directly on that day when released; normal page turns keep "left = newer, right = older".
- Light and dark themes are both supported. Settings let you swap swipe directions and choose prompt behavior, and show version `1.9.14` with「开发者：RonMao」.

## 6. Data and safety

- **Local only**: no account, no user ID, no upload, no sharing. The single network exception is GPS reverse geocoding delegated to the system Geocoder, cached locally; the app never reads your live location.
- **Deduplication**: URI, stable signature and full-file SHA-256 are all compared. Renamed copies can be skipped; compressed or edited variants are not guaranteed to count as the same photo.
- **Ledger**: SQLite `review_ledger.db` records reviewed and deleted items and survives reinstalls on top; `allowBackup=false`, so uninstalling or clearing data loses the local records.
- **Signing**: data is preserved only when a new build is installed over the old one with the same package and signature. A self-signed build from another machine cannot overwrite it — never uninstall just to upgrade.

## 7. Implementation

- Native Java, a single Activity (`MainActivity`) and 14 product modules; minSdk 26 / target34, portrait.
- No AndroidX, no Gradle, no native libraries. The build chain is `aapt2 → javac (Java 8) → d8 → zipalign → apksigner`, driven by one `build.sh`.
- Imaging uses the system `ImageDecoder` and thumbnail APIs, honoring EXIF orientations 1–8 including mirroring; Live Photos and animated images play according to system capability.
- The v1.9.14 signed APK is 115,736 bytes. On the API 34 emulator, 43 targeted checks (3 of them gesture assertions) and 9 real-UI checks pass.

## 8. Known limits

- Regression runs only on the API 34 emulator; the Honor Magic7 / MagicOS 10 / Android 16 device is not reachable over adb, so real-device feel and vendor gallery behavior are not fully verified.
- Flick-to-commit is asserted only at the `decide` level: synthetic adb swipes do not yield trustworthy velocity (measured 0 / 217 / −1232 px/s, even reversed), so a finger flick cannot be reproduced end to end.
- Worldwide district-level place names depend on the system geocoding service and are not guaranteed; without GPS the app shows coordinates or an explicit status.
- The 2.x online sharing feature is planned only; it is not implemented.
