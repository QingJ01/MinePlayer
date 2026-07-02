# MinePlayer R8 rules.
#
# Media3/ExoPlayer, Jetpack Compose, and DataStore ship their own consumer rules inside
# their AARs, so R8 applies them automatically — we only add what's specific to this app.

# Keep readable crash stack traces even though release is minified/obfuscated.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# The playback service is instantiated by the framework (declared in the manifest) and
# reached via a MediaController SessionToken(ComponentName(..., PlaybackService::class.java)),
# so R8 must not rename or strip it.
-keep class com.mine.player.audio.PlaybackService { *; }
