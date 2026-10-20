# 来源与第三方许可

本项目适配与修改日期：2026-10-20。

## Ultralytics 代码及模型

- 官方仓库：https://github.com/ultralytics/yolo-flutter-app
- 固定代码提交：`46a325a17aa85ad2988be41aed393d3bca17c71d`
- 固定模型发布：https://github.com/ultralytics/yolo-flutter-app/releases/tag/v0.6.6
- 许可：AGPL-3.0，全文见根目录 `LICENSE`，引擎副本见 `yolo-engine/LICENSE`。
- Android 原生模块适配详情：`yolo-engine/UPSTREAM.md`。
- 官方测试图片：https://raw.githubusercontent.com/ultralytics/assets/main/im/bus.jpg
- 测试图 SHA-256：`c02019c4979c191eb739ddd944445ef408dad5679acab6fd520ef9d434bfbc63`。

修改包括中文 Android 界面、模型管理、中文标签及视觉样式、Kotlin 后处理替代 JNI、并发和生命周期修复。保留原文件版权及许可标记；本仓库包含对应完整源码与构建脚本。

## 构建依赖

由 Gradle 从 Google Maven、Maven Central、Gradle Plugin Portal 拉取，版本在工程配置中固定：

- AndroidX / CameraX、Material Components、Google LiteRT、Guava：Apache-2.0。
- Kotlin / Kotlin Coroutines、SnakeYAML：Apache-2.0。
- JUnit：EPL-1.0，仅用于测试。
- Android Gradle Plugin / Gradle Wrapper：Apache-2.0，工具自身许可以官方发行内容为准。

上述是主要组件的许可摘要，不表示整个 APK 中的所有依赖都采用同一许可。LiteRT 的运行时传递依赖还包括：

- `com.google.android.play:ai-delivery:0.1.1-alpha01`
- `com.google.android.play:asset-delivery:2.3.0`
- `com.google.android.play:core-common:2.0.4`

这些组件的发布 POM 指向 [Play Core 条款](https://developer.android.com/guide/playcore#license)。

- `com.google.android.gms:play-services-basement:18.4.0`
- `com.google.android.gms:play-services-tasks:18.2.0`

这两个组件的发布 POM 指向 [Android SDK License](https://developer.android.com/studio/terms)。项目源码的 AGPL-3.0 声明不改变这些第三方组件各自的条款。

实际运行时组件清单、许可地址以及依赖包内的 LICENSE / NOTICE 原文见 [THIRD_PARTY_LICENSES.txt](THIRD_PARTY_LICENSES.txt)。GitHub Release 同时提供该附件，与对应 APK 一起发布。

上述依赖的代码、许可和发布元数据由各自官方仓库提供。本项目不是以上组织的官方产品。
