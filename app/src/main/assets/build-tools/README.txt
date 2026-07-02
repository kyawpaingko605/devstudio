===================================================
  Android Build Tools - ARM64 (Termux / Android)
  Build-Tools r33 + API 27 (Android 8.1 Oreo)
===================================================

ဤ tools များသည် Android ARM64 (aarch64) အတွက်ဖြစ်သည်
Termux တွင် တိုက်ရိုက်အသုံးပြုနိုင်သည်

bin/ (ARM64 native binaries - Termux aarch64)
  aapt        aapt v1  (Android Asset Packaging Tool)
  aapt2       aapt v2  (v13.0.0.6)
  zipalign    ZIP Alignment Tool

lib/ (JAR files - platform independent)
  android.jar    Android SDK API 27 (Android 8.1 Oreo)
  d8.jar         DEX Compiler D8 v33
  apksigner.jar  APK Signer v33
  ecj.jar        Eclipse Java Compiler (DEX version)

keystore/
  debug.keystore    alias=androiddebugkey  pass=android
  release.keystore  alias=releasekey       pass=release123

===================================================
TERMUX တွင် အသုံးပြုနည်း
===================================================
1. zip extract လုပ်ပါ:
   unzip android-tools-arm64.zip -d ~/android-tools

2. bin/ ထဲ permission ပေးပါ:
   chmod +x ~/android-tools/bin/*

3. PATH ထည့်ပါ (~/.bashrc):
   export PATH=$HOME/android-tools/bin:$PATH
   export ANDROID_JAR=$HOME/android-tools/lib/android.jar

===================================================
BUILD WORKFLOW
===================================================
1. Java compile (openjdk လိုသည်: pkg install openjdk-17):
   javac -classpath lib/android.jar -d obj/ src/**/*.java

2. DEX:
   java -cp lib/d8.jar com.android.tools.r8.D8 \
     --output classes.dex obj/**/*.class

3. Package (aapt2):
   aapt2 compile res/**/* -o compiled/
   aapt2 link -o app-unsigned.apk \
     --manifest AndroidManifest.xml \
     -I lib/android.jar compiled/*.flat

4. Zipalign:
   zipalign -v 4 app-unsigned.apk app-aligned.apk

5. Sign:
   java -jar lib/apksigner.jar sign \
     --ks keystore/release.keystore \
     --ks-pass pass:release123 \
     --ks-key-alias releasekey \
     --key-pass pass:release123 \
     --out app-signed.apk app-aligned.apk

===================================================
NOTE: javac/keytool လိုလျှင် Termux တွင်:
  pkg install openjdk-17
===================================================
