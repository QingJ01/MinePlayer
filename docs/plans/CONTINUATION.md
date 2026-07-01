# Mineradio Android — 续作交接文档

> 给下一个接手的会话/人。本项目把 Windows Electron 版 Mineradio 重写成 Android 原生
> (Kotlin + Compose + OpenGL ES),**保留全部美术风格**。核心决策见
> [设计文档](2026-06-30-mineradio-android-design.md),移植细节见 [porting-notes](porting-notes.md)。

## 当前状态(全部已编译验证,真机运行待用户插 USB)

| 里程碑 | 状态 | 内容 |
|---|---|---|
| 工具链 | ✅ | JDK 17 + Gradle 8.9 + SDK android-34 装在 **D 盘**(见下) |
| M0 | ✅ 编译过 | Compose 工程、暗色主题(青+香槟金,1:1 移植 CSS 变量)、XML 自适应图标 |
| M1 | ✅ 编译过 | MediaStore 本地曲库扫描、Media3/ExoPlayer 播放、玻璃播放条、权限闸门 |
| M2 | ✅ 编译过 | OpenGL ES 封面粒子(14161 粒子)+ bloom + 音频 PCM-tap FFT 律动、EGL TextureView、玻璃正在播放舞台 |
| M3 | ✅ 编译过 | LRC 解析、sidecar `.lrc` 查找、同步歌词视图(自动滚动/高亮) |
| M4a | ✅ 编译过 | 6 预设选择器(SILK/隧道/星球/虚空/黑胶/壁纸)+ 骷髅点云(52416 点) |
| M4b | ✅ 编译过 | WebGL 启动动画(splash 着色器字节级移植)+ 程序化星河待机背景(galaxy.frag,按原版调色板神似复刻) |
| M5 | ✅ 编译过 | **3D 封面墙歌单架**(Compose HorizontalPager + rotationY 透视,触屏原生;非桌面 1:1) |
| M6 | ✅ 编译过 | 后台播放(MediaSessionService + MediaController + 媒体通知);AudioAnalyzer 单例在服务/可视化间共享 |
| **剩余(可选)** | ⬜ | DataStore 持久化视觉预设(依赖已加,未接线);3D 架可后续升级为 GL 1:1 + 连线粒子(13498-13520) |

**未做但原版也没显示的**:浮空粒子层(原版 `createFloatLayer()` 开头即 `return`,已关闭,为保真跳过)。

**全部里程碑均编译验证通过(含一次 clean 全量构建)。真机渲染/律动表现仍待用户插 USB 确认。**

## 工具链(D 盘)与构建

```
JDK:    D:\Android\jdk-17.0.19+10        (路径也存在 D:\Android\JDK_HOME.txt)
Gradle: D:\Android\gradle-8.9\bin\gradle.bat
SDK:    D:\Android\Sdk                   (local.properties 已指向)
```

构建命令(PowerShell):
```powershell
$root="D:\Android"; $jdk=Get-Content "$root\JDK_HOME.txt"
$env:JAVA_HOME=$jdk; $env:ANDROID_HOME="$root\Sdk"; $env:ANDROID_SDK_ROOT="$root\Sdk"; $env:PATH="$jdk\bin;$env:PATH"
& "$root\gradle-8.9\bin\gradle.bat" -p "d:\project\Mineradio-andriod" :app:assembleDebug --console=plain
```
产物:`app\build\outputs\apk\debug\app-debug.apk`(当前约 63MB,debug;material-icons-extended 占大头,release 走 R8 会瘦)。

真机安装(用户插 USB 后):`& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r <apk>`。

## 已验证的着色器移植配方(照此办)

1. **抽取**:原版着色器在 `scratchpad/Mineradio/public/index.html`。模板字符串(`var xx = \`...\``)用
   `scratchpad/extract-shaders.js` 那种脚本抽;数组拼接(`[...].join('\n')`)手工搬(如 skull)。
2. **加 GLES 内建**:Three.js 自动注入的要手动声明 —— 顶点着色器开头(`precision` 之后)加:
   `attribute vec3 position;` `uniform mat4 modelViewMatrix, projectionMatrix;`,
   用到法线的再加 `uniform mat3 normalMatrix;`(在 Kotlin 用 `transpose(inverse(mat3(modelView)))` 算)。
   片元着色器(`texture2D`/`gl_FragColor`)直接可用。**v1 用 GLES2 写法**(`attribute/varying`)。
3. **Kotlin 系统**:仿 `visual/gl/CoverParticleSystem.kt` 或 `SkullPointCloud.kt` —— 读资产、建 VBO、
   `GlProgram` 编译、逐帧 set uniform、`glDrawArrays(GL_POINTS, ...)`。
