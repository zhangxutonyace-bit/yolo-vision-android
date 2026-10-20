# 构建安卓应用

## 环境

- Android Studio（支持 Android Gradle Plugin 8.10.1 的版本）。
- JDK 17。
- 在 Android Studio 的 SDK Manager 安装 Android SDK Platform 35、Android SDK Build-Tools 35.0.0 和 Android SDK Platform-Tools。
- Gradle 8.13 由项目自带的 Gradle Wrapper 下载，无需另行安装。
- 首次构建需要联网下载 Google Maven、Maven Central 和 Gradle 的依赖。此项目无需另外安装 NDK 或 CMake。

应用最低支持 Android 8.0（API 26）。

## Android Studio

1. 打开包含 `settings.gradle.kts` 的项目目录。
2. 在设置中的 **Build, Execution, Deployment → Build Tools → Gradle** 选择 JDK 17，等待项目同步。
3. 选择 `app`，连接已开启 USB 调试的手机或使用已配置的模拟器。
4. 点击 Run 安装运行；首次进入时允许相机权限。

## Windows 命令行

在项目目录打开 PowerShell，配置自己的实际路径：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug
```

也可使用附带的构建脚本：

```powershell
.\build.ps1
.\build.ps1 -Validate
```

脚本优先读取显式参数、`JAVA_HOME`、`ANDROID_HOME` / `ANDROID_SDK_ROOT`、`local.properties` 及常用安装目录。必要时可以明确指定：

```powershell
.\build.ps1 -JavaHome 'C:\path\to\jdk-17' -AndroidSdk 'C:\path\to\Android\Sdk'
```

`-GradleHome` 指定 Gradle 缓存目录，也可通过 `GRADLE_USER_HOME` 设置。未设置时，脚本使用本地准备环境的缓存（如存在），否则使用项目内 `.build-env/gradle-home`；日志位于 `.build-env/logs`。只有依赖完整缓存后才使用 `-Offline`。

APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`。这是供安装测试的调试包；公开发布前需要配置自己的正式签名。

## 检查与设备验证

```powershell
.\gradlew.bat :app:testDebugUnitTest :yolo-engine:testDebugUnitTest :app:lintDebug
```

构建、单元测试和 lint 通过不代表相机与模型已经完成真机验收。安装后仍需检查权限拒绝与重新授权、前后摄像头、屏幕旋转、暂停/继续、任务切换、设置保存、分享以及实际画面的识别表现。相机效果与推理速度以真实手机为准。
