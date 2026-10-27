# 使用时间线 (UsageTimeline)

一个轻量的 Android 应用使用时间查看器。以**时间线**形式查看什么时候打开了哪个应用、用了多久，点进应用可查看完整使用记录，并统计一天的总使用时长。

界面完全基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（Compose Multiplatform 的 MIUI 风格组件库）构建。

## 功能

### 时间线
- 按「小时」分组展示当天的每一次应用切换：开始时间、应用图标、时长、结束时间
- 顶部「全天分布」色带：横向一览 24 小时内的活跃区间，不同应用固定配色，色带稳定可辨
- **默认倒序**（从晚到早），一键切换正序 / 倒序
- **搜索应用**：点顶部搜索图标展开，按应用名称或包名即时过滤记录；展开时自动弹出输入法
- 每小时小标题显示该小时累计使用时长
- 底部「每小时使用时长」柱状图

### 应用
- 当天所有应用按使用时长（或打开次数）排序，带占比百分比和进度条
- 支持按名称 / 包名搜索

### 统计
- 总使用时长、应用数、打开次数、亮屏次数的概览卡片
- 24 小时柱状图
- 使用分布：使用最多的应用、打开最多次数、解锁次数、平均每次时长、最活跃时段
- 应用排行（前三名高亮）
- 使用时长占比堆叠条 + 图例

### 应用详情
- 应用信息卡片：总时长、打开次数、平均每次、首次打开时间
- 一键「打开应用」/「应用信息」
- **当天 / 近 7 天**切换，查看该应用的完整使用记录，逐条列出精确到秒的起止时间
- 当天时段分布柱状图（使用该应用的主题色）

### 其他
- **下拉刷新**：三个页面均支持，刷新时不清空列表，不会跳回顶部
- **日期切换**：左右箭头逐日切换，或点日期打开自绘月历选择器
- 设置：最短记录时长阈值（0–60 秒）、隐藏系统应用、主题（跟随系统 / 浅色 / 深色）、壁纸取色（Monet）
- 返回上一页时**保留滚动位置**，不会跳回顶部

## 实现要点

### 数据来源
使用系统 `UsageStatsManager.queryEvents()` 还原会话，核心难点是事件配对：

- `ACTIVITY_RESUMED(1)`：同包内多 Activity 叠加时用**引用计数**，不切断会话；不同包则收尾上一个会话
- `ACTIVITY_PAUSED(2)`：计数减到 0 才认为应用离开前台
- 息屏 / 锁屏 / 关机：强制收尾当前会话，避免把息屏时间算进使用时长
- 查询起点会**往前多看 6 小时**，用于还原跨零点仍在运行的会话，之后再裁剪到当天范围
- 相邻同应用碎片（间隔 ≤ 2 秒）自动合并

### 权限
需要「使用情况访问」权限（`PACKAGE_USAGE_STATS`）。

- 检测同时使用 `AppOpsManager` 和「实际查询近 24 小时数据」兜底，避免部分 ROM 返回 `MODE_DEFAULT` 造成误判
- 通过 `ON_RESUME` 生命周期监听，从系统设置页授权返回后**立即生效，无需重启应用**

### 状态管理
- 详情页 / 设置页以**覆盖层**方式叠加在 Tab 页之上，下层 Tab 不被销毁
- 用 `rememberSaveableStateHolder().SaveableStateProvider` 隔离保存每个 Tab 的界面状态
- 滚动状态使用 `rememberSaveable(saver = LazyListState.Saver)` 显式持久化

## 构建

环境要求：JDK 17+、Android SDK（compileSdk 37）。

```bash
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 技术栈

| 项目 | 版本 |
| --- | --- |
| Kotlin | 2.4.20 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.14.3 |
| compileSdk / targetSdk | 37 / 36 |
| minSdk | 26 |
| Miuix | 0.9.1 |

## 隐私

所有数据均由系统 `UsageStats` 在本地读取和处理，应用**不联网、不上传任何信息**。

## 目录结构

```
app/src/main/kotlin/com/operit/usagetimeline/
├── MainActivity.kt              # 入口、主题、生命周期刷新
├── data/
│   ├── Models.kt                # UsageSession / AppUsage / DayUsage
│   ├── UsageRepository.kt       # 事件流解析、会话还原、权限检测
│   └── SettingsStore.kt         # 偏好存储 + 应用配色
├── ui/
│   ├── MainScreen.kt            # 底部导航 + 覆盖层路由
│   ├── UsageViewModel.kt        # 状态与数据加载
│   ├── Components.kt            # 图标、搜索框、设置行等通用组件
│   ├── Widgets.kt               # 日期条、柱状图、概览卡片
│   └── screens/
│       ├── TimelineScreen.kt    # 时间线
│       ├── AppsScreen.kt        # 应用列表
│       ├── StatsScreen.kt       # 统计
│       ├── AppDetailScreen.kt   # 应用详情
│       ├── SettingsScreen.kt    # 设置
│       └── DatePickerDialog.kt  # 自绘月历
└── util/
    └── Fmt.kt                   # 时间 / 时长格式化
```

## License

Apache-2.0
