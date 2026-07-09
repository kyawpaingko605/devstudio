package pro.devstudio.mobile.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Helper to install build tools from assets/build-tools/
 * 
 * Usage:
 * - Put all build tools in app/src/main/assets/build-tools/
 * - On app start call ToolInstaller.installAllFromAssets(context)
 * 
 * This will copy all files from assets/build-tools/ to files/build-tools/
 * and make executables (like aapt2) executable.
 */
public final class ToolInstaller {
    private static final String TAG = "ToolInstaller";

    private ToolInstaller() {}

    public static void installAllFromAssets(Context ctx) {
        AssetManager am = ctx.getAssets();
        String base = "build-tools";
        File outDir = new File(ctx.getFilesDir(), "build-tools");
        
        // ✅ outDir ကိုဖန်တီးပါ
        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            boolean anyInstalled = false;
            
            // ✅ assets/build-tools/ ထဲက ဖိုင်တွေကိုရှာပါ
            String[] files = am.list(base);
            if (files != null && files.length > 0) {
                for (String name : files) {
                    // ✅ aapt2_mobile.zip ကိုကျော်ပါ (0 bytes)
                    if (name.equals("aapt2_mobile.zip")) {
                        Log.i(TAG, "Skipping " + name + " (empty file)");
                        continue;
                    }
                    
                    String assetPath = base + "/" + name;
                    boolean ok = copyAssetToFiles(ctx, assetPath, name, outDir);
                    if (ok) anyInstalled = true;
                }
            } else {
                Log.w(TAG, "No files found in assets/build-tools/");
            }

            Log.i(TAG, "installAllFromAssets completed, anyInstalled=" + anyInstalled);
        } catch (Exception e) {
            Log.w(TAG, "Error while installing tools from assets", e);
        }
    }

    private static boolean copyAssetToFiles(Context ctx, String assetPath, String outName, File outDir) {
        AssetManager am = ctx.getAssets();
        File out = new File(outDir, outName);

        try (InputStream is = am.open(assetPath);
             FileOutputStream os = new FileOutputStream(out)) {
            
            // ✅ ဖိုင်ကို copy လုပ်ပါ
            byte[] buf = new byte[8192];
            int r;
            long totalBytes = 0;
            while ((r = is.read(buf)) > 0) {
                os.write(buf, 0, r);
                totalBytes += r;
            }
            os.getFD().sync();
            
            Log.i(TAG, "Copied " + assetPath + " → " + out.getAbsolutePath() + " (" + totalBytes + " bytes)");

            // ✅ aapt2 နဲ့ non-jar/non-keystore ဖိုင်တွေကို executable ဖြစ်အောင်လုပ်ပါ
            boolean isExecutable = !outName.endsWith(".jar") && 
                                   !outName.endsWith(".keystore") && 
                                   !outName.endsWith(".txt") &&
                                   !outName.endsWith(".aar");
            
            if (isExecutable || outName.equals("aapt2")) {
                out.setExecutable(true, false);
                out.setReadable(true, false);
                out.setWritable(true, false);
                Log.i(TAG, "✅ Set executable permission for: " + outName);
            }
            
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy " + assetPath + ": " + e.getMessage());
            return false;
        }
    }
}
