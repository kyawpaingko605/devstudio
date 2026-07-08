package pro.devstudio.mobile.build;

import android.util.Log;

public class Aapt2 {
    static {
        try {
            // ✅ ပြင်ဆင်ချက်- jniLibs ထဲက libaapt2.so ဖိုင်နာမည်အမှန် 'aapt2' ကို load လုပ်ခြင်း
            System.loadLibrary("aapt2");
        } catch (UnsatisfiedLinkError e) {
            Log.e("DevStudio_Aapt2", "CRITICAL: Cannot load libaapt2.so — " + e.getMessage());
        }
    }

    // Google ရဲ့ AAPT2 core ထဲကို Command array လှမ်းပစ်မယ့် native method
    public static native int execute(String[] args);
}
