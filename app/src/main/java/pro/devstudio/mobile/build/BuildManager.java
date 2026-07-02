package pro.devstudio.mobile.build;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

import dalvik.system.DexClassLoader;
import pro.devstudio.mobile.model.Project;
import pro.devstudio.mobile.util.FileUtils;
import pro.devstudio.mobile.ai.GeminiClient;

/**
 * Orchestrates the DevStudio build pipeline (No-Root Local Build System)
 * Fixed: Completely eliminated System.exit() via safe Direct In-App Core API Loading.
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

    // ── PROJECT GENERATION SYSTEM ────────────────────────────────────────────

    /**
     * Creates a standard Android project layout from scratch inside the app's working directory.
     */
    public File createNewProjectStructure(String projectName, String packageName) throws Exception {
        File projectsDir = new File(context.getFilesDir(), "projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();

        File projectRoot = new File(projectsDir, projectName);
        String packagePath = packageName.replace(".", "/");
        
        File javaDir = new File(projectRoot, "app/src/main/java/" + packagePath);
        File resLayoutDir = new File(projectRoot, "app/src/main/res/layout");
        File resValuesDir = new File(projectRoot, "app/src/main/res/values");
        
        javaDir.mkdirs();
        resLayoutDir.mkdirs();
        resValuesDir.mkdirs();

        // 1. AndroidManifest.xml
        String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\">\n" +
                "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>";
        writeProjectFile(new File(projectRoot, "app/src/main/AndroidManifest.xml"), manifest);

        // 2. MainActivity.java
        String javaCode = "package " + packageName + ";\n\n" +
                "import android.app.Activity;\n" +
                "import android.os.Bundle;\n\n" +
                "public class MainActivity extends Activity {\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        int layoutId = getResources().getIdentifier(\"activity_main\", \"layout\", getPackageName());\n" +
                "        setContentView(layoutId);\n" +
                "    }\n" +
                "}";
        writeProjectFile(new File(javaDir, "MainActivity.java"), javaCode);

        // 3. activity_main.xml
        String layout = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:gravity=\"center\"\n" +
                "    android:background=\"#FAFAFA\">\n" +
                "\n" +
                "    <TextView\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Hello DevStudio!\"\n" +
                "        android:textSize=\"24sp\"\n" +
                "        android:textColor=\"#212121\"\n" +
                "        android:textStyle=\"bold\"/>\n" +
                "</LinearLayout>";
        writeProjectFile(new File(resLayoutDir, "activity_main.xml"), layout);

        // 4. build.gradle
        String gradle = "plugins {\n    id 'com.android.application'\n}\n" +
                "android {\n    namespace '" + packageName + "'\n    compileSdk 34\n}";
        writeProjectFile(new File(projectRoot, "build.gradle"), gradle);

        return projectRoot;
    }

    private void writeProjectFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    // ── BUILD PIPELINE MOTOR ─────────────────────────────────────────────────

    public void build(Project project, File projectDir, BuildCallback cb) {
        buildInternal(project, projectDir, cb, false);
    }

    private void prepareLocalTools(BuildCallback cb) throws IOException {
        File jarToolsDir = new File(context.getFilesDir(), "build-tools");
        if (!jarToolsDir.exists()) jarToolsDir.mkdirs();

        String[] files = context.getAssets().list("build-tools");
        if (files != null) {
            for (String filename : files) {
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
                if (!filename.endsWith(".jar") && !filename.endsWith(".keystore")) {
                    targetFile.setExecutable(true, false);
                }
            }
        }

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

                // ── Step 3: AAPT2 Compile & Link ──────────────────────────────
                cb.onProgress("Compiling resources…", 40);
                cb.onLog("► [1/5] Compiling resources via AAPT2…", LogLevel.INFO);
                
                File rawManifestFile = new File(manifestPath);
                String backupManifestContent = FileUtils.readFile(rawManifestFile);
                try {
                    String temporaryManifest = backupManifestContent.replace("<manifest", "<manifest package=\"pro.devstudio.mobile.targetapp\"");
                    FileUtils.writeFile(rawManifestFile, temporaryManifest);
                } catch (Exception e) {
                    cb.onLog("  ✗ Source Manifest Modification Failed: " + e.getMessage(), LogLevel.ERROR);
                    cb.onError("Manifest Inject Failed");
                    return;
                }

                List<String> compileCmd = List.of(aapt2Binary.getAbsolutePath(), "compile", "--dir", resPath, "-o", intermediatesRes + "/resources.zip");
                if (runProcess(compileCmd, projectDir, cb) != 0) { 
                    FileUtils.writeFile(rawManifestFile, backupManifestContent);
                    cb.onError("AAPT2 Compile Failed"); 
                    return; 
                }

                cb.onLog("► [2/5] Linking resources and generating R.java…", LogLevel.INFO);
                String unalignedApk = binDir + "/app-unaligned.apk";
                
                List<String> linkCmd = List.of(
                    aapt2Binary.getAbsolutePath(), "link", 
                    "-I", androidJar.getAbsolutePath(), 
                    "--manifest", manifestPath, 
                    "-o", unalignedApk, 
                    "--java", genPath, 
                    intermediatesRes + "/resources.zip"
                );
                int linkResult = runProcess(linkCmd, projectDir, cb);

                try {
                    FileUtils.writeFile(rawManifestFile, backupManifestContent);
                    cb.onLog("  ✓ Source Manifest restored to clean state.", LogLevel.SUCCESS);
                } catch (Exception e) {
                    cb.onLog("  ⚠ Failed to restore source Manifest: " + e.getMessage(), LogLevel.WARNING);
                }

                if (linkResult != 0) { cb.onError("AAPT2 Link Failed"); return; }

                // ── Step 4: Java Compilation (In-App) ─────────────────────────
                cb.onProgress("Compiling Java code…", 60);
                cb.onLog("► [3/5] Compiling Java source codes with In-App ECJ Tool…", LogLevel.INFO);
                
                List<String> ecjArgs = new ArrayList<>();
                ecjArgs.add("-d"); ecjArgs.add(objPath);
                ecjArgs.add("-cp"); ecjArgs.add(androidJar.getAbsolutePath());
                ecjArgs.add("-source"); ecjArgs.add("1.8"); 
                ecjArgs.add("-target"); ecjArgs.add("1.8");
                addAllFiles(new File(srcPath), ".java", ecjArgs);
                addAllFiles(new File(genPath), ".java", ecjArgs);
                
                boolean ecjSuccess = runJarMainInApp(ecjJar, "org.eclipse.jdt.internal.compiler.batch.Main", ecjArgs.toArray(new String[0]), cb);
                if (!ecjSuccess) {
                    cb.onLog("  ✗ Java Compilation Failed. Launching AI Fix…", LogLevel.ERROR);
                    if (!isRetry && geminiClient.hasApiKey()) {
                        triggerAiAutoFix(project, projectDir, cb);
                    } else {
                        cb.onError("Java Compilation Failed");
                    }
                    return;
                }

                // ── Step 5: DEX Conversion (In-App) ───────────────────────────
                cb.onProgress("Converting to DEX…", 80);
                cb.onLog("► [4/5] Converting class files to DEX via In-App D8 Tool…", LogLevel.INFO);
                String intermediatesDex = buildDir + "/intermediates/dex";
                new File(intermediatesDex).mkdirs();
                
                List<String> d8Args = new ArrayList<>();
                d8Args.add("--lib"); d8Args.add(androidJar.getAbsolutePath());
                d8Args.add("--output"); d8Args.add(intermediatesDex);
                addAllFiles(new File(objPath), ".class", d8Args);
                
                boolean d8Success = runJarMainInApp(d8Jar, "com.android.tools.r8.D8", d8Args.toArray(new String[0]), cb);
                if (!d8Success) { cb.onError("DEX Conversion Failed"); return; }

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

                // ── Step 6: Sign APK (In-App) ─────────────────────────────────
                cb.onProgress("Signing APK…", 95);
                cb.onLog("► [5/5] Signing APK with debug.keystore…", LogLevel.INFO);
                File releaseApk = new File(binDir, "app-release.apk");

                if (apksignerJar.exists() && debugKeystore.exists()) {
                    String[] signArgs = {
                        "sign",
                        "--ks", debugKeystore.getAbsolutePath(),
                        "--ks-pass", "pass:android",
                        "--key-pass", "pass:android",
                        "--out", releaseApk.getAbsolutePath(),
                        unalignedApk
                    };
                    boolean signSuccess = runJarMainInApp(apksignerJar, "com.android.apksigner.ApkSignerTool", signArgs, cb);
                    if (signSuccess) {
                        cb.onLog("  ✓ APK Signed successfully!", LogLevel.SUCCESS);
                    } else {
                        cb.onLog("  ⚠ APK Sign failed, using unsigned APK.", LogLevel.WARNING);
                        unalignedApkFile.renameTo(releaseApk);
                    }
                } else {
                    unalignedApkFile.renameTo(releaseApk);
                }

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

    /**
     * Safe In-App Execution Runner.
     * Uses strict Primitive Reflection to avoid ClassNotFound errors at runtime.
     */
    private boolean runJarMainInApp(File jarFile, String mainClassName, String[] args, BuildCallback cb) {
        try {
            File optimizedDir = new File(context.getCacheDir(), "dex-opt");
            if (!optimizedDir.exists()) optimizedDir.mkdirs();

            DexClassLoader classLoader = new DexClassLoader(
                jarFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                null,
                ClassLoader.getSystemClassLoader()
            );

            java.io.StringWriter outWriter = new java.io.StringWriter();
            java.io.StringWriter errWriter = new java.io.StringWriter();
            java.io.PrintWriter outPrintWriter = new java.io.PrintWriter(outWriter);
            java.io.PrintWriter errPrintWriter = new java.io.PrintWriter(errWriter);

            if (mainClassName.contains("org.eclipse.jdt")) {
                // ECJ Core Reflection: Loads BatchCompiler safely without class cast crashes
                Class<?> batchCompilerClass = classLoader.loadClass("org.eclipse.jdt.core.compiler.batch.BatchCompiler");
                Class<?> progressClass = classLoader.loadClass("org.eclipse.jdt.core.compiler.CompilationProgress");
                
                Method compileMethod = batchCompilerClass.getMethod("compile", 
                    String[].class, java.io.PrintWriter.class, java.io.PrintWriter.class, progressClass);
                
                boolean success = (boolean) compileMethod.invoke(null, args, outPrintWriter, errPrintWriter, null);
                flushLogsToCallback(outWriter, errWriter, cb);
                return success;

            } else if (mainClassName.contains("com.android.tools.r8.D8")) {
                // D8 Code Processing via system proxy interface targeting diagnostics handler
                Class<?> d8Class = classLoader.loadClass("com.android.tools.r8.D8");
                Class<?> d8CommandClass = classLoader.loadClass("com.android.tools.r8.D8Command");
                Class<?> diagnosticsHandlerClass = classLoader.loadClass("com.android.tools.r8.DiagnosticsHandler");
                
                Method parseMethod = d8CommandClass.getMethod("parse", String[].class, diagnosticsHandlerClass);
                
                Object diagnosticsHandler = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{diagnosticsHandlerClass},
                    (proxy, method, methodArgs) -> null
                );
                
                Object command = parseMethod.invoke(null, args, diagnosticsHandler);
                Method runMethod = d8Class.getMethod("run", d8CommandClass);
                runMethod.invoke(null, command);
                return true;

            } else {
                // For general command-line execution blocks like ApkSignerTool
                Class<?> mainClass = classLoader.loadClass(mainClassName);
                Method mainMethod = mainClass.getMethod("main", String[].class);
                
                java.io.PrintStream originalOut = System.out;
                java.io.PrintStream originalErr = System.err;
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.PrintStream customPS = new java.io.PrintStream(baos);
                
                System.setOut(customPS);
                System.setErr(customPS);
                try {
                    mainMethod.invoke(null, (Object) args);
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    String logOutput = baos.toString();
                    if (!logOutput.isBlank()) cb.onLog(logOutput, LogLevel.INFO);
                }
                return true;
            }

        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            cb.onLog("  ✗ Process Interrupted: " + (cause != null ? cause.getMessage() : e.getMessage()), LogLevel.ERROR);
            return false;
        } catch (Exception e) {
            cb.onLog("  ✗ Local Runtime Reflection Link Error: " + e.getMessage(), LogLevel.ERROR);
            return false;
        }
    }

    private void flushLogsToCallback(java.io.StringWriter out, java.io.StringWriter err, BuildCallback cb) {
        String outStr = out.toString();
        String errStr = err.toString();
        if (!outStr.isBlank()) cb.onLog(outStr, LogLevel.INFO);
        if (!errStr.isBlank()) cb.onLog(errStr, LogLevel.ERROR);
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
        
        Map<String, String> env = pb.environment();
        String nativeLibDir = context.getApplicationInfo().nativeLibraryDir;
        
        String systemLdPath = env.get("LD_LIBRARY_PATH");
        if (systemLdPath != null && !systemLdPath.isBlank()) {
            env.put("LD_LIBRARY_PATH", nativeLibDir + ":" + systemLdPath);
        } else {
            env.put("LD_LIBRARY_PATH", nativeLibDir + ":/system/lib64:/system/lib");
        }

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
