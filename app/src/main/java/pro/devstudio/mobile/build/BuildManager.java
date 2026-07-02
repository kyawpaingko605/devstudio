package pro.devstudio.mobile.build;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pro.devstudio.mobile.model.Project;
import pro.devstudio.mobile.util.FileUtils;
import pro.devstudio.mobile.ai.GeminiClient;

/**
 * Orchestrates the DevStudio build pipeline (No-Root Local Build System)
 * Fixed: Added --manifest-package flag to AAPT2 Link to bypass AGP package restrictions in Main Manifest
 */
public class BuildManager {

    public interface BuildCallback {
        void onProgress(String message, int percent);
        void onLog(String line, LogLevel level);
        void onSuccess(File zipFile, File apkFile);
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

    private void prepareLocalTools(BuildCallback cb) throws IOException {
        File jarToolsDir = new File(context.getFilesDir(), "build-tools");
        if (!jarToolsDir.exists()) jarToolsDir.mkdirs();

        // ၁။ .jar နှင့် .keystore ဖိုင်များကို ပုံမှန်အတိုင်း assets မှ ကူးယူမည်
        String[] files = context.getAssets().list("build-tools");
        if (files != null) {
            for (String filename : files) {
                if (filename.endsWith(".jar") || filename.endsWith(".keystore")) {
                    File targetFile = new File(jarToolsDir, filename);
                    if (targetFile.exists()) continue; 

                    try (InputStream in = context.getAssets().open("build-tools/" + filename);
                         OutputStream out = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                    }
                }
            }
        }

        // ၂။ [Self-Extraction] အကယ်၍ စနစ်က libaapt2.so ကို မထုတ်ပေးထားပါက APK ထဲမှ အတင်းဆွဲထုတ်မည်
        File secureBinDir = new File(context.getCodeCacheDir(), "bin");
        if (!secureBinDir.exists()) secureBinDir.mkdirs();
        File localAapt2 = new File(secureBinDir, "libaapt2.so");

        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        File systemAapt2 = new File(nativeLibDir, "libaapt2.so");

        if (!systemAapt2.exists() && !localAapt2.exists()) {
            java.util.zip.ZipFile apkZip = new java.util.zip.ZipFile(context.getApplicationInfo().sourceDir);
            java.util.zip.ZipEntry entry = apkZip.getEntry("lib/arm64-v8a/libaapt2.so");
            if (entry == null) entry = apkZip.getEntry("lib/armeabi-v7a/libaapt2.so");

            if (entry != null) {
                try (InputStream in = apkZip.getInputStream(entry);
                     OutputStream out = new FileOutputStream(localAapt2)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                localAapt2.setExecutable(true, false);
            }
            apkZip.close();
        } else if (localAapt2.exists()) {
            localAapt2.setExecutable(true, false);
        }
    }

    private void buildInternal(Project project, File projectDir, BuildCallback cb, boolean isRetry) {
        executor.execute(() -> {
            try {
                if (isRetry) {
                    cb.onLog("► AI Auto-Fix applied. Re-attempting compilation…", LogLevel.INFO);
                }

                // ── Step 1: Prepare Tools ────────────────────────────────────
                cb.onProgress("Preparing build tools…", 10);
                cb.onLog("► Preparing local build tools from assets…", LogLevel.INFO);
                prepareLocalTools(cb);
                
                String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
                File aapt2Binary = new File(nativeLibDir, "libaapt2.so"); 

                if (!aapt2Binary.exists()) {
                    aapt2Binary = new File(context.getCodeCacheDir(), "bin/libaapt2.so");
                }
                
                if (!aapt2Binary.exists()) {
                    cb.onLog("  ✗ Critical Error: libaapt2.so not found anywhere!", LogLevel.ERROR);
                    cb.onError("Missing Native Core (libaapt2.so)");
                    return;
                } else {
                    aapt2Binary.setExecutable(true, false);
                    cb.onLog("  ✓ Found Core Binary at: " + aapt2Binary.getAbsolutePath(), LogLevel.SUCCESS);
                }

                File toolsDir      = new File(context.getFilesDir(), "build-tools");
                File ecjJar        = new File(toolsDir, "ecj.jar");
                File d8Jar         = new File(toolsDir, "d8.jar");
                File androidJar    = new File(toolsDir, "android.jar");
                File apksignerJar  = new File(toolsDir, "apksigner.jar");
                File debugKeystore = new File(toolsDir, "debug.keystore");
                
                cb.onLog("  ✓ Tools are ready in secure storage.", LogLevel.SUCCESS);

                // ── Step 2: Validate XMLs ────────────────────────────────────
                cb.onProgress("Validating XML files…", 20);
                cb.onLog("► Checking XML layouts…", LogLevel.INFO);
                List<String> xmlErrors = validateXmlFiles(projectDir);
                if (!xmlErrors.isEmpty()) {
                    for (String err : xmlErrors) cb.onLog("  ✗ " + err, LogLevel.ERROR);
                    cb.onError("XML Validation Failed");
                    return;
                }
                cb.onLog("  ✓ All XML files valid.", LogLevel.SUCCESS);

                // Paths Setup
                String manifestPath = new File(projectDir, "app/src/main/AndroidManifest.xml").getAbsolutePath();
                String resPath      = new File(projectDir, "app/src/main/res").getAbsolutePath();
                String srcPath      = new File(projectDir, "app/src/main/java").getAbsolutePath();
                
                String buildDir         = new File(projectDir, "app/build").getAbsolutePath();
                String genPath          = buildDir + "/generated";
                String objPath          = buildDir + "/obj";
                String intermediatesRes = buildDir + "/intermediates/res";
                String binDir           = buildDir + "/outputs/apk/release";

                // Clean and Create Directories
                deleteDir(new File(buildDir));
                new File(genPath).mkdirs();
                new File(objPath).mkdirs();
                new File(intermediatesRes).mkdirs();
                new File(binDir).mkdirs();

                // ── Step 3: AAPT2 Compile & Link ─────────────────────────────
                cb.onProgress("Compiling resources…", 40);
                cb.onLog("► [1/5] Compiling resources via AAPT2…", LogLevel.INFO);
                
                List<String> compileCmd = List.of(aapt2Binary.getAbsolutePath(), "compile", "--dir", resPath, "-o", intermediatesRes + "/resources.zip");
                if (runProcess(compileCmd, projectDir, cb) != 0) { cb.onError("AAPT2 Compile Failed"); return; }

                cb.onLog("► [2/5] Linking resources and generating R.java…", LogLevel.INFO);
                String unalignedApk = binDir + "/app-unaligned.apk";
                
                // Fixed: Added `--manifest-package` flag to bypass the package attribute requirement in source XML
                List<String> linkCmd = List.of(
                    aapt2Binary.getAbsolutePath(), "link", 
                    "-I", androidJar.getAbsolutePath(), 
                    "--manifest", manifestPath, 
                    "--manifest-package", "pro.devstudio.mobile.targetapp",
                    "-o", unalignedApk, 
                    "--java", genPath, 
                    intermediatesRes + "/resources.zip"
                );
                if (runProcess(linkCmd, projectDir, cb) != 0) { cb.onError("AAPT2 Link Failed"); return; }

                // ── Step 4: Java Compilation (ECJ) ───────────────────────────
                cb.onProgress("Compiling Java code…", 60);
                cb.onLog("► [3/5] Compiling Java source codes with ECJ…", LogLevel.INFO);
                List<String> ecjCmd = new ArrayList<>();
                ecjCmd.add("java"); ecjCmd.add("-jar"); ecjCmd.add(ecjJar.getAbsolutePath());
                ecjCmd.add("-d"); ecjCmd.add(objPath);
                ecjCmd.add("-cp"); ecjCmd.add(androidJar.getAbsolutePath());
                ecjCmd.add("-source"); ecjCmd.add("1.8"); ecjCmd.add("-target"); ecjCmd.add("1.8");
                addAllFiles(new File(srcPath), ".java", ecjCmd);
                addAllFiles(new File(genPath), ".java", ecjCmd);
                
                if (runProcess(ecjCmd, projectDir, cb) != 0) {
                    cb.onLog("  ✗ Java Compilation Failed. Launching AI Fix…", LogLevel.ERROR);
                    if (!isRetry && geminiClient.hasApiKey()) {
                        triggerAiAutoFix(project, projectDir, cb);
                    } else {
                        cb.onError("Java Compilation Failed");
                    }
                    return;
                }

                // ── Step 5: DEX Conversion (D8) ──────────────────────────────
                cb.onProgress("Converting to DEX…", 80);
                cb.onLog("► [4/5] Converting class files to DEX (d8)…", LogLevel.INFO);
                String intermediatesDex = buildDir + "/intermediates/dex";
                new File(intermediatesDex).mkdirs();
                
                List<String> d8Cmd = new ArrayList<>();
                d8Cmd.add("java"); d8Cmd.add("-jar"); d8Cmd.add(d8Jar.getAbsolutePath());
                d8Cmd.add("--lib"); d8Cmd.add(androidJar.getAbsolutePath());
                d8Cmd.add("--output"); d8Cmd.add(intermediatesDex);
                addAllFiles(new File(objPath), ".class", d8Cmd);
                if (runProcess(d8Cmd, projectDir, cb) != 0) { cb.onError("DEX Conversion Failed"); return; }

                // Inject classes.dex into APK
                File dexFile = new File(intermediatesDex, "classes.dex");
                File unalignedApkFile = new File(unalignedApk);
                try {
                    Map<String, String> env = new HashMap<>();
                    env.put("create", "false");
                    URI uri = URI.create("jar:" + unalignedApkFile.toURI());
                    try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                        Path nf = fs.getPath("classes.dex");
                        Files.copy(dexFile.toPath(), nf, StandardCopyOption.REPLACE_EXISTING);
                    }
                    cb.onLog("  ✓ classes.dex injected into APK successfully.", LogLevel.SUCCESS);
                } catch (Exception e) {
                    cb.onLog("  ✗ Failed to inject classes.dex: " + e.getMessage(), LogLevel.ERROR);
                    cb.onError("DEX Injection Failed");
                    return;
                }

                // ── Step 6: Sign APK ─────────────────────────────────────────
                cb.onProgress("Signing APK…", 95);
                cb.onLog("► [5/5] Signing APK with debug.keystore…", LogLevel.INFO);
                File releaseApk = new File(binDir, "app-release.apk");

                if (apksignerJar.exists() && debugKeystore.exists()) {
                    List<String> signCmd = List.of(
                        "java", "-jar", apksignerJar.getAbsolutePath(), "sign",
                        "--ks", debugKeystore.getAbsolutePath(),
                        "--ks-pass", "pass:android",
                        "--key-pass", "pass:android",
                        "--out", releaseApk.getAbsolutePath(),
                        unalignedApk
                    );
                    if (runProcess(signCmd, projectDir, cb) == 0) {
                        cb.onLog("  ✓ APK Signed successfully!", LogLevel.SUCCESS);
                    } else {
                        cb.onLog("  ⚠ APK Sign failed, using unsigned APK.", LogLevel.WARNING);
                        unalignedApkFile.renameTo(releaseApk);
                    }
                } else {
                    unalignedApkFile.renameTo(releaseApk);
                }

                // Backup ZIP
                File cacheDir = new File(context.getCacheDir(), "builds");
                cacheDir.mkdirs();
                File zipOut = new File(cacheDir, project.dirName() + ".zip");
                FileUtils.zipDirectory(projectDir, zipOut);

                cb.onProgress("Done.", 100);
                cb.onLog("━━━━━━━━━━━━━━━━━━━━━━━━", LogLevel.INFO);
                cb.onLog("✓ Build complete — Standalone App Ready!", LogLevel.SUCCESS);
                cb.onSuccess(zipOut, releaseApk);

            } catch (Exception e) {
                cb.onLog("✗ Build error: " + e.getMessage(), LogLevel.ERROR);
                cb.onError(e.getMessage());
            }
        });
    }

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
            String simulatedErrorLog = "Compilation failed inside: " + targetCodeFile.getName() + ". Check for syntax errors.";
            cb.onLog("  ℹ Sending code to Gemini…", LogLevel.INFO);

