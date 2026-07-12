# DevStudio - Mobile IDE

[🇲🇲 မြန်မာဘာသာ](#-မြန်မာဘာသာ) | [🇬🇧 English](#-english)

---

## 🇲🇲 မြန်မာဘာသာ

DevStudio သည် Android Device များပေါ်တွင် ကွန်ပျူတာ လုံးဝမလိုဘဲ Android Applications များကို တိုက်ရိုက် ရေးသား၊ စီမံပြီး Standalone APK များအဖြစ် တည်ဆောက် (Build) နိုင်ရန် ဖန်တီးထားသော အဆင့်မြင့် **Mobile IDE (Integrated Development Environment)** တစ်ခု ဖြစ်သည်။

### 🚀 အဓိက လုပ်ဆောင်ချက်များ (Key Features)

* **AI-Powered Intelligent Assistant & Auto-Fix:** App အတွင်း၌တင် **Google Gemini API** ကို တိုက်ရိုက်ချိတ်ဆက်ထားသည်။ ကုဒ်ရေးသားရာတွင် မေးမြန်းနိုင်ခြင်း၊ ရှင်းလင်းခိုင်းခြင်း အပြင် Java Compilation လုပ်စဉ် Error တက်ခဲ့ပါက Gemini LLM က အမှားကို အလိုအလျောက် ရှာဖွေပြင်ဆင်ပေးပြီး (`Auto-Fix`) ကုဒ်အသစ်ဖြင့် Re-compile ချက်ချင်း ပြန်လုပ်ပေးနိုင်သည်။
* **Embedded Local Build Toolchain:** ပြင်ပ Server သို့မဟုတ် အင်တာနက် မလိုဘဲ အောက်ပါ Local Tools များကို အသုံးပြု၍ ဖုန်းပေါ်တွင်တင် အပြီးသတ် APK ထုတ်ပေးနိုင်သည်။
    * **AAPT2 (Resource Linker):** Non-rooted device များတွင်ပါ အဆင်ပြေစေရန် `cache` engine သုံး၍ binary permission ပြဿနာကို ကျော်ဖြတ်ပြီး ပတ်မောင်းသည်။
    * **ECJ (Java Compiler):** Eclipse Compiler for Java ကို အသုံးပြု၍ အမြန်နှုန်း မြင့်မားစွာဖြင့် Java Source များကို `.class` သို့ ပြောင်းလဲပေးသည်။
    * **D8/R8 DEX Engine:** Class ဖိုင်များကို Android Runtime အတွက် `.dex` ဖိုင်သို့ စွမ်းဆောင်ရည်မြင့်စွာ ပြောင်းလဲပေးနိုင်သော Fallback system ပါဝင်သည်။
    * **ApkSigner Tool:** တည်ဆောက်ပြီးသော APK များကို `debug.keystore` ဖြင့် တခါတည်း Sign လုပ်ပေးပြီး ဖုန်းထဲတွင် ချက်ချင်း Install လုပ်နိုင်သည်။
* **Asynchronous File Utility (`FileUtils`):** IDE စနစ် ဖိုင်ဖတ်ရှု/သိမ်းဆည်းရာတွင် ပိတ်ဆို့မှုမရှိစေရန် Safe Directory Creation၊ Recursive Folder Purge (ပရောဂျက်ဖျက်ခြင်း) နှင့် `8KB buffer engine` သုံး မြန်နှုန်းမြင့် ZipCompression စနစ်များ ပါဝင်သည်။
* **Full Storage Access & Scoped Framework:** Android ဗားရှင်းအလိုက် `MANAGE_EXTERNAL_STORAGE` စနစ်များ ထည့်သွင်းထားသဖြင့် ဖုန်းတွင်းရှိ မည်သည့်နေရာမှမဆို Project source code များကို လွတ်လပ်စွာ သိမ်းဆည်း/ဖတ်ရှုနိုင်သည်။
* **Automatic XML Validator:** Layout XML ဖိုင်များ တည်ဆောက်ပုံ မှန်/မမှန်ကို `XmlPullParser` ဖြင့် အလိုအလျောက် ကြိုတင်စစ်ဆေးပေးသည်။
* **Project Templates စနစ်:** *Empty Activity*, *Basic Activity*, *Login Screen* နှင့် *List + Detail* စသည့် templates များကို အခြေခံ၍ ပရောဂျက်အသစ်များကို ချက်ချင်းဖန်တီးနိုင်သည်။

### 🛠 အသုံးပြုထားသော နည်းပညာများ (Tech Stack)

* **Language:** Java / Kotlin (Android)
* **AI Engine:** Google Gemini API (REST Network via OkHttp3 Async Callbacks)
* **File Architecture:** Native Java IO Stack (`ZipOutputStream`, `BufferedReader`, `FileWriter`)
* **Compiler Infrastructure:** Java DexClassLoader (Dynamic JAR Reflection - `ecj.jar`, `d8.jar`, `r8.jar`, `apksigner.jar`)

### 💻 စတင်အသုံးပြုပုံ (Getting Started)

#### လိုအပ်ချက်များ (Prerequisites)
* Android Studio (Ladybug သို့မဟုတ် ထို့ထက်မြင့်သော Version)
* Android SDK (Target API Level 33+)

#### စက်တွင်းထည့်သွင်းနည်း (Setup & Installation)

၁။ ရီပိုစီတိုရီကို Clone လုပ်ပါ -
```bash
git clone [https://github.com/kyawpaingko605/devstudio.git](https://github.com/kyawpaingko605/devstudio.git)
