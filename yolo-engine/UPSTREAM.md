# 原生推理模块来源

本项目适配与修改日期：2026-10-20。

来源：https://github.com/ultralytics/yolo-flutter-app

固定提交：`46a325a17aa85ad2988be41aed393d3bca17c71d`

许可：AGPL-3.0，原许可全文见 `LICENSE`。本模块保留原文件中的版权和许可声明。

适配内容：

- 只保留 Android 原生 Kotlin 推理、CameraX 视图和七种任务。没有 Flutter 运行时。
- LiteRT 2.1.5；移除可选 QNN 后端及其依赖。
- 官方 `native-lib.cpp` 检测解码/NMS 和 `depth-colorizer.h` 深度上色函数移植为 Kotlin，原算法参考文件保留在 `upstream-reference/`，不需要 NDK/CMake。
- 对非法输入增加长度检查。NMS 仍按官方源码使用类别无关抑制、严格大于置信度/IoU边界；不改变模型输出、坐标反变换或元数据。
- 主相机视图默认隐藏置信度，COCO/姿态/旋转框标签在显示时翻译为中文；分类标签保留模型原文。青色细框和标签、截图与实时视图共用绘制。
- 模型异步加载使用代号丢弃过期请求，销毁时防止重新缓存晚到的模型；每次加载结束关闭其执行器。
- 暂停/分享只导出最近完成推理的原始帧及其对应结果；只保留一个快照，切模型、重绑相机和销毁时清理。未完成首帧时返回空，不拼接当前预览与旧结果。
- 原生运行/关闭及相机预测缓冲区访问串行化；慢推理完成前不会释放其缓冲区，销毁时在后台关闭模型。
- 目标 Android API 35 / 最低 API 23，Java 17，CameraX 1.4.2。
- Kotlin 2.2.20：实际 LiteRT 2.1.5 AAR 使用 Kotlin 2.3 元数据，Kotlin 2.1.21 无法读取；采用与固定上游相同的 2.2.20 编译器。

七种官方 nano LiteRT 模型均来自固定发布：

`https://github.com/ultralytics/yolo-flutter-app/releases/download/v0.6.6/`

模型名：`yolo26n_w8a32.tflite`、`yolo26n-cls_w8a32.tflite`、`yolo26n-seg_w8a32.tflite`、`yolo26n-sem_w8a32.tflite`、`yolo26n-depth_w8a32.tflite`、`yolo26n-pose_w8a32.tflite`、`yolo26n-obb_w8a32.tflite`。

这些 Android 资产采用 raw one-to-many 输出，检测类任务由客户端执行 NMS，因此 IoU 参数有效。它们与 iOS 的 NMS-free Core ML 导出路径不同。性能和相机对齐仍需目标真机验收。
