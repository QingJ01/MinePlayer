# Mineradio Android 原生重写 — 设计文档

日期: 2026-06-30
状态: 设计待确认

## 0. 目标与已锁定决策

把 Windows Electron 版 Mineradio 重写成 **Android 原生音乐播放器**,**保留全部美术风格**。

| 决策点 | 选择 |
|---|---|
| 技术栈 | 原生 **Kotlin + Jetpack Compose**(切换掉 Electron/HTML) |
| 音乐来源 | **本地音频文件**(扫描设备),不移植 server.js 在线代理 |
| v1 视觉范围 | **全部招牌视觉**:封面粒子+辉光、歌词舞台、骷髅点云、星河待机、启动动画、3D 歌单架 |
| 构建/验证 | 工具链装 **D 盘**,我用 gradlew 编译验证;真机 USB 调试由用户后续提供 |

### 核心洞察
原版"美术风格"= **GLSL 着色器 + WebGL 实时渲染**,不是一套可搬运的图片资源。
- 整个前端是 26,879 行 `public/index.html`,一个 Three.js 大场景挂了 11 套 ShaderMaterial。
- 着色器是标准 GLSL ES(`texture2D`/`gl_FragColor`/`gl_PointSize`),**可直接移植到 OpenGL ES 2.0/3.0**。
- 要用 Kotlin 重写的是:几何/粒子布点逻辑、相机、场景管线、动画(GSAP→Compose)。
- 配色固定:纯黑底 `#000` + 青 `#00F5D4` + 香槟金 `#f4d28a`;玻璃质感靠 CSS backdrop-filter / SVG 滤镜。

## 1. 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 + 自定义暗色主题 |
| 音频播放 | AndroidX **Media3 (ExoPlayer)** + `MediaSessionService`(后台播放 + 通知) |
| 本地曲库 | **MediaStore** 扫描;权限 `READ_MEDIA_AUDIO`(API33+)/`READ_EXTERNAL_STORAGE`(更低) |
| 音频分析 | ExoPlayer 音频处理链 tap 取 PCM → 自算 FFT → bass/mid/treble/beat(比 `Visualizer` 更可靠) |
| 视觉渲染 | **OpenGL ES 3.0**,`GLSurfaceView`/SurfaceView 置底,Compose UI 叠加在上 |
| 玻璃 UI | 半透明深色渐变 + 内高光边(v1);真·背景模糊(RenderEffect/Haze, API31+)作为增强 |
| 偏好存储 | DataStore;移植 `default-user-fx-archive.json` 默认视觉参数 |
| minSdk / target | minSdk 28, compile/target 34(玻璃模糊在 31+ 启用,28-30 回退) |

## 2. 模块结构

```
com.mineradio.android
├─ app/            Application, MainActivity(Compose host)
├─ audio/          LocalLibraryRepository(MediaStore 扫描)
│                  PlaybackService(Media3 MediaSessionService)
│                  PlayerController, AudioAnalyzer(PCM→FFT→频段/节拍)
├─ visual/
│   ├─ gl/         EglCore, SceneRenderer(主循环, 共享相机/uniform)
│   ├─ systems/    CoverParticleSystem, BloomSystem, FloatParticleSystem,
│   │              SkullPointCloud, IdleGalaxy, SplashScene, ShelfConnectors
│   ├─ shaders/    从 index.html 抠出的 .glsl(assets)
│   ├─ CoverSampler 封面位图→NxN 粒子属性
│   └─ DotTexture   程序化柔光点精灵
├─ ui/
│   ├─ theme/      Color/Type/Glass(青+香槟金暗色)
│   ├─ screens/    Home, Player, Library, LyricStage
│   ├─ components/ GlassPanel, GlassButton, TransportControls,
│   │              VisualHost(AndroidView 包 GLSurfaceView), LyricView
│   └─ nav/
├─ lyrics/         LrcParser, 内嵌歌词提取, LyricSync
└─ data/           DataStore 偏好, 默认 FX 存档移植
```

## 3. 视觉移植方案(核心)

Three.js 单场景 → 一个 GLES `SceneRenderer`,各子系统共享相机与逐帧 uniform。

逐帧共享 uniform:`uTime, uBass, uMid, uTreble, uBeat, uBurstAmt, uPreset, uPixel`,投影/模型视图矩阵(`android.opengl.Matrix`)。

| 子系统 | 原版位置 | 移植要点 |
|---|---|---|
| 封面粒子 + 辉光 | index.html:6359 / 6387 | 封面位图采样成 119×119 网格→位置/颜色/属性 VBO;vs/fs 直接移植;additive bloom 先渲染 |
| 浮空氛围粒子 | :6499 | 1300 颗,长周期 sin/cos 漂移 |
| 骷髅点云 | :6811 | 读 `skull-decimation-points.bin`(Float32, 1MB)→VBO |
| 背封面 / 其他点系 | :7130 / :7345 | 同管线复用 |
| 全屏背景着色器 | :8766 | fullscreen quad,星河/雾 |
| 火花 | :8887 | additive 火花 |
| 3D 歌单架连线粒子 | :13498 | 连线粒子 + 歌单架 3D 布局(最复杂,触屏交互重做) |
| 视觉预设 uPreset | 着色器内分支 | >3.5 环形、>4.5 流场等"视觉模式"(Emily/节奏)→预设切换 |

**音频→视觉**:ExoPlayer 音频链 tap 取 PCM,自算 FFT 得 bass/mid/treble,能量阈值做 beat;逐帧写入 uniform。

**玻璃合成**:GLSurfaceView 置底渲染视觉,Compose 玻璃面板叠加。v1 用"伪玻璃"(半透明深色渐变+内高光+让底层 bloom 透出);真·背景模糊作后续增强(跨 Surface 模糊非平凡)。

## 4. 构建顺序(v1 内部里程碑,始终保持可跑)

- **M0** 工具链装 D 盘 + 空 Compose 工程能编译运行(黑屏+标题)
- **M1** 本地曲库扫描 + Media3 播放 + 最小玻璃播放控制(能放歌)
- **M2** GLES 管线 + 封面粒子 + 辉光,随音频 FFT 律动(招牌视觉)
- **M3** 歌词舞台(lrc 解析 + 同步样式歌词)+ 浮空粒子
- **M4** 骷髅点云 + 视觉预设 + 星河待机 + 启动动画
- **M5** 3D 歌单架 + 连线粒子 + 玻璃/主题/字体对齐打磨
- **M6** 后台播放(MediaSession 通知)+ DataStore 视觉参数

## 5. 已知技术风险(交底)

- 跨 GL Surface 的真·背景模糊不平凡 → v1 伪玻璃,真模糊作增强。
- `Visualizer` FFT 在部分 API 受限/需 RECORD_AUDIO → 改用 ExoPlayer PCM tap 自算 FFT。
- 3D 歌单架原版靠鼠标悬停/右键 → 触屏需重设计(长按/手势)。
- 字体 Cinzel Decorative / UnifrakturCook 等需打包成 assets(ttf)或用 Downloadable Fonts。
- OpenGL ES 视觉的真实帧率/质感必须真机验证(模拟器 GPU 不可信)。

## 6. 法务/版权

仅做本地播放,不内置在线音源,规避在线音乐 API 的合规问题。原版采用其许可证(见仓库 LICENSE),沿用其美术方向属同一作者项目。
