# Unity-Android-Clicker

一个中文 Android 连点器项目，可执行重复点击、悬浮靶标连点、脚本录制和脚本回放。工程不依赖 Gradle，直接使用本机 Unity 附带的 Android SDK/OpenJDK 工具链构建 APK。

## 功能

- 悬浮靶标选择连点位置
- 悬浮窗启动/暂停
- 应用内画布录制点击和滑动脚本
- 透明悬浮录制层录制脚本
- 脚本回放，最多保存 3 个脚本
- 横屏/竖屏脚本区分

## 导出 APK

确认本机已安装 Unity Android Build Support。当前构建脚本默认使用：

```powershell
D:\2022.3.62f2c1\Editor\Data\PlaybackEngines\AndroidPlayer
```

如果你的 Unity 安装路径不同，修改 [build.ps1](build.ps1) 里的 `$UnityAndroid`。

构建：

```powershell
.\build.ps1
```

输出：

```text
Builds\Clicker.apk
```

## 修改内容

常用入口：

- 应用名称、无障碍服务名称：`app/src/main/res/values/strings.xml`
- 主界面按钮和状态文本：`app/src/main/java/com/localclicker/autoclicker/MainActivity.java`
- 悬浮窗按钮、靶标、连点和脚本回放逻辑：`app/src/main/java/com/localclicker/autoclicker/ClickAccessibilityService.java`
- 脚本录制画布：`app/src/main/java/com/localclicker/autoclicker/RecordScriptActivity.java`
- 脚本保存格式和数量上限：`app/src/main/java/com/localclicker/autoclicker/ScriptStore.java`
- 包名、Activity、无障碍服务声明：`AndroidManifest.xml`
- 无障碍能力配置：`app/src/main/res/xml/accessibility_service_config.xml`

修改后重新运行：

```powershell
.\build.ps1
```

## 安装和权限

安装 `Builds\Clicker.apk` 后，需要手动开启：

- 无障碍服务：用于执行点击和滑动手势
- 悬浮窗权限：用于显示控制按钮和靶标

如果更新 APK 后无障碍行为异常，先在系统设置里关闭“连点器服务”，再重新开启。

## 注意

安卓普通悬浮窗无法同时“透传触摸到底下应用”并“录制触摸坐标”。当前悬浮录制采用安全透明覆盖层：能录制点击/滑动，但触摸不会传递到底下应用。
