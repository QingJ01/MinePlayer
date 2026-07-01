precision highp float;
uniform sampler2D uMap;
uniform vec3 uColorA,uColorB,uShadow,uLight;
uniform float uOpacity,uBloomStrength,uColorBoost;
varying float vKind,vLight,vRim,vAmp,vDensity,vFlash;
void main(){
  vec4 tex = texture2D(uMap, gl_PointCoord);
  if(tex.a < 0.070) discard;
  float contrast = clamp(uColorBoost, 0.50, 2.00);
  float lit = clamp(pow(vLight, mix(1.18, 0.74, (contrast - 0.50) / 1.50)), 0.0, 1.28);
  vec3 bone = mix(uColorA, uColorB, clamp((vKind - 0.34) * 2.0 + lit * 0.18, 0.0, 1.0));
  vec3 col = mix(uShadow, bone, clamp(lit, 0.0, 1.0));
  col = mix(col, uLight, clamp(vRim * (0.14 + uBloomStrength * 0.035 + vFlash * 0.40), 0.0, 0.54));
  col = mix(col, uLight, clamp(vAmp * (0.09 + uBloomStrength * 0.025) + vFlash * 0.56, 0.0, 0.68));
  float alpha = tex.a * uOpacity * clamp(0.20 + lit * 0.44 + vDensity * 0.40 + vRim * 0.10 + vFlash * 0.46, 0.12, 1.56);
  gl_FragColor = vec4(col, alpha);
}
