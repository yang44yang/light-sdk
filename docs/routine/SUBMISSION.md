# Routine — Tool Library 提交资料

更新日期：2026-10-10。候选版本：**1.3.1 / versionCode 8**。

**结论：可以进入官方提交流程。当前完成的是本地开发与验收，尚未公开最新源码、运行官方构建或获得审核通过。**

本文件是可复制的文案和操作清单，尚未发送给 Light。Dashboard 的具体字段、字符限制和图片裁剪规则尚未逐项核对，按实际表单调整。

## 1. 当前准备程度

| 项目 | 状态 |
| --- | --- |
| 功能与界面 | Habits / To-dos 分类、难度、奖励兑换与历史、使用引导、About 已实现 |
| 语言 | 英文、简体中文、繁体中文跟随系统 |
| 本地验证 | 52 项 instrumentation 测试通过；官方 allowlist 提取 24 个文件且内容匹配；干净官方 SDK 的优化 arm64 构建与 lint 通过，仍有两项警告 |
| 真机验证 | 已安装到 LP3；更新前后可见任务、记录数、分类数量、gold 与累计统计一致；About 和类型提示已检查 |
| 公开资料 | 本文案、隐私说明、用户指南、中性演示截图已准备 |
| 最新公开源码 | **待发布**；2026-10-10 核对公开 fork 的 main，仍是 Habits 1.0 / code 1，并指向模拟器 |
| 官方服务器构建与签名 | **未运行**；当前安装的是开发签名 APK |
| Tool Library 审核 | **未提交、未获批** |

建议提交前补一次真机奖励兑换与 Undo 的完整流程。该流程已通过模拟器验证，之前真机只检查了列表、历史和返回，没有实际兑换用户的奖励。

**已知兼容性限制：** 测试的 `582-release-lp3` File Manager 不展示第三方工具目录。Routine 可以在手机本地生成 CSV / JSON，但无法通过该版本的 File Manager 下载到电脑或上传备份。真机端到端备份恢复尚未验收，不能宣传为已可用的跨设备备份。模拟器上的文件传输与恢复已经验证。

这项限制应如实告知审核方；官方没有明确规定它必然导致拒绝。是否接受这个候选版本由 Light 判断。

## 2. 可复制的应用介绍（英文）

### Name

```text
Routine
```

### Short description

```text
Build small habits and record everyday progress.
```

### Description

```text
Routine helps you turn a goal into small actions you can record on your Light Phone. Track habits you want to build or reduce, keep a short list of one-time tasks, and view your counts for the week, month, or year.

Separate Habits and To-dos tabs keep each list easy to reach. Choose Easy, Medium, or Hard for a habit's future positive records. An optional local gold counter lets you set your own rewards, redeem them, and look back at your reward history. Gold has no monetary value.

A short, skippable introduction explains positive and negative records using Strength training, No alcohol, and No food after 8pm. Help remains available from the home screen. Daily goals are recorded manually; Routine does not automatically mark successful days.

Routine works without an account. There are no ads, feeds, analytics, reminders, or streak penalties. English, Simplified Chinese, and Traditional Chinese follow your system language. Your records are stored in an app-private database; Routine does not send them to the developer.

CSV reports and full JSON backups can be created locally. Transfer through LightOS File Manager requires third-party tool directory support, which is unavailable on the tested 582-release-lp3 firmware. Off-device backup and restore on a compatible real-phone firmware have not yet been verified.

Author / X: @BitVendetta — https://x.com/BitVendetta
Feedback: yang.yang.pro@gmail.com
Source: https://github.com/yang44yang/light-sdk
```

作者信息已获作者授权。如果 Dashboard 有公开作者 / 联系方式字段，可分别填写；若没有，保留在介绍末尾。字段是否公开、是否存在尚未核对。手机内也可从 **Help → About** 查看这些静态文字。

### 中文说明（供你审阅）

