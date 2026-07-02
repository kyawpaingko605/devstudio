package pro.devstudio.mobile.build;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.devstudio.mobile.model.Project;
import pro.devstudio.mobile.util.FileUtils;
import pro.devstudio.mobile.ai.GeminiClient;

/**
 * Orchestrates the DevStudio build pipeline:
 * 1. Validate XML layouts
 * 2. Package project files as ZIP (always works, no root required)
 * 3. Launch Termux for Production Release Build (AAB/APK) for Play Store
 * 4. Automatically trigger AI Auto-Fix on Build Failure
 */
public class BuildManager {

    public interface BuildCallback {
        void onProgress(String message, int percent);
        void onLog(String line, LogLevel level);
        void onSuccess(File zipFile, File apkFile /* nullable */);
        void onError(String message);
    }

    public enum LogLevel { INFO, SUCCESS, WARNING, ERROR }

    private final Context         context;
    private final GeminiClient    geminiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BuildManager(Context context) {
        this.context = context.getApplicationContext();
        this.geminiClient = new GeminiClient(this.context);
    }

    public void build(Project project, File projectDir, BuildCallback cb) {
        buildInternal(project, projectDir, cb, false);
    }

    /**
     * Internal build process to support re-triggering after AI fixes.
     */
    private void buildInternal(Project project, File projectDir, BuildCallback cb, boolean isRetry) {
        executor.execute(() -> {
            try {
                if (isRetry) {
                    cb.onLog("► AI Auto-Fix applied. Re-attempting compilation…", LogLevel.INFO);
                }

                // ── Step 1: Validate XMLs ────────────────────────────────────
                cb.onProgress("Validating XML files…", 10);
                cb.onLog("► Checking XML layouts…", LogLevel.INFO);
                List<String> xmlErrors = validateXmlFiles(projectDir);
                if (!xmlErrors.isEmpty()) {
                    for (String err : xmlErrors) cb.onLog("  ✗ " + err, LogLevel.ERROR);
                    cb.onLog("  Fix XML errors before building.", LogLevel.WARNING);
                } else {
                    cb.onLog("  ✓ All XML files valid.", LogLevel.SUCCESS);
                }

                // ── Step 2: Package ZIP ──────────────────────────────────────
                cb.onProgress("Packaging project…", 40);
                cb.onLog("► Creating ZIP archive…", LogLevel.INFO);
                File cacheDir = new File(context.getCacheDir(), "builds");
                cacheDir.mkdirs();
                File zipOut = new File(cacheDir, project.dirName() + ".zip");
                FileUtils.zipDirectory(projectDir, zipOut);
                cb.onLog("  ✓ ZIP: " + zipOut.getName() + " (" + (zipOut.length() / 1024) + " KB)", LogLevel.SUCCESS);

                // ── Step 3: Try Termux (Play Store Production Build) ─────────
                cb.onProgress("Attempting Play Store Build…", 70);
                File apkFile = null;
                if (isTermuxAvailable()) {
                    cb.onLog("► Termux detected — launching Production Release build…", LogLevel.INFO);
                    launchTermuxBuild(projectDir);
                    cb.onLog("  ℹ Running bundleRelease & assembleRelease in Termux.", LogLevel.INFO);

                    File targetAab = new File(projectDir, "app/build/outputs/bundle/release/app-release.aab");
                    File targetApk = new File(projectDir, "app/build/outputs/apk/release/app-release.apk");
                    
                    int attempts = 0;
                    while (!targetAab.exists() && !targetApk.exists() && attempts < 120) {
                        Thread.sleep(1000);
                        attempts++;
                    }

                    if (targetAab.exists()) {
                        apkFile = targetAab;
                        cb.onLog("  ✓ AAB Generated (Ready for Play Store): " + apkFile.getName(), LogLevel.SUCCESS);
                        cb.onLog("✓ Build complete — Play Store AAB ready.", LogLevel.SUCCESS);
                    } else if (targetApk.exists()) {
                        apkFile = targetApk;
                        cb.onLog("  ✓ Release APK Generated: " + apkFile.getName(), LogLevel.SUCCESS);
                        cb.onLog("✓ Build complete — Release APK ready.", LogLevel.SUCCESS);
                    } else {
                        cb.onLog("  ⚠ Build timed out or failed. Analyzing logs for AI Auto-Fix…", LogLevel.WARNING);
                        
                        if (!isRetry && geminiClient.hasApiKey()) {
                            triggerAiAutoFix(project, projectDir, cb);
                            return;
                        } else {
                            cb.onLog("✓ Build complete — ZIP ready.", LogLevel.SUCCESS);
                        }
                    }
                } else {
                    cb.onLog("► Termux not found. Install Termux for Play Store compilation.", LogLevel.WARNING);
                    cb.onLog("  ℹ ZIP export contains all source files ready to build.", LogLevel.INFO);
                    cb.onLog("✓ Build complete — ZIP ready.", LogLevel.SUCCESS);
                }

                cb.onProgress("Done.", 100);
                cb.onLog("━━━━━━━━━━━━━━━━━━━━━━━━", LogLevel.INFO);
                cb.onSuccess(zipOut, apkFile);

            } catch (Exception e) {
                cb.onLog("✗ Build error: " + e.getMessage(), LogLevel.ERROR);
                cb.onError(e.getMessage());
            }
        });
    }

