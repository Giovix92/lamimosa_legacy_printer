# Nothing custom needed for our own code: no reflection-based JSON mapping
# (org.json is used directly, by hand, not via a reflective adapter like
# Gson/Moshi).
#
# Conscrypt ships its own consumer-rules.pro (applied automatically), but it
# still references legacy OEM-internal platform adapter classes
# (com.android.org.conscrypt.SSLParametersImpl / KitKat/pre-KitKat shims)
# that exist on-device but not on the compile classpath - harmless (they're
# conditionally used only on the old Android versions that actually have
# them) but R8 fails the build over the unresolved reference unless told to
# ignore it. Rules below are exactly what R8 generated into
# missing_rules.txt when this first failed.
-dontwarn com.android.org.conscrypt.SSLParametersImpl
-dontwarn org.apache.harmony.xnet.provider.jsse.SSLParametersImpl
