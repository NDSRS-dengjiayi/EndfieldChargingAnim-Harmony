# EndfieldCharge

一个将 ROM 原生的插拔充电动画替换为《明日方舟：终末地》（Arknights: Endfield）风格"超充模式"HUD 的 Xposed 模块，基于 **libxposed API 102**。

视觉效果 1:1 复刻自桌面端项目 [zmd-charge](https://github.com/QinAnze/zmd-charge)（Avalonia 实现）——胶囊几何形状、配色、16 条动画轨道乃至每一个 cubic-bezier 控制点均以该项目源码为规格移植。如果你喜欢这个效果，记得去给 zmd-charge 点一颗 Star。

> Fully Powered by DeepSeek V4.1 Flash.

## 功能特性

- **插入充电**：播放完整三段动画——闪电图标弹出、胶囊展开、涟漪扩散、标题与电量数字依次呈现
- **拔出充电**：播放简化的单次动画（仅胶囊与数字，无闪电与涟漪）
- 在类型为 `2026` 的 `WindowManager` 系统窗口中自绘 HUD，可显示于锁屏之上
- 核心层自行注册 `ACTION_BATTERY_CHANGED` 接收器识别插拔，不依赖 ROM 自身的事件管线
- 设置通过模块 App 的 `ContentProvider` 实时下发到 SystemUI，**无需重启 SystemUI**
- 在设置界面关闭模块后，充电动画立即完整交还 ROM
- 设置界面同时是预览器：以完全相同的 View 与时间轴在普通进程中播放、逐帧拖放、导出帧图
- 支持插电亮屏、拔出动画开关、动画时长/回弹/涟漪强度调节、中英文跟随系统语言

## 适配机型与 ROM

仅为以下机型及 ROM 特别适配并真机验证：

| 机型 | 设备代号 | ROM | 系统 |
| --- | --- | --- | --- |
| Redmi K60 | `mondrian_eea` | HyperOS `OS2.0.208.0.VMNEUXM` | Android 15 |
| OnePlus 15 | `CPH2747` | OxygenOS `16.0.9.400(EX01)` | Android 16 |

其它机型或 ROM 版本上，适配器探测会失败并放弃 Hook（"不匹配即不动作"），不会影响系统正常行为，但也不会有任何替换效果。

> **安装门槛说明**：APK 的 `minSdk` 为 31（Android 12），这只是让设置/预览 App 能旁加载到更多手机；充电动画 Hook 功能本身仅在上述 Android 15/16 机型上验证，且需要 LSPosed/libxposed 环境。

## 工作原理简述

模块只 Hook `com.android.systemui`（见 `META-INF/xposed/scope.list`）。各 ROM 的适配器只负责"让 ROM 不再画自己的充电动画"，其余一切（时间轴、渲染、窗口、插拔检测）均为 ROM 无关的共享核心。

**HyperOS** 存在两套互相独立的充电视觉：

| 插入时状态 | ROM 视觉 | 抑制方式 |
| --- | --- | --- |
| 锁屏显示中 | `MiuiChargeAnimationView` 全屏动画 | 将 `addChargeView()` 置空 |
| 已解锁 | 顶部通栏 `MIUIStrongToastControl` | 在 `strong_toast_category == "charge"` 时丢弃 `showCustomStrongToast(Bundle)` |

**OxygenOS 16** 则有三种不同机制：

| ROM 视觉 | 抑制方式 |
| --- | --- |
| `OplusChargeAnimationView` 全屏动画 | 按窗口标题拦截 `WindowManagerImpl.addView` |
| `Wired Charging Animation` 原生 AOSP 涟漪 | 同一窗口标题过滤 |
| 水波纹涟漪（独立 surface 上的 `SurfaceControlViewHost`） | 将 `OplusChargeAnimController.onWaterWaveVFXStart()` 置空 |
| 充电实时提醒胶囊（Pantanal 服务的 Seedland 卡片） | 在 `DecisionHelper.filterStaticServiceListByEntrance` 中按服务 ID 过滤 |

之后模块在与 ROM 原窗口分支相同的 `2026` 类型窗口中绘制自己的 HUD。因为接管后 ROM 的亮屏路径也被抑制，模块会通过反射调用隐藏 API `PowerManager.wakeUp()` 自行复刻插电亮屏行为。

## 项目结构

```
app/src/main/java/com/lemoneko/endfieldcharge/
  ModuleEntry.kt              libxposed API 102 入口
  core/                       ROM 无关层：窗口、电池、时间轴、设置
    timeline/                 移植而来的动画核心，关键路径不依赖 Android import
    settings/                 设置模型、JSON 编解码、Hook 侧通道
  rom/                        各 ROM 适配器，一个 ROM 一个文件
    hyperos/HyperOsAdapter.kt
    oxygenos/OxygenOsAdapter.kt
  ui/                         Canvas 渲染器、HUD 文案、字体、设置界面
  debug/                      离屏帧渲染器、sysfs 探测
  settings/                   SettingsProvider 与 App 侧通道
app/src/main/resources/META-INF/xposed/   Xposed 模块描述符与作用域声明
docs/                         可行性分析与 OxygenOS 逆向记录
tools/                        参考 HUD 逐帧抓取脚本
```

## 编译

### 环境要求

- **JDK 17**（AGP 8.9.1 要求；更高版本 JDK 未经验证）
- **Android SDK**：`platforms;android-36` 与 `build-tools;36.0.0`
- Gradle 8.12（已随 Wrapper 提供，无需单独安装）

如本机 SDK 不在默认位置，可在仓库根目录创建 `local.properties`（已被 git 忽略）：

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
```

### 构建命令

Windows（PowerShell，会话内临时指定环境变量）：

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

### 产物位置

| 任务 | 产物 | 说明 |
| --- | --- | --- |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` | 使用 debug 密钥自动签名，可直接安装 |
| `assembleRelease` | `app/build/outputs/apk/release/app-release-unsigned.apk` | 项目未配置 release 签名，产物未签名，需自行签名后才能安装 |

## 安装与启用

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

然后在 Xposed 管理器（LSPosed / Vector 等）中启用模块，作用域选择 `com.android.systemui`（`scope.list` 已预声明，多数管理器可直接识别），随后重启 SystemUI。

开发设备使用 Vector 时可通过 CLI 操作：

```bash
adb shell su -c '/data/adb/lspd/cli modules enable com.lemoneko.endfieldcharge'
adb shell su -c '/data/adb/lspd/cli scope set com.lemoneko.endfieldcharge com.android.systemui'
adb shell su -c 'killall com.android.systemui'
```

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| 替换 ROM 动画 | 开 | 关闭后动画立即交还 ROM |
| 顶部间距 | 8 dp | 位于屏幕挖孔下方（K60 挖孔高 34 dp 居中） |
| 宽度 | 屏幕的 92% | 560 设计单位 × 0.8 = 448 dp，宽于 411 dp 的屏幕 |
| 时长 | 6.0 s | 范围 3–10 s；入场段固定保持 2.52 s |
| 回弹 | 0.275 | BackOut 样条的过冲量 |
| 涟漪强度 / 扩散 | 1.0 | |
| 语言 | 自动 | 跟随系统语言 |
| 插电时点亮屏幕 | 开 | 复刻 ROM 的 `PowerManager.wakeUp` |
| 拔出时播放动画 | 开 | 简化的单次时间轴 |

设置不使用 libxposed remote preferences（其写入依赖框架管理器推送 binder，开发环境未安装管理器），而是经由模块 App 的 `SettingsProvider`（authority 为 `<包名>.settings`）下发：App 调用 `set` → Provider `notifyChange` → SystemUI 中的 `ContentObserver` 生效，即时切换语言等设置无需重启。

## 调试

```bash
# 离屏按每个 cue 渲染一张 PNG，用于与参考实现逐像素对比
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez renderSheets true
adb pull /sdcard/Android/data/com.lemoneko.endfieldcharge/files/frames

# 在指定帧打开预览，或直接播放拔出线动画
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ei cue 250
adb shell am start -n com.lemoneko.endfieldcharge/.ui.MainActivity --ez unplug true

# 不经过 UI，直接读写设置 Provider
adb shell sh /data/local/tmp/probe_settings.sh '<json>'
```

`tools/capture-reference-hud.ps1` 用于逐帧抓取参考 App 画面做 A/B 对比；时间轴不变量已移植为 JVM 单元测试（`./gradlew testDebugUnitTest`）。

## 免责声明

本项目仅作学习研究使用，不推荐尝试或传播，因此不会发布 Release。由此导致的任何问题概不负责——刷机不规范，数据两行泪。

## 许可证

- 模块代码：**AGPL-3.0**
- 内置 Inter 字体：SIL Open Font License（见 `app/src/main/assets/fonts/Inter-OFL.txt`）
