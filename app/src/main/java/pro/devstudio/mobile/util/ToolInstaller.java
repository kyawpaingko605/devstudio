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
 * Small helper to install native executables placed under assets/tools/<abi>/
 *
 * Usage:
 * - Put per-ABI binaries in app/src/main/assets/tools/arm64-v8a/toolname
 *   app/src/main/assets/tools/armeabi-v7a/toolname
 *   etc.
 * - On app start call ToolInstaller.installAllFromAssets(context)
 *
 * This will copy the first matching binary for the device ABI into
 * the app's internal files directory (files/tools/) and make it executable.
 */
public final class ToolInstaller {
    private static final String TAG = "ToolInstaller";

    private ToolInstaller() {}

    public static void installAllFromAssets(Context ctx) {
        AssetManager am = ctx.getAssets();
        String base = "tools";

        try {
            // Try each ABI in order and copy files found under tools/<abi>/*
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

            // Fallback: try files directly under tools/
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
        File outDir = new File(ctx.getFilesDir(), "tools");
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, outName);

        try (InputStream is = am.open(assetPath);
             FileOutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
            os.getFD().sync();

            // Try setExecutable and fallback to chmod
            boolean execSet = out.setExecutable(true);
            if (!execSet) {
                try {
                    Runtime.getRuntime().exec(new String[]{"chmod", "755", out.getAbsolutePath()}).waitFor();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to chmod via shell", e);
                }
            }

            Log.i(TAG, "Installed tool: " + out.getAbsolutePath());
            return true;
        } catch (IOException e) {
            // asset doesn't exist or copy failed
            return false;
        }
    }
}
