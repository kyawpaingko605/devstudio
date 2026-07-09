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
 * Small helper to install native executables placed under assets/build-tools/
 *
 * Usage:
 * - Put per-ABI binaries in app/src/main/assets/build-tools/arm64-v8a/toolname
 *   app/src/main/assets/build-tools/armeabi-v7a/toolname
 *   etc.
 * - On app start call ToolInstaller.installAllFromAssets(context)
 *
 * This will copy the first matching binary for the device ABI into
 * the app's internal files directory (files/build-tools/) and make it executable.
 */
public final class ToolInstaller {
    private static final String TAG = "ToolInstaller";

    private ToolInstaller() {}

    public static void installAllFromAssets(Context ctx) {
        AssetManager am = ctx.getAssets();
        String base = "build-tools";

        try {
            String[] abis = Build.SUPPORTED_ABIS;
            boolean anyInstalled = false;

            for (String abi : abis) {
                String path = base + "/" + abi;
                try {
                    String[] files = am.list(path);
                    if (files == null || files.length == 0) continue;
                    for (String name : files) {
                        boolean ok = copyAssetToFiles(ctx, path + "/" + name, name);
                        if (ok) anyInstalled = true;
                    }
                } catch (IOException ignored) {
                    // directory not present for this abi
                }
            }

            if (!anyInstalled) {
                try {
                    String[] files = am.list(base);
                    if (files != null) {
                        for (String name : files) {
                            boolean ok = copyAssetToFiles(ctx, base + "/" + name, name);
                            if (ok) anyInstalled = true;
                        }
                    }
                } catch (IOException ignored) {}
            }

            Log.i(TAG, "installAllFromAssets completed, anyInstalled=" + anyInstalled);
        } catch (Exception e) {
            Log.w(TAG, "Error while installing tools from assets", e);
        }
    }

    private static boolean copyAssetToFiles(Context ctx, String assetPath, String outName) {
        AssetManager am = ctx.getAssets();
        File outDir = new File(ctx.getFilesDir(), "build-tools");
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, outName);

        try (InputStream is = am.open(assetPath);
             FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
            os.getFD().sync();

            // ✅ aapt2 အတွက် executable permission ပေးပါ
            boolean execSet = out.setExecutable(true, false);
            
            // ✅ aapt2 ဆိုရင် read permission ပါပေးပါ
            if (outName.equals("aapt2")) {
                out.setReadable(true, false);
                out.setWritable(true, false);
                Log.i(TAG, "✅ aapt2 installed at: " + out.getAbsolutePath());
            }
            
            Log.i(TAG, "Installed tool: " + out.getAbsolutePath() + ", executable=" + execSet);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Failed to copy " + assetPath, e);
            return false;
        }
    }
}
