# Mineradio 视觉/音频移植笔记(从原版 index.html 抠出)

> 给 M2/M3/M4 直接照搬用。行号指向 scratchpad 里克隆的原版 `public/index.html`。

## 1. 封面粒子几何(M2 核心)— index.html:5704-5739

- 网格 `GRID × GRID`,默认 `GRID=119`(由 `coverParticleGridForResolution(fx.coverResolution)`,范围 119→183)。
- 粒子数 `PCOUNT = GRID*GRID`(默认 14,161)。
- `PLANE_SIZE = 4.8`。每粒子:
  - position: `((gx/(GRID-1))-0.5)*4.8, ((gy/(GRID-1))-0.5)*4.8, 0`
  - aUv: 纹素中心 `((gx+0.5)/GRID, (gy+0.5)/GRID)`(避免采到纹理缝)
  - aRand: `Math.random()`
- Kotlin 端:`FloatBuffer` 3 个 VBO(position vec3, aUv vec2, aRand float)。封面分辨率滑块改 GRID 时重建几何。

## 2. Uniforms 全量 — index.html:5808-5852

**核心(M2 必接):**
`uTime, uBass, uMid, uTreble, uBeat, uEnergy, uBurstAmt, uPreset, uPointScale,
uColorBoost(1.1), uBloomStrength(0.62), uBloomSize(2.65), uCoverTex, uPrevCoverTex,
uColorMixT(切歌 0→1 渐变), uEdgeTex, uDotTex, uHasCover, uHasDepth, uEdgeEnabled,
uPixel(devicePixelRatio), uAlpha(启动 fade-in), uParticleDim, uLoading(0→1 聚成圆环)`

**可先置 0/桩(交互后续补):**
`uVinylSpin, uIntensity, uDepth, uSpeed, uTwist, uScatter, uCoverRes, uBgFade,
uTintColor/uTintStrength, uRippleTex/uRippleCount(鼠标涟漪), uAiBoost,
uMouseXY/uMouseActive/uHandXY/uHandActive/uGestureGrip(鼠标/手势/AI), uFloatAlpha`

## 3. 封面纹理体系 — index.html:5781-5806

- `uCoverTex`: 当前封面位图(LinearFilter, ClampToEdge)。
- `uEdgeTex`: R=depth, G=edge, B=fg-mask, A=lum(预处理的边缘/深度图)。v1 可先只给封面、`uHasDepth=0`,边缘图后补。
- `uPrevCoverTex` + `uColorMixT`: 切歌时旧→新封面交叉渐变。
- `uDotTex`: 柔光圆点精灵(程序化径向渐变生成的 RGBA 贴图,见 fs 里 `texture2D(uDotTex, gl_PointCoord)`)。
- `uRippleTex`: 1×12 RGBA Float DataTexture,存触摸涟漪 (x,y,age,str)。

## 4. 着色器抽取清单(原样搬到 OpenGL ES,改极少)

| 着色器 | 行号 | 用途 |
|---|---|---|
| 封面 vertex `vs` | 5858-6328 | 主粒子顶点(采封面、律动、预设分支、loading 聚环) |
| 封面 fragment `fs` | 6332-6357 | 柔点 + 可读描边 + 黑色保留 |
| bloom vs/fs | 6364-6390 | 由 vs/fs 派生,additive 辉光 |
| float vs/fs | 6499-6510 | 1300 浮空粒子 |
| skull vs/fs | 6831-6913 | 骷髅点云 |
| 全屏背景 | 8766-8923 | 雾/星河 + 火花 |
| 连线粒子 | 13499-13517 | 3D 歌单架连线 |

GLSL ES 注意:原版是 ES1.00 风格(`attribute/varying/texture2D/gl_FragColor`),
OpenGL ES 2.0 直接可用;若用 ES3 `#version 300 es` 需把 `attribute→in, varying→out/in,
texture2D→texture, gl_FragColor→out vec4`。**v1 先用 ES2 写法省改动。**

## 5. 音频 → uniform(M2/M3)— index.html:4378-4402, 17730-17737

- 实时:`AnalyserNode` FFT(`analyser.fftSize=FFT_SIZE`)+ 独立 `beatAnalyser`。
- `beatBandRms(data, sampleRate, fftSize, hz0, hz1)`:把字节频谱在 [hz0,hz1] 频段求 RMS。
- 频段划分(原版):
  - sub `38-74Hz`,kick `52-165Hz`,body `165-420Hz`,vocal `420-2600Hz`,snap `1800-9200Hz`
- 映射:`uBass`≈sub+kick,`uMid`≈body+vocal,`uTreble`≈snap,`uBeat`=kick 能量起跳(onset),都带平滑。
- 离线:`OfflineAudioContext` + MusicTempo 求精确鼓点网格(可选,后续)。

**Android 复刻:** ExoPlayer 自定义 `AudioProcessor` 截 PCM → 环形缓冲 → 手写 radix-2 FFT(1024/2048)→
按上面 Hz 频段算 RMS → 平滑后写 uniform。beat 用 kick 频段能量超过滑动均值阈值判定。

## 6. 骷髅点云资产 — index.html:6605-6635, public/assets/skull-decimation-points.bin

- `skull-decimation-points.bin` = `Float32Array`(decimation 后的点位)。1,048,320 字节 / 4 = 262,080 float = 87,360 个 vec3 点。
- 属性:position(vec3) + seed(float) + kind(float)(seed/kind 在加载时程序生成)。
- Kotlin:把 bin 放 `assets/`,`ByteBuffer.order(LITTLE_ENDIAN).asFloatBuffer()` 读入 VBO。

## 7. 合成与坐标

- 相机:`PerspectiveCamera(45, aspect, 0.1, 100)`(index.html:3721)。Kotlin 用 `Matrix.perspectiveM` fovy=45。
- 渲染顺序:bloom(additive, renderOrder 0)先,主粒子(normal blend, renderOrder 1)后。
- `uPixel = devicePixelRatio` → Android 用 `resources.displayMetrics.density` 或实际像素比。
