# ClassSchedule

一个使用 Kotlin 和 Jetpack Compose 开发的大学生课表 Android 应用。

当前版本为 `v1.5.0`，已经覆盖课程管理、多课表、按周查看、课表文件导入、桌面小组件和应用内更新等核心流程。

[查看更新记录](CHANGELOG.md) · [下载正式版本](https://github.com/lighthouse333/ClassSchedule/releases) · [反馈问题](https://github.com/lighthouse333/ClassSchedule/issues)

## 当前功能

- 显示周一至周日课表
- 按教学周翻页，并显示对应日期
- 连续节次课程显示为一个跨节课程块
- 新建和切换多张课表，课程与学期设置相互独立
- 新建、重命名和安全删除课表
- 添加、查看、编辑和删除课程
- 为课程单独保存备忘
- 使用 Room 在本地保存课程
- 支持连续节次和不连续周次，例如 `1-9,11-18`
- 检测课程时间冲突和重复课程
- 设置学期开始日期、学期总周数和每日节数
- 单独设置每一节课的开始及结束时间
- 从课表文件导入课程
  - 当前支持北京化工大学、东北师范大学课表 PDF
  - 支持浙江大学选课系统导出的 XLSX 课表
  - 导入前可以预览并选择课程
  - 东北师大多班级总课表支持先选择班级
  - 自动跳过重复课程并提示冲突
- 提供 2×2“当前课程”和 2×4“当前与下一节课”桌面小组件
- 添加、编辑、删除、导入课程或切换课表后自动刷新小组件
- 自动或手动检查 GitHub Release 更新
- 下载 APK 后校验 SHA-256、包名、版本号和应用签名
- 可独立控制自动检查和新版本弹窗提醒；选择稍后更新后 24 小时内不重复提醒同一版本

课程、课表和备忘数据保存在设备本地；导入的课表文件只在设备本地解析，不会上传到服务器。

## 导入北京化工大学课表

1. 打开右上角菜单。
2. 选择“从课表导入”。
3. 选择“北京化工大学”。
4. 选择教务系统导出的课表 PDF。
5. 检查识别结果，勾选课程并确认导入。

当前解析器适用于带文字层的北京化工大学标准课表 PDF。扫描件以及版式不同的文件暂不保证能够识别。

东北师范大学导入适用于罗格斯大学纽瓦克学院的标准总课表 PDF，支持单周、双周、指定周次和多班级选择。

浙江大学导入适用于选课系统导出的 `.xlsx` 课表文件。

## 桌面小组件

安装应用后，在系统桌面长按空白区域进入“小部件”，搜索“课表”或 `ClassSchedule`：

- 2×2 小组件显示当天当前课程。
- 2×4 小组件显示当天当前课程和下一节课程。

小组件会在课程开始、结束、日期或系统时间变化时刷新；课程和课表数据发生修改后也会立即更新。

## 应用更新

“关于与设置”支持手动检查更新，也可以分别启用自动检查和新版本弹窗提醒。下载安装包时会依次校验 SHA-256、应用包名、版本号和签名，通过后才会调用系统安装界面。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Preferences DataStore
- PdfBox-Android
- Android Gradle Plugin

## 本地运行

1. 使用 Android Studio 打开项目。
2. 等待 Gradle 同步完成。
3. 启动 Android 模拟器或连接 Android 设备。
4. 运行 `app` 配置。

也可以使用命令行测试并构建 Debug APK：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK 将生成在 `app/build/outputs/apk/debug/app-debug.apk`。

## 正式签名构建

正式签名信息从项目根目录的 `keystore.properties` 读取，该文件已被 Git 忽略。文件格式如下：

```properties
storeFile=C:/path/to/classschedule-release.jks
storePassword=本地密钥库密码
keyAlias=classschedule
keyPassword=本地密钥密码
```

配置完成后执行：

```powershell
.\gradlew.bat clean testDebugUnitTest assembleRelease
```

正式签名 APK 将生成在 `app/build/outputs/apk/release/app-release.apk`。签名文件和密码不得提交到 GitHub。

## 发布 Release

发布 GitHub Release 时，说明中必须包含 `versionCode`，否则应用内的自动更新功能无法检测到新版本。支持的格式：

```
versionCode: 8
versionCode：8
versionCode = 8
```

或在标题中注明：

```
v1.5.0（versionCode 8）
```

发布前同步更新 `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`，以及 `CHANGELOG.md`。

## 开发计划

后续计划包括北京化工大学教务系统在线导入、通用学校适配器、课程与上课安排模型拆分，以及安全的课表差异同步。详细设计与实施清单见 [Issue #18](https://github.com/lighthouse333/ClassSchedule/issues/18)。

其他开发计划和问题通过 [GitHub Issues](https://github.com/lighthouse333/ClassSchedule/issues) 跟踪。