把目标化成小事，每天简单记录。Routine 支持正向与负向习惯、一次性待办、周 / 月 / 年统计，以及自定义奖励兑换。习惯和待办分开展示，正向记录可选择三档难度。无需账号，英文和中文跟随系统。备份文件能在手机本地生成，但测试的 v582 不支持通过 File Manager 将它们传到电脑。

## 3. 提交参数与附件

| 参数 | 填写内容 |
| --- | --- |
| Public GitHub repository | `https://github.com/yang44yang/light-sdk`，先发布本次源码到默认分支 |
| Package ID | `io.github.yang44yang.habits`，保持现有 ID；应用显示名是 Routine |
| Version name / code | `1.3.1` / `8`；若 Dashboard 已有 code ≥ 8，先递增并重新构建验收 |
| SDK | `0.2.0`；2026-10-10 核对官方 main，与本地一致 |
| Server package | `com.lightos` |
| Tool permissions / capability | `[]` / `["tool-manager-provider"]`；SDK 继承权限见隐私说明 |
| Build git ref | **待填写：发布后的完整 commit SHA**，不要填未发布的本地分支状态 |
| License | MIT，保留原 SDK 的许可证 |
| Feedback email | `yang.yang.pro@gmail.com` |
| Author / X | `@BitVendetta` / `https://x.com/BitVendetta` |

附件：

- [隐私说明](PRIVACY.md)
- [用户指南](USER_GUIDE.md)
- [验证记录及开发说明](README.md)
- [File Manager 兼容性调查](FILE_MANAGER_DIAGNOSIS.md)
- [截图目录与来源说明](screenshots/README.md)

若表单要求隐私说明或指南的公开 URL，在源码发布后使用：

```text
https://github.com/yang44yang/light-sdk/blob/main/docs/routine/PRIVACY.md
https://github.com/yang44yang/light-sdk/blob/main/docs/routine/USER_GUIDE.md
```

这些是待发布页面的目标地址，当前尚不能视为已经公开的最新资料。

### 推荐提交截图

| 顺序 | 文件 | 展示内容 |
| --- | --- | --- |
| 1 | [home-en.png](screenshots/home-en.png) | 首页习惯、方括号数量和分类切换 |
| 2 | [home-todos-en.png](screenshots/home-todos-en.png) | 独立待办列表 |
| 3 | [welcome-en.png](screenshots/welcome-en.png) | 正向 / 负向习惯的首次使用介绍 |
| 4 | [rewards-en.png](screenshots/rewards-en.png) | 自定义奖励与直达兑换 |
| 5 | [reward-receipt-en.png](screenshots/reward-receipt-en.png) | 兑换结果与 Undo |

官方要求至少一张图；以上五张是建议组合。均为官方 SDK 模拟器中独立测试用户的中性演示数据，1080 × 1240，未经修饰。引导图来自 1.3.1；首页图来自 1.3.0，奖励图来自 1.1.0，对应界面在当前版本保持一致。其他语言、About 和难度截图可作补充。上传后检查 Dashboard 预览及裁剪。

不要提交私人手机照片、真实记录、备份、配对令牌、开发密钥或 `local/` 下的验收文件。

## 4. 可复制的构建说明与审核备注（英文）

### Build changelog

```text
Routine 1.3.1 provides local habit and to-do tracking for Light Phone III, separate category tabs, three difficulty levels for positive records, personal reward redemption with receipts and undo, and reward history. English and Chinese follow the system language. This release adds concrete positive and negative habit examples, editor type explanations, and Help → About with author and feedback details. CSV and JSON files can be generated locally; file transfer depends on LightOS support for third-party tool directories.
```

### Reviewer notes

若表单没有备注字段，保留在公开文档供审核阅读；不要假定存在该字段。若 Light 要求补充资料，再通过其指定渠道提供。

