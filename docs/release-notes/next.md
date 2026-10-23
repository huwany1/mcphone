# 下一版本

## 简体中文

### 新增

- 设置中增加“更新日志”，支持滚动浏览分类内容、平台标注和链接；正文提供简体中文和英文，其他语言显示英文。
- 正式版首次打开手机时展示当前版本日志，出现即记为已查看；本机按账号和存档／服务器分别记录，同版本不会重复提醒。测试版仅支持手动查看。

### 修复

- 修复本地音乐与原版声音线程共享全进程 OpenAL 上下文导致的自动停播，初始化及播放改用线程局部隔离。
- 本地音乐在游戏卡顿耗尽缓冲后可恢复同一音轨；用户暂停、显式停止与真正播放结束仍保持原语义。

### 优化

- 美西螈私聊和群聊支持引用文字、图片摘要；输入栏提供可取消的引用预览，回复气泡显示作者及原文摘要。
- 引用只提交消息 ID，服务端按会话与可见性生成单层快照；引用回复不嵌套，图片引用不读取额外像素。
- 引用发送等待服务端回声，超时或本地目标失效保留正文；旧聊天记录可继续读取。
- 本次聊天线格式升级，客户端和服务端须同步更新。Forge 通道 13、NeoForge 通道 10；Fabric 相关正文 payload 更新 ID，群协议更新为 v5，并检查新旧端协议是否匹配。
- 固定旧消息 ID 的迁移种子，防止新增正文属性改变旧消息身份和个人删除标记。
- 引用提交按独立票据确认，超时重试复用同一次身份，避免重复落库及通知；等待期间改动过的草稿不会被旧回声清空。
- 私聊补发回声按消息 ID 去重；图片缓存增加元数据条数上限，被逐出的后台解码结果不再重新创建条目。

- 引用菜单补上圆环左引号图标，按参考图调整方向、尺寸及对齐，随主题文字着色；保留 Lucide 原稿与许可。

### 相关链接

- 查看 [GitHub 发布记录](https://github.com/november521/mcphone/releases)。

## English

### Added

- Added Changelog to Settings, with scrollable categories, platform labels and links. Notes are available in Simplified Chinese and English; other languages use English.
- Opening the phone on a new stable release shows that version's notes once. Viewing progress is stored locally per account and world/server, and is recorded as soon as the popup appears. Prerelease notes are available manually.

### Fixed

- Isolated local music playback from the process-wide OpenAL context used by the vanilla sound thread, preventing unexpected playback stops.
- Local music resumes the same track after a game stall exhausts its buffers, while preserving user pause, explicit stop and actual end-of-track behavior.

### Improved

- Axolotl private and group chats support replies to text and image summaries, with a cancellable input preview and an author/original-text preview in the reply bubble.
- Replies submit only a message ID. The server creates a single-level snapshot according to conversation visibility; replies do not nest or load extra image pixels.
- Reply drafts are kept when the server echo times out or the local target becomes unavailable. Existing chat history remains readable.
- [Forge 1.20.1 / NeoForge / Fabric] Clients and servers must update together. Forge channel is now 13, NeoForge channel 10; Fabric body payload IDs changed, group protocol is v5, and protocol compatibility is checked.
- Legacy message ID migration uses a fixed seed so new body properties cannot change old message identities or personal deletion markers.
- Reply sends use independent confirmation tickets. Retries reuse the original identity to avoid duplicate storage and notifications; late echoes cannot clear a newer draft.
- Private-chat echoes are deduplicated by message ID. Image-cache metadata has a bounded entry count, and evicted background decodes cannot recreate entries.
- The reply menu has an aligned quotation icon that follows the theme's text color. The original Lucide artwork and license are retained.

### Links

- Browse [GitHub releases](https://github.com/november521/mcphone/releases).
