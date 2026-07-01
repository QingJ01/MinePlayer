precision highp float;
varying vec2 vUv;
uniform vec2 uResolution;
uniform float uTime;

// Procedural idle "star river" background. The desktop original draws this on a 2D canvas
// (wallpaper.html) rather than a shader, so this is a faithful-in-spirit GLSL recreation of
// its palette and feel: dark base #050608, cyan/mint nebula, champagne aura, twinkling stars.

float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

float noise(vec2 p){
  vec2 i = floor(p), f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p){
  float v = 0.0, a = 0.5;
  for (int i = 0; i < 5; i++) { v += a * noise(p); p *= 2.02; a *= 0.5; }
  return v;
}

float starLayer(vec2 g, float t){
  vec2 gi = floor(g);
  float h = hash(gi);
  float bright = step(0.987, h);
  vec2 c = fract(g) - 0.5;
  float d = smoothstep(0.42, 0.0, length(c));
  float tw = 0.5 + 0.5 * sin(t * (1.0 + hash(gi * 1.7) * 3.0) + h * 40.0);
  return bright * d * tw;
}

void main(){
  vec2 uv = vUv;
  vec2 p = uv * 2.0 - 1.0;
  float aspect = uResolution.x / max(uResolution.y, 1.0);
  p.x *= aspect;
  float t = uTime;

  vec3 base = vec3(0.020, 0.024, 0.031);     // ~#050608 (slightly lifted)
  vec3 primary = vec3(0.839, 0.973, 1.000);  // #d6f8ff
  vec3 secondary = vec3(0.612, 1.000, 0.874); // #9cffdf
  vec3 highlight = vec3(1.000, 0.941, 0.722); // #fff0b8

  // Slow drifting nebula
  vec2 q = p * 0.9 + vec2(t * 0.012, t * 0.008);
  float n = fbm(q * 1.6 + fbm(q * 0.8) * 0.6);
  float n2 = fbm(q * 3.1 - t * 0.02);
  vec3 neb = mix(secondary, primary, n) * pow(n, 1.6) * 0.11;
  neb += secondary * pow(n2, 3.0) * 0.05;

  // Elliptical aura, gently breathing
  vec2 e = p * vec2(1.0 / 1.30, 1.0 / 0.95);
  float aura = exp(-dot(e, e) * (0.85 + 0.05 * sin(t * 0.28)));
  vec3 auraCol = mix(secondary, highlight, 0.40) * aura * 0.16;

  // Twinkling stars (two scales)
  float stars = 0.0;
  stars += starLayer(uv * vec2(aspect, 1.0) * 120.0 + 11.0, t);
  stars += starLayer(uv * vec2(aspect, 1.0) * 220.0 + 47.0, t * 1.3) * 0.7;

  vec3 col = base + neb + auraCol;
  col += mix(primary, highlight, 0.5) * stars * 0.9;

  float vig = smoothstep(1.65, 0.30, length(p * vec2(0.82, 1.04)));
  col *= 0.52 + 0.62 * vig;

  gl_FragColor = vec4(col, 1.0);
}