            geminiClient.fixCodeAuto(currentCode, simulatedErrorLog, fixedCode -> {
                try {
                    if (fixedCode != null && !fixedCode.trim().isEmpty()) {
                        FileUtils.writeFile(targetCodeFile, fixedCode.trim());
                        cb.onLog("  ✓ AI successfully patched " + targetCodeFile.getName(), LogLevel.SUCCESS);
                        buildInternal(project, projectDir, cb, true);
                    } else {
                        cb.onLog("  ✗ AI returned empty code.", LogLevel.ERROR);
                    }
                } catch (Exception e) {
                    cb.onLog("  ✗ Failed to save AI fixed code: " + e.getMessage(), LogLevel.ERROR);
                }
            }, error -> cb.onLog("  ✗ AI Auto-Fix failed: " + error, LogLevel.ERROR));

        } catch (Exception e) {
            cb.onLog("  ✗ Error reading code file for AI: " + e.getMessage(), LogLevel.ERROR);
        }
    }

    private int runProcess(List<String> command, File workingDir, BuildCallback cb) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        
        // Dynamic library ချိတ်ဆက်မှုအမှား (libandroid-base.so not found) ကို ကျော်ဖြတ်ရန်
        // Environment ထဲတွင် လိုအပ်သော System Library လမ်းကြောင်းအားလုံးကို စုံလင်စွာ လှမ်းညွှန်းပေးခြင်း
        Map<String, String> env = pb.environment();
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        
        String systemLibPath = "/system/lib64:/system/lib"
                + ":/apex/com.android.runtime/lib64:/apex/com.android.runtime/lib"
                + ":/apex/com.android.art/lib64:/apex/com.android.art/lib"
                + ":/system/apex/com.android.runtime/lib64:/system/apex/com.android.runtime/lib";
                
        env.put("LD_LIBRARY_PATH", nativeLibDir + ":" + systemLibPath);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                cb.onLog(line, LogLevel.INFO);
            }
        }
        return process.waitFor();
    }

    private void addAllFiles(File dir, String extension, List<String> list) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) addAllFiles(f, extension, list);
            else if (f.getName().endsWith(extension)) list.add(f.getAbsolutePath());
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

    private List<String> validateXmlFiles(File dir) {
        List<String> errors = new ArrayList<>();
        collectXmlFiles(dir, errors);
        return errors;
    }

    private void collectXmlFiles(File dir, List<String> errors) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectXmlFiles(f, errors);
            else if (f.getName().endsWith(".xml")) {
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
            while (parser.next() != XmlPullParser.END_DOCUMENT) {}
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    private void deleteDir(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File c : files) deleteDir(c);
            }
        }
        f.delete();
    }
}
