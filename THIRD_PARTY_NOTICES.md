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

上述依赖的代码、许可和发布元数据由各自官方仓库提供。本项目不是以上组织的官方产品。