4. **接入** `visual/gl/VisualRenderer.kt` 的 `onSurfaceCreated`/`onDrawFrame`。
5. **编译验证**(上面命令)。unstable API 让编译器把关 —— 必要时用
   `D:\Android\jdk-17.0.19+10\bin\javap.exe -cp <jar> <类>` 查签名(Media3 jar 在
   `C:\Users\QingJ\.gradle\caches\8.9\transforms\*media3*\...\classes.jar`)。

## 剩余工作 + 精确源码位置

### M4-剩余 a:星河待机背景
- 源:`index.html` 全屏背景着色器 **8766 起**(`makeBackgroundMaterial` 附近,雾/星河 fullscreen quad)、
  火花 **8887 起**;另有 `public/wallpaper.html`(142 行,独立壁纸场景)。
- 做法:一个全屏 quad(两三角形,无几何 VBO 也行)跑片元着色器,作为最底层(在封面粒子之前画,关 depth)。
  加 `@Volatile var idleBackground` 到 renderer,空闲/首页时开。

### M4-剩余 b:WebGL 启动动画
- 源:`index.html` **25523-25720+**(`splashGl`/`splashGlProgram`/`splashGlUniforms`/draw)。是 fullscreen
  shader(ShipSwiftAnimatedLoop 风格:高亮线场 + RGB channel offset + warp)。
- 做法:做一个 `SplashScreen`(Compose)内嵌一个全屏 `GLTextureView` 跑该 splash 片元着色器;跑完/点击
  `dismissSplash` 进主界面。原版逻辑:动画完 → `ready` 状态显示"点击进入" → 点任意处/Enter 进入。

### M5:3D 歌单架
- 源:连线粒子 **13498-13520**(`connectorParticles`,position 偏 (0,-2.2,0))、上方 `shelf` 几何(grep
  `shelf` 共 1379 处,体量大)。原版靠鼠标悬停/右键 —— **触屏要重设计**(长按/手势)。
- 这是剩余最复杂项;建议放最后,先做能用的列表式歌单架,再上 3D。

### M6:后台播放 + 偏好
- 后台播放:把 ExoPlayer 迁到 `androidx.media3.session.MediaSessionService`(已加依赖 media3-session、
  Manifest 已留 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`/`POST_NOTIFICATIONS` 权限和 service 注释位)。
  通知用 MediaSession 自带。`PlayerViewModel` 改为连 `MediaController` 而非直接持 ExoPlayer。
- 偏好:`androidx.datastore`(已加依赖)存视觉预设/封面清晰度等;移植
  `scratchpad/Mineradio/public/default-user-fx-archive.json` 作默认值。

## 关键技术约定 / 坑

- **合成**:GL 用 `TextureView`(不是 GLSurfaceView)叠在 Compose 下层,`isOpaque=false`,渲染线程自管
  EGL14。正在播放页背景透明让 GL 透出;库页画暗色背景盖住。见 `visual/GLTextureView.kt`。
- **相机**:`Matrix.perspectiveM(45°, aspect, 0.1, 100)`;`VisualRenderer.cameraDistance()` 按宽高比框 4.8
  平面;有轻微轨道漂移 + beat 推镜。骷髅有自己的 model 矩阵(translate (0,0.22,0.10) scale 2.34)。
- **音频律动**:`AudioAnalyzer` 继承 `BaseAudioProcessor` 做透传 tap(`TeeAudioProcessor` 在 Media3 1.4.1
  **不存在**,别用)。频段映射见 porting-notes 第 5 节。灵敏度 `sensitivity` 需真机调。
- **玻璃**:v1 是"伪玻璃"(半透明渐变 + 边),真·背景模糊(跨 Surface)是后续增强。
- **minSdk 28**;XML 自适应图标(无 PNG)。
- **诚实交底**:以上全部只做过**编译验证**,GL 渲染/音频律动真实表现必须真机确认(模拟器 GPU 不可信)。

## 文件地图(已建)

```
audio/   Track, LocalLibraryRepository, AlbumArtLoader, PlayerViewModel(含 AudioAnalyzer 接线)
visual/  AudioAnalyzer, Fft, GLTextureView, VisualStage
  gl/    GlUtil, GlProgram, DotTexture, CoverParticleSystem, SkullPointCloud, VisualRenderer, FrameUniforms
lyrics/  LrcParser, LyricRepository
ui/      MineradioRoot(权限+导航)
  theme/     MR(调色板), Theme, Type
  components/ Glass, AlbumThumb, TransportBar, LyricView, Format
  screens/   LibraryScreen, NowPlayingScreen(含预设选择器)
assets/shaders/  cover.vert/frag, cover_bloom.vert/frag, skull.vert/frag
assets/          skull-decimation-points.bin (52416 点 × 5 float)
```
