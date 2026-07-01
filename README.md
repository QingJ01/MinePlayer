<div align="center">

# MinePlayer

**沉浸式本地音乐播放器 · Native Android**

Kotlin · Jetpack Compose · Media3 · OpenGL ES

</div>

---

MinePlayer 是一个原生 Android 本地音乐播放器,主打**沉浸式粒子视觉**与干净的播放体验。所有音乐来自本机,无需联网。

## 功能

- 🎵 **本地播放** — 基于 Media3 (ExoPlayer + MediaSession),后台播放、锁屏/通知栏控件
- 🌌 **粒子视觉台** — OpenGL ES 把专辑封面化作粒子,音频驱动律动;内置**丝绸 / 黑胶 / 星球 / 隧道**多种预设
- 📜 **歌词** — 内嵌歌词(ID3 USLT / FLAC Vorbis)+ 外挂 LRC,双语显示、点句跳转、当前句居中
- 🎚️ **音频** — 系统均衡器、压限器、回放增益(音量平衡)、播放速度、输出采样率、无间隙播放
- 🗂️ **曲库** — MediaStore 扫描 + 自定义目录 / 屏蔽目录、排序、专辑/艺术家/歌单浏览、封面墙(歌单架)
- 🎨 **主题** — 深/浅色(可跟随系统)、强调色(预置 / 自定义取色 / 跟随系统动态色)
- 🌫️ **细节** — 沉浸模式、睡眠定时、播放进度记忆、启动即恢复、高斯模糊等

## 技术栈

| 领域 | 技术 |
|---|---|
| UI | Jetpack Compose (Material 3) |
| 播放 | AndroidX Media3 (ExoPlayer / Session) |
| 视觉 | OpenGL ES 2.0 (EGL + 自管理渲染线程) + GLSL |
| 音频分析 | BaseAudioProcessor PCM tap → FFT → 频段 |
| 存储 | DataStore Preferences + SharedPreferences |
| 语言 | Kotlin 2.0 · minSdk 28 · targetSdk 34 |

## 构建

```bash
./gradlew :app:assembleDebug
```

产物在 `app/build/outputs/apk/debug/`。需要 JDK 17。

## 致谢

粒子视觉风格移植自 [XxHuberrr/Mineradio](https://github.com/XxHuberrr/Mineradio)(原 Web/Electron 版)。
