# EndfieldChargingAnim

在插拔充电器时播放《明日方舟：终末地》（Arknights: Endfield）风格"超充模式"HUD 充电动画的 Android 应用。

本仓库在两个上游项目的基础上改编而来：

- **[NingmengLemon/EndfieldChargingAnim](https://github.com/NingmengLemon/EndfieldChargingAnim)** —— 本项目的代码基础。原项目是一个基于 **libxposed API 102** 的 Xposed 模块，通过 Hook `com.android.systemui` 替换 ROM 原生充电动画，已适配 HyperOS 与 OxygenOS。
- **[QinAnze/zmd-charge](https://github.com/QinAnze/zmd-charge)** —— 视觉规格来源（Avalonia 桌面端实现）。HUD 的胶囊几何形状、配色、16 条动画轨道乃至每一个 cubic-bezier 控制点，均以该项目源码为规格 1:1 移植到 Android Canvas。如果你喜欢这个效果，欢迎去给两个原项目点 Star。

本改编版在保留原有 Xposed 模式的同时，新增了一套**免 root 的独立运行模式**，使动画在无法安装 Xposed 框架、也无法 Hook SystemUI 的华为 EMUI/HarmonyOS 手机上同样可用（真机：HUAWEI OCE-AN10 / Mate 40E，HarmonyOS，Android 12）。

> Fully Powered by DeepSeek V4.1 Flash.

## 两种运行模式

| | 模式 A：Xposed 模块（原项目） | 模式 B：免 root 独立模式（本改编新增） |
| --- | --- | --- |
| 原理 | Hook SystemUI，抑制 ROM 动画并在系统窗口中自绘 HUD | 普通 App 监听充电广播，以前台 Activity 播放全屏 HUD |
| 是否需要 root / 框架 | 需要 LSPosed/libxposed | 不需要 |
| ROM 动画 | 被替换（ROM 自身动画不显示） | **无法抑制**，华为自带冒泡动画仍会出现 |
| 验证设备 | Redmi K60（HyperOS / Android 15）、OnePlus 15（OxygenOS 16 / Android 16） | HUAWEI Mate 40E（EMUI/HarmonyOS / Android 12） |
| 重启后 | 框架自动加载 | 需手动打开一次 App 启动监控服务 |

两种模式共用同一套渲染与时间轴核心（`core/` 与 `ui/EndfieldHudView.kt`），仅"事件来源与宿主窗口"不同。

## 功能特性

- **插入充电**：完整三段动画——闪电图标弹出、胶囊展开为"超充模式"面板、涟漪扩散、标题与电量数字依次呈现、停留、缩小收回
- **拔出充电**：简化动画——仅弹电量圆胶囊，内容快速显现后收回
- **电量显示**：百分比 + 电池容量（mAh）
  - Xposed 模式下读取电池 sysfs（`charge_full` / `charge_counter` / `voltage_now`）
  - 免 root 模式下 sysfs 不可读，改为：首次启动通过反射隐藏 API `PowerProfile.getBatteryCapacity()` 自动探测设计容量；探测失败回退为 **1000 mAh** 示例值；用户可在设置页手动修改总容量，**已充电量 = 总容量 × 百分比** 反推
- 插电亮屏、拔出动画开关、动画时长 / 回弹 / 涟漪强度调节、中英文跟随系统语言
- 设置界面同时是预览器：同一套 View 与时间轴在普通进程中播放、逐帧拖放、导出帧图
- Xposed 模式下设置经 `ContentProvider` 实时下发到 SystemUI，**无需重启 SystemUI**；关闭模块后动画立即完整交还 ROM

## 模式 A：Xposed 模块

### 适配机型与 ROM

| 机型 | 设备代号 | ROM | 系统 |
| --- | --- | --- | --- |
| Redmi K60 | `mondrian_eea` | HyperOS `OS2.0.208.0.VMNEUXM` | Android 15 |
| OnePlus 15 | `CPH2747` | OxygenOS `16.0.9.400(EX01)` | Android 16 |

其它机型或 ROM 上适配器探测失败即放弃 Hook（"不匹配即不动作"），不影响系统，但也没有替换效果。

### 工作原理

模块只 Hook `com.android.systemui`（见 `META-INF/xposed/scope.list`）。各 ROM 适配器只负责"让 ROM 不再画自己的充电动画"，其余（时间轴、渲染、窗口、插拔检测）为 ROM 无关的共享核心。

**HyperOS** 两套独立充电视觉的抑制方式：

| 插入时状态 | ROM 视觉 | 抑制方式 |
| --- | --- | --- |
| 锁屏显示中 | `MiuiChargeAnimationView` 全屏动画 | 将 `addChargeView()` 置空 |
| 已解锁 | 顶部通栏 `MIUIStrongToastControl` | 在 `strong_toast_category == "charge"` 时丢弃 `showCustomStrongToast(Bundle)` |

**OxygenOS 16** 的抑制方式：

| ROM 视觉 | 抑制方式 |
| --- | --- |
| `OplusChargeAnimationView` 全屏动画 | 按窗口标题拦截 `WindowManagerImpl.addView` |
| `Wired Charging Animation` 原生 AOSP 涟漪 | 同一窗口标题过滤 |
| 水波纹涟漪（`SurfaceControlViewHost`） | 将 `OplusChargeAnimController.onWaterWaveVFXStart()` 置空 |
| 充电实时提醒胶囊（Pantanal / Seedland 卡片） | 在 `DecisionHelper.filterStaticServiceListByEntrance` 中按服务 ID 过滤 |

随后模块在类型为 `2026` 的系统窗口中绘制自己的 HUD，并通过反射隐藏 API `PowerManager.wakeUp()` 复刻插电亮屏。

## 模式 B：免 root 独立模式（华为适配）

### 组件与链路

```
充电广播 ──► StandaloneMonitorService（常驻前台服务，进程不被冻结）
              └─ 服务内动态注册的 BroadcastReceiver 收到 ACTION_POWER_CONNECTED/DISCONNECTED
                   └─ StandaloneLauncher ──► StandaloneChargeActivity（全屏、可显示于锁屏之上）
                                                └─ EndfieldHudView 播放动画
```

### 为绕过 EMUI 限制所做的关键处理

1. **常驻前台服务 + 动态接收器**：实测华为 iaware 电源管理会把后台冻结应用的隐式充电广播直接丢弃，清单接收器在桌面/其他 App 下根本不会被唤起。前台服务（FGS）进程免于冻结，其动态注册的接收器随活进程同步收到事件。服务伴随独立模式开关启停，通知栏有一条最低优先级的常驻通知。
2. **悬浮窗权限（`SYSTEM_ALERT_WINDOW`）**：仅有前台服务仍不够——EMUI 会以 `activity start fail: Background activity start` 静默拒绝后台拉起界面（比原生 Android 更严格）。授予"显示在其他应用上层"权限后获得 BAL 豁免，桌面、其他 App、熄屏状态均可弹出 HUD 并自动亮屏。
3. **事件去抖**：清单接收器作为兜底保留，与服务内动态接收器可能同时命中，2 秒内的重复事件会被忽略。
4. **容量数据**：无 root 无法读取 `/sys/class/power_supply/`（华为节点名为大写 `Battery` 且权限封闭），因此采用 PowerProfile 探测 + 用户可改总容量 + 百分比反推的方案。

### 限制

- **无法隐藏华为系统自带的充电动画/冒泡提示**——抑制 SystemUI 动画是 Xposed/系统权限才能做到的事。
- 不做开机自启保证：**手机重启后手动打开一次 App**（开关保持打开即会重新启动监控服务），之后即可正常使用。
- 熄屏深睡时的拔出广播投递属于尽力而为，不保证 100% 送达。

## 编译

### 环境要求

- **JDK 17**（AGP 8.9.1 要求）
- **Android SDK**：`platforms;android-36` 与 `build-tools;36.0.0`
- Gradle 8.12（已随 Wrapper 提供）
- `minSdk` 为 31（Android 12）：仅表示设置/预览/独立模式 App 可安装到 Android 12+；Xposed Hook 仍只在上述 Android 15/16 机型验证

如本机 SDK 不在默认位置，在仓库根目录创建 `local.properties`（已被 git 忽略）：

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

### 构建命令

Windows（PowerShell）：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

macOS / Linux：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # 仅 macOS 示例
./gradlew testDebugUnitTest assembleDebug
```

> 国内网络若卡在 `dl.google.com` 下载依赖超时，可在 `~/.gradle/init.gradle` 配置阿里云 Maven 镜像（google/central 镜像），仓库构建文件本身无需改动。

### 产物

| 任务 | 产物 | 说明 |
| --- | --- | --- |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | debug 密钥签名，可直接安装 |
| `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` | 未配置 release 签名，需自行签名 |

## 安装与启用

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**模式 A（Xposed）**：在 LSPosed / Vector 等管理器中启用模块，作用域选择 `com.android.systemui`，重启 SystemUI。

```bash
adb shell su -c '/data/adb/lspd/cli modules enable com.lemoneko.endfieldcharge'
adb shell su -c '/data/adb/lspd/cli scope set com.lemoneko.endfieldcharge com.android.systemui'
adb shell su -c 'killall com.android.systemui'
```

**模式 B（免 root，华为等）**：

1. 打开 App，打开 **Enable standalone charge animation** 开关，按提示授予通知权限
2. 点击 **Display over other apps: GRANT (required)**，在系统设置中允许"显示在其他应用上层"（**必授，否则后台无法弹出**）
3. 建议同时：Battery 按钮中允许后台活动；应用信息 → 电池 → 启动管理中放开自启动/后台活动
4. 可用 **Test charge / Test unplug** 立即验证
5. 手机重启后重新打开一次 App 即可

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| 替换 ROM 动画（Xposed） | 开 | 关闭后动画立即交还 ROM |
| Enable standalone charge animation | 关 | 免 root 模式总开关，同时启停监控服务 |
| battery total capacity (mAh) | 自动探测 / 失败 1000 | 独立模式电池总容量，可手动保存或重新 Auto detect |
| 顶部间距 | 8 dp | |
| 宽度 | 屏幕的 92% | |
| 时长 | 6.0 s | 范围 3–10 s |
| 回弹 | 0.275 | BackOut 样条过冲量 |
| 涟漪强度 / 扩散 | 1.0 | |
| 语言 | 自动 | 跟随系统 |
| 插电时点亮屏幕 | 开 | |
| 拔出时播放动画 | 开 | |

## 项目结构

```
app/src/main/java/com/lemoneko/endfieldcharge/
  ModuleEntry.kt              libxposed API 102 入口（模式 A）
  core/                       ROM 无关层：窗口、电池、时间轴、设置
    timeline/                 动画核心，关键路径不依赖 Android import
    settings/                 设置模型、JSON 编解码、Hook 侧通道
  rom/                        Xposed 模式各 ROM 适配器
    hyperos/HyperOsAdapter.kt
    oxygenos/OxygenOsAdapter.kt
  standalone/                 免 root 独立模式（模式 B）
    StandaloneMonitorService.kt   常驻前台服务 + 动态充电广播接收器
    ChargeEvents.kt               插/拔事件共用处理（含去抖）
    ChargeEventReceiver.kt        清单接收器（兜底）
    BootReceiver.kt               开机/解锁兜底接收器
    StandaloneChargeActivity.kt   全屏 HUD 宿主（真实电量 + 配置容量）
    StandaloneLauncher.kt         后台拉起 HUD
    StandaloneNotifier.kt         全屏意图通知兜底
    StandaloneScreen.kt           亮屏/解锁
    StandalonePrefs.kt            开关与电池容量存取、PowerProfile 探测
  ui/                         Canvas 渲染器、HUD 文案、设置/预览界面
  debug/                      离屏帧渲染器、sysfs 探测
  settings/                   SettingsProvider 与 App 侧通道
app/src/main/resources/META-INF/xposed/   Xposed 模块描述符与作用域声明
docs/                         可行性分析与 OxygenOS 逆向记录
tools/                        参考 HUD 逐帧抓取脚本
```

## 调试

```bash
# 离屏按每个 cue 渲染一张 PNG，与参考实现逐像素对比
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez renderSheets true
adb pull /sdcard/Android/data/com.lemoneko.endfieldcharge/files/frames

# 指定帧预览 / 直接播放拔线动画
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ei cue 250
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez unplug true

# 独立模式监控服务状态
adb shell dumpsys activity services com.lemoneko.endfieldcharge
adb shell appops get com.lemoneko.endfieldcharge SYSTEM_ALERT_WINDOW
```

时间轴不变量已移植为 JVM 单元测试：`./gradlew testDebugUnitTest`。

## 免责声明

本项目仅作学习研究使用，不推荐尝试或传播，因此不会发布 Release。

**作者及贡献者不对因使用（或无法使用）本项目而造成的任何直接或间接后果负责**，包括但不限于：

- 手机变砖、无法开机、系统异常、数据丢失、硬件损坏；
- 电池异常（鼓包、过热、加速老化等）、充电故障；
- 因解锁 Bootloader、刷入框架、授予高级权限、修改系统设置导致的保修失效；
- 免 root 模式下常驻前台服务带来的额外耗电，以及与系统电源管理策略冲突引起的任何异常；
- 任何第三方 ROM、Xposed 框架、本软件之间兼容性问题引发的故障。

刷机会改变设备状态，存在不可逆风险，操作前请务必备份数据并自行评估。**一旦安装或使用本项目，即视为你已理解并自愿承担全部风险；作者及贡献者不承担任何责任，也没有义务提供修复或赔偿。**

## 许可证

- 模块 / 应用代码：**AGPL-3.0**
- 内置 Inter 字体：SIL Open Font License（见 `app/src/main/assets/fonts/Inter-OFL.txt`）
