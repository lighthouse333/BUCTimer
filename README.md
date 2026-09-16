# BUCTimer

BUCTimer 是面向北京化工大学学生的 Android 校园学习工具，使用 Kotlin 和 Jetpack Compose 开发。

从 `v2.0.0` 开始，项目由通用课表应用转型为北化学生专用软件。当前版本以北化课表、待办和专注计时为核心，并为教务系统学业信息查询提供统一入口。

[查看更新记录](CHANGELOG.md) · [下载正式版本](https://github.com/lighthouse333/ClassSchedule/releases) · [反馈问题](https://github.com/lighthouse333/ClassSchedule/issues)

## 当前功能

### 北化课表

- 通过北京化工大学教务系统在线导入课表（输入学号密码自动登录拉取）
- 从北化标准课表 PDF 导入课程
- 导入前预览并选择课程，自动跳过重复课程并提示冲突
- 自动应用北京化工大学 12 节作息时间
- 按教学周查看周一至周日课表，并显示对应日期
- 提供只显示当天课程的简洁视图，并记住上次选择
- 支持单双周、连续节次和不连续周次
- 支持多张课表、课程备忘和自定义上课时间
- 可添加、查看、编辑和删除课程

### 待办

- 创建任意层级的待办与子待办
- 展开或收起子项
- 勾选完成、编辑内容和侧滑删除
- 删除父待办时同步删除其全部子项

### 番茄钟

- 自定义专注和休息时长
- 开始、暂停和重置倒计时
- 设置、修改和删除本次专注目标
- 自动切换专注与休息阶段
- 本地保存目标、时长和计时状态

### 桌面小组件与更新

- 2×2 小组件显示当天当前课程
- 2×4 小组件显示当天当前课程和下一节课程
- 课程、课表或当前课表变化后自动刷新小组件
- 自动或手动检查 GitHub Release 更新
- 下载后校验 SHA-256、包名、版本号和应用签名

课程、课表、备忘、待办和番茄钟数据均保存在设备本地；导入文件只在设备本地解析，不会上传。

## 学业功能规划

以下功能是 BUCTimer 面向北化学生的后续核心能力，在 `v2.0.0` 中尚未实现：

- 考试查询
- 成绩查询
- 绩点查询

应用内“学业”页面已预留对应入口，后续将复用北化教务系统登录会话逐步接入。

## 导入北化课表

### 教务系统在线导入

1. 打开课表页面右上角菜单。
2. 选择“北化教务系统导入”。
3. 输入学号、学年和学期。
4. 完成统一认证登录后导入课程。

### PDF 文件导入

1. 打开课表页面右上角菜单。
2. 选择“导入北化课表 PDF”。
3. 选择教务系统导出的标准课表 PDF。
4. 检查识别结果并确认导入。

PDF 解析适用于带文字层的北京化工大学标准课表，扫描件或不同版式文件不保证能够识别。

## 桌面小组件

安装应用后，在系统桌面长按空白区域进入“小部件”，搜索“北化课表”或 `BUCTimer`：

- 2×2 小组件显示当天当前课程。
- 2×4 小组件显示当天当前课程和下一节课程。

## 技术栈

- Kotlin
- Jetpack Compose 与 Material 3
- Room
- Preferences DataStore
- PdfBox-Android
- Android Gradle Plugin

## 本地运行

使用 Android Studio 打开项目并运行 `app` 配置，或通过命令行构建独立包名的 Debug 版本：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，应用名称为 `BUCTimer Debug`，可与正式版共存。

## 正式签名构建

正式签名信息从项目根目录的 `keystore.properties` 读取，该文件已被 Git 忽略：

```properties
storeFile=C:/path/to/buctimer-release.jks
storePassword=本地密钥库密码
keyAlias=buctimer
keyPassword=本地密钥密码
```

执行：

```powershell
.\gradlew.bat clean testDebugUnitTest assembleRelease
```

正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`。签名文件和密码不得提交到 GitHub。

## 发布与自动更新

为保持旧版本的覆盖更新和本地数据，BUCTimer 继续使用原包名 `com.example.timetable` 和原签名密钥。

发布 GitHub Release 时，说明中必须包含递增后的 `versionCode`。`v2.0.0` 使用：

```text
versionCode: 11
```

发布前需要同步更新 `app/build.gradle.kts` 中的 `versionCode`、`versionName` 和 `CHANGELOG.md`。