```text
Tested candidate: Routine 1.3.1, versionCode 8, on Light Phone III running 582-release-lp3. The optimized arm64 release builds from sources extracted through the official builder allowlist. 52 instrumentation tests pass. A same-development-certificate phone update preserved the collected visible task names, record counts, category sizes, gold, and lifetime totals.

Known limitation: this firmware's File Manager does not expose third-party tool directories. CSV and JSON creation works locally, but off-device transfer and real-phone restore remain unverified. These operations were verified on the SDK emulator. Reward redemption and undo were also verified on the emulator; the physical-phone check opened Rewards and History without redeeming a real reward.

An earlier user-reported not-responding incident could not be diagnosed from an actual ANR trace. Subsequent short phone checks recorded no additional ANR exit entry; this is not a claim of long-term stability.

Local lint completes with two remaining warnings: the exported SDK file provider and the missing generated application icon. The provider's runtime system-UID access check is documented in the privacy information. The official hosted build and Light-signed APK still need verification.

Suggested review flow: read or skip the introduction; create a Strength training Habit +; create a No alcohol Habit ± and read the type explanation; switch between Habits and To-dos; select difficulty; record progress; create and redeem a reward, inspect its receipt, undo it, then open History. Open Help → About for the public feedback contact. Use test data for the review.
```

## 5. 实际提交步骤

1. **发布源码。** 审阅待提交改动，仅提交应用、所需构建配置、测试及公开文档。排除凭证、个人数据和本地验收文件。将最终 Routine 源码发布到公开 fork 的默认分支；重新确认 `tool/lighttool.toml` 是 Routine / `com.lightos`，记录完整 SHA。官方注册时从默认分支读取 package ID，注册后不可更改。
2. **开启开发者账号。** 打开 [Light Dashboard](https://dashboard.thelightphone.com/)，进入 **Settings → Account → Developer Account**。
3. **注册工具。** 进入 **Manage Custom Tools → Submit New Tool**，提供公开仓库；按表单填写上面的应用介绍和联系资料，上传至少一张截图。若工具已注册，打开现有工具，不重复注册。
4. **提交官方构建。** 在工具详情页选择 **Submit build**，填写最终公开 commit SHA 和 changelog。先检查 versionCode 高于该工具上一次版本，且没有正在运行的构建。
5. **查看结果。** 失败时查看 **Builds → View details**，根据报错处理；**Retry build** 可重试同一 git ref。修改源码后，应使用新的公开 SHA。
6. **验收官方签名 APK。** 构建成功会出现 **Download APK**，新版本进入 **pending approval**。先核对官方签名与当前开发签名的差异及保留数据的安装路径，避免默认卸载现用版本；再验证正常使用流程。
7. **等待审核并处理反馈。** 官方通过邮件沟通审核意见。构建成功、获得 Light 签名和进入 Tool Library 是不同状态；能被其他用户使用或如何分发，以最终审核状态及平台功能为准。

## 6. 提交前的最后清单

- [ ] 真机补测奖励兑换 → receipt → Undo → History，并确认 gold 正确恢复。
- [ ] 审阅本文案、隐私说明及截图；如实保留 v582 文件传输限制。
- [ ] 将最新源码与公开资料发布到默认分支，确认没有私人文件。
- [ ] 核对 Dashboard 现有工具、上次 versionCode 和实际表单字段。
- [ ] 记录最终公开 commit SHA，提交官方构建。
- [ ] 官方构建成功后验收其签名 APK，跟进 pending approval 与邮件反馈。

提高通过机会的重点：清楚说明工具帮助用户完成什么、沿用 Light 的简洁界面、披露数据存储和兼容性限制，并提供可复现的审核流程。不存在可保证通过的文案或测试数量。

官方依据（2026-10-10 核对）：

- [Submitting Your Tool](https://github.com/lightphone/light-sdk#submitting-your-tool)
- [Tool Library Guidelines](https://github.com/lightphone/light-sdk/blob/main/TOOL_GUIDELINES.md)
- 官方 SDK main commit：`311aa2d81e03e7df2603fa3943d05e4aad51d29b`（2026-10-08）；最新要求以官方页面为准。
- 官方开发问题邮箱：`tools@thelightphone.com`。本次仅准备资料，未发送邮件。