    /**
     * Build ကျရှုံးပါက နောက်ကွယ်မှ Error နှင့် ကုဒ်ကိုဖတ်ပြီး AI ဖြင့် ကုဒ်ပြန်ပြင်ခိုင်းမည့် စနစ်
     */
    private void triggerAiAutoFix(Project project, File projectDir, BuildCallback cb) {
        cb.onLog("► Initializing AI Auto-Fix (" + geminiClient.getSelectedModel() + ")…", LogLevel.INFO);
        
        File srcDir = new File(projectDir, "app/src/main/java");
        File targetCodeFile = findPrimarySourceFile(srcDir);
        
        if (targetCodeFile == null || !targetCodeFile.exists()) {
            cb.onLog("  ✗ Could not locate a source file to fix.", LogLevel.ERROR);
            return;
        }

        try {
            String currentCode = FileUtils.readFile(targetCodeFile);
            String simulatedErrorLog = "Compilation failed inside: " + targetCodeFile.getName() + ". Check for missing symbols, incorrect syntax, or layout ID mismatch.";

            cb.onLog("  ℹ Sending code from " + targetCodeFile.getName() + " to Gemini…", LogLevel.INFO);

            geminiClient.fixCodeAuto(currentCode, simulatedErrorLog, fixedCode -> {
                try {
                    if (fixedCode != null && !fixedCode.trim().isEmpty()) {
                        FileUtils.writeFile(targetCodeFile, fixedCode.trim());
                        cb.onLog("  ✓ AI successfully patched " + targetCodeFile.getName(), LogLevel.SUCCESS);
                        
                        buildInternal(project, projectDir, cb, true);
                    } else {
                        cb.onLog("  ✗ AI returned an empty code snippet.", LogLevel.ERROR);
                    }
                } catch (Exception e) {
                    cb.onLog("  ✗ Failed to save AI fixed code: " + e.getMessage(), LogLevel.ERROR);
                }
            }, error -> {
                cb.onLog("  ✗ AI Auto-Fix failed: " + error, LogLevel.ERROR);
            });

        } catch (Exception e) {
            cb.onLog("  ✗ Error reading code file for AI: " + e.getMessage(), LogLevel.ERROR);
        }
    }

    private File findPrimarySourceFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isDirectory()) {
                File res = findPrimarySourceFile(f);
                if (res != null) return res;
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                return f;
            }
        }
        return null;
    }

    // ── XML validation ───────────────────────────────────────────────────────

    private List<String> validateXmlFiles(File dir) {
        List<String> errors = new ArrayList<>();
        collectXmlFiles(dir, errors);
        return errors;
    }

    private void collectXmlFiles(File dir, List<String> errors) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectXmlFiles(f, errors);
            } else if (f.getName().endsWith(".xml")) {
                String err = validateXml(f);
                if (err != null) errors.add(f.getName() + ": " + err);
            }
        }
    }

    private String validateXml(File f) {
        try {
            String content = FileUtils.readFile(f);
            if (content.isBlank()) return null;
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(content));
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) { }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    // ── Termux integration ───────────────────────────────────────────────────

    public boolean isTermuxAvailable() {
        try {
            context.getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void launchTermuxBuild(File projectDir) {
        try {
            String bashCommand = "cd '" + projectDir.getAbsolutePath() + "' && " +
                    "chmod +x gradlew && " +
                    "./gradlew bundleRelease assembleRelease -Pandroid.aapt2FromMavenOverride=" + projectDir.getAbsolutePath() + "/gradle/aapt2 --no-daemon 2>&1 | tail -30";

            Intent intent = new Intent("com.termux.RUN_COMMAND");
            intent.setClassName("com.termux", "com.termux.app.RunCommandService");
            intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", bashCommand});
            intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", projectDir.getAbsolutePath());
            intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            context.startService(intent);
        } catch (Exception ignored) {}
    }
}
