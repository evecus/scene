# SceneClone

参考 Scene 9.3.8 功能的复刻项目（不复用其任何代码/资源）。UI: Jetpack Compose (M3) + libsu。

## M1 已完成
- 工程骨架（AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12）
- libsu root 层（`core/root/Root.kt`，读文件优先直读、失败降级 root cat）
- 快照采集（`core/proc/`）：/proc/meminfo、/proc/swaps、cpufreq policy、/proc/stat 差分（核负载/进程 CPU%）、GPU（GED/kgsl 探测）、power_supply uevent、thermal 温度、uptime
- 顶部四 Tab（功能/概览/调节/用户）；概览页：Memory 环形+进度条、GPU 环形、CPU top5 进程 + per-core 迷你柱状图（5 次采样历史）、底部功率/SDK/电池/开机/温度/SoC 卡片，1s 刷新
- 功能页骨架（分组卡片，路由占位）

## 构建
用 Android Studio 打开本目录（Gradle 8.9 / JDK 17），或：
```bash
gradle wrapper && ./gradlew :app:assembleDebug
```
注意：沙箱内无 Android SDK，未做过编译验证；如遇 API 细节报错按提示修正即可。

## M2 已完成
- `native/scene-rs`：Rust 采集守护（fps record / power log / dump / stop），JSONL 输出 + pidfile，`--bg` 后台化
- 交叉编译脚本 `native/scene-rs/build-android.sh`（NDK r27c, aarch64-linux-android, API 29），产物拷入 `app/src/main/assets/native/scene-rs`
- Kotlin 侧 `core/daemon/SceneRs.kt`：assets 释放 + chmod、root 启停、JSONL tail 读取

## M5 已完成
- `SceneRs` 会话化：`fps_<ts>.jsonl` / `power_<ts>.jsonl` + current 指针 + pidfile 存活检测（kill -0 / /proc）
- `Records`：kotlinx.serialization 解析 scene-rs JSONL
- `ui/record/LineChart`：自绘 Canvas 折线图
- `FpsRecordScreen`：开始/停止、FPS 曲线、Avg/Max/Min/Jitter、历史会话列表
- `PowerScreen`：电量% 与功率 W 双曲线、平均功率/平均电流/时长/电量变化、充电速度(%/h) 或理论续航(h) 自动区分
- 产物二进制已编译入 assets（aarch64, 340KB）

## M6 已完成
- scene-rs 新增 `charge guard --stop N --start N`（探测 battery/usb 的 input_suspend，3s 轮询电量，到点停充回落恢复，App 退出仍生效），已重编译入 assets（346KB）
- `ChargeScreen`：电池状态、input_suspend 暂停充电、charge_control_limit 限值、constant_charge_current_max 限流（探测式，不支持自动置灰）、充电保护守护启停
- `MiscScreen`：DPI（wm density）、机型修改（setprop/resetprop）、WiFi 密码查看（WifiConfigStore.xml 解析）、全局 dex2oat
- `BackupScreen`：分区列表（boot/dtbo/vbmeta/super…）dd 备份到 filesDir/backups、刷入（二次确认）、删除
- `PowerMenuScreen`：关机/重启/热重启(stop;start)/Recovery/Bootloader/EDL，全部二次确认；入口在「调节」Tab
- 功能页新增「进阶功能」分组

## 后续里程碑（见 /workspace/scene-clone-方案.md）
- M2 Rust scene-rs 工具链（cargo-ndk aarch64，fps/power JSONL 采集）✅
- M3 CPU 控制 + SWAP 设置页
- M4 进程管理 + 应用管理
- M5 帧率记录 / 耗电 / 充电统计（图表页）
- M6 充电控制 + 杂项 + 备份还原
