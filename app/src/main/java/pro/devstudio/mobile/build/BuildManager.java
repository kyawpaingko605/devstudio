package pro.devstudio.mobile.build;

import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
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

public class BuildManager {

    public static interface BuildCallback {
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

    public File createNewProjectStructure(String projectName, String packageName) throws Exception {
        File projectsDir = new File(context.getFilesDir(), "projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();

        File projectRoot = new File(projectsDir, projectName);
        String packagePath = packageName.replace(".", "/");
        
        File javaDir = new File(projectRoot, "app/src/main/java/" + packagePath);
        File resLayoutDir = new File(projectRoot, "app/src/main/res/layout");
        File resValuesDir = new File(projectRoot, "app/src/main/res/values");
        File resDrawableDir = new File(projectRoot, "app/src/main/res/drawable");
        
        javaDir.mkdirs();
        resLayoutDir.mkdirs();
        resValuesDir.mkdirs();
        resDrawableDir.mkdirs();

        String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + packageName + "\">\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@android:style/Theme.Holo.Light.DarkActionBar\">\n" +
                "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>";
        writeProjectFile(new File(projectRoot, "app/src/main/AndroidManifest.xml"), manifest);

        String javaCode = "package " + packageName + ";\n\n" +
                "import android.app.Activity;\n" +
                "import android.os.Bundle;\n" +
                "import android.widget.Button;\n" +
                "import android.widget.TextView;\n\n" +
                "public class MainActivity extends Activity {\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        int layoutId = getResources().getIdentifier(\"activity_main\", \"layout\", getPackageName());\n" +
                "        setContentView(layoutId);\n\n" +
                "        int tvId = getResources().getIdentifier(\"tv_title\", \"id\", getPackageName());\n" +
                "        int btnId = getResources().getIdentifier(\"btn_click\", \"id\", getPackageName());\n" +
                "        final TextView tv = findViewById(tvId);\n" +
                "        Button btn = findViewById(btnId);\n\n" +
                "        if (btn != null && tv != null) {\n" +
                "            btn.setOnClickListener(new android.view.View.OnClickListener() {\n" +
                "                @Override\n" +
                "                public void onClick(android.view.View v) {\n" +
                "                    tv.setText(\"DevStudio APK Works Successfully!\");\n" +
                "                }\n" +
                "            });\n" +
                "        }\n" +
                "    }\n" +
                "}";
        writeProjectFile(new File(javaDir, "MainActivity.java"), javaCode);

        String layout = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\"\n" +
                "    android:layout_height=\"match_parent\"\n" +
                "    android:orientation=\"vertical\"\n" +
                "    android:gravity=\"center\"\n" +
                "    android:background=\"#FFFFFF\"\n" +
                "    android:padding=\"20dp\">\n" +
                "\n" +
                "    <TextView\n" +
                "        android:id=\"@+id/tv_title\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Welcome to DevStudio Local Build!\"\n" +
                "        android:textSize=\"20sp\"\n" +
                "        android:textColor=\"#000000\"\n" +
                "        android:layout_marginBottom=\"24dp\"\n" +
                "        android:textStyle=\"bold\"/>\n" +
                "\n" +
                "    <Button\n" +
                "        android:id=\"@+id/btn_click\"\n" +
                "        android:layout_width=\"wrap_content\"\n" +
                "        android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Test Interaction\" />\n" +
                "</LinearLayout>";
        writeProjectFile(new File(resLayoutDir, "activity_main.xml"), layout);

        String stringsXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">" + projectName + "</string>\n" +
                "</resources>";
        writeProjectFile(new File(resValuesDir, "strings.xml"), stringsXml);

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

    public void build(Project project, File projectDir, BuildCallback cb) {
        if (project == null || projectDir == null || cb == null) {
            if (cb != null) cb.onError("Build execution aborted: Null arguments injected.");
            return;
        }
        buildInternal(project, projectDir, cb, false);
    }

    private void prepareLocalTools(BuildCallback cb) throws IOException {
        File jarToolsDir = new File(context.getFilesDir(), "build-tools");
        if (!jarToolsDir.exists()) {
            cb.onLog("  ✗ Critical: Build tools not found! Please restart the app.", LogLevel.ERROR);
            throw new IOException("Build tools directory missing.");
        }
        cb.onLog("  ✓ Internal build-tools verified successfully.", LogLevel.SUCCESS);
    }

    // ✅ No-root အတွက် aapt2 Process (cache ထဲ copy လုပ်ပြီး permission ပေးတယ်)
    private int runAapt2Binary(String[] args, BuildCallback cb) {
        try {
            File toolsDir = new File(context.getFilesDir(), "build-tools");
            File aapt2Binary = new File(toolsDir, "aapt2");

            if (!aapt2Binary.exists()) {
                cb.onLog("  ✗ Critical: aapt2 missing in internal build-tools!", LogLevel.ERROR);
                return -1;
            }

            // ✅ aapt2 ကို cache ထဲ ကူးပြီး permission ပေးပါ (No-root အတွက်)
            File aapt2Cache = new File(context.getCacheDir(), "aapt2");
            if (!aapt2Cache.exists() || aapt2Cache.length() != aapt2Binary.length()) {
                cb.onLog("  ℹ Copying aapt2 to cache for permission fix...", LogLevel.INFO);
                try (InputStream in = new FileInputStream(aapt2Binary);
                     OutputStream out = new FileOutputStream(aapt2Cache)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                aapt2Cache.setExecutable(true, false);
                cb.onLog("  ✓ aapt2 copied to cache with execute permission.", LogLevel.SUCCESS);
            }

            // ✅ cache ထဲက aapt2 ကိုသုံးပါ
            File aapt2ToUse = aapt2Cache.exists() ? aapt2Cache : aapt2Binary;

            List<String> command = new ArrayList<>();
            command.add(aapt2ToUse.getAbsolutePath());
            for (String arg : args) {
                command.add(arg);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Map<String, String> env = pb.environment();
            env.put("LD_LIBRARY_PATH", toolsDir.getAbsolutePath() + ":/system/lib64:/system/lib");

            cb.onLog("  ℹ Running AAPT2...", LogLevel.INFO);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    cb.onLog("  [AAPT2] " + line, LogLevel.INFO);
                }
            }

            int exitCode = process.waitFor();
            cb.onLog("  ℹ AAPT2 exit code: " + exitCode, LogLevel.INFO);
            return exitCode;

        } catch (IOException e) {
            cb.onLog("  ✗ AAPT2 IOException: " + e.getMessage(), LogLevel.ERROR);
            return -1;
        } catch (Exception e) {
            cb.onLog("  ✗ AAPT2 Exec Engine Exception: " + e.getMessage(), LogLevel.ERROR);
            return -1;
        }
    }

    // ✅ D8 + R8 Fallback System
    private boolean convertToDexWithFallback(File toolsDir, File androidJar, String intermediatesDex, 
                                               List<String> classFiles, BuildCallback cb) {
        
        String[] jarFiles = {"d8.jar", "r8.jar"};
        String[] classNames = {
            "com.android.tools.r8.D8",
            "com.android.tools.r8.R8"
        };
        
        for (String jarFile : jarFiles) {
            File jar = new File(toolsDir, jarFile);
            if (!jar.exists()) {
                cb.onLog("  ⚠ " + jarFile + " not found, trying next...", LogLevel.WARNING);
                continue;
            }
            
            cb.onLog("  ℹ Trying " + jarFile + "...", LogLevel.INFO);
            
            for (String className : classNames) {
                try {
                    List<String> d8Args = new ArrayList<>();
                    d8Args.add("--lib");
                    d8Args.add(androidJar.getAbsolutePath());
                    d8Args.add("--output");
                    d8Args.add(intermediatesDex);
                    d8Args.add("--min-api");
                    d8Args.add("21");
                    d8Args.addAll(classFiles);
                    
                    boolean success = runDexTool(jar, className, d8Args, cb);
                    if (success) {
                        cb.onLog("  ✓ DEX conversion successful with " + jarFile + " (" + className + ")", LogLevel.SUCCESS);
                        return true;
                    }
                } catch (Exception e) {
                    cb.onLog("  ⚠ " + className + " failed: " + e.getMessage(), LogLevel.WARNING);
                }
            }
        }
        
        cb.onLog("  ✗ All DEX tools failed!", LogLevel.ERROR);
        return false;
    }

    private boolean runDexTool(File jarFile, String mainClassName, List<String> args, BuildCallback cb) {
        try {
            File optimizedDir = new File(context.getCacheDir(), "dex-opt");
            if (!optimizedDir.exists()) optimizedDir.mkdirs();

            DexClassLoader classLoader = new DexClassLoader(
                jarFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                jarFile.getParent(),
                context.getClassLoader()
            );

            Class<?> mainClass = classLoader.loadClass(mainClassName);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.PrintStream customPS = new java.io.PrintStream(baos);
            java.io.PrintStream originalOut = System.out;
            java.io.PrintStream originalErr = System.err;
            
            System.setOut(customPS);
            System.setErr(customPS);
            
            try {
                mainMethod.invoke(null, (Object) args.toArray(new String[0]));
                String logOutput = baos.toString();
                if (!logOutput.isBlank()) {
                    cb.onLog(logOutput, LogLevel.INFO);
                }
                return true;
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable cause = e.getCause();
                cb.onLog("  ✗ " + mainClassName + " error: " + (cause != null ? cause.getMessage() : e.getMessage()), LogLevel.ERROR);
                return false;
            } finally {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
            
        } catch (Exception e) {
            cb.onLog("  ✗ " + mainClassName + " reflection error: " + e.getMessage(), LogLevel.ERROR);
            return false;
        }
    }

    private void buildInternal(Project project, File projectDir, BuildCallback cb, boolean isRetry) {
        executor.execute(() -> {
            try {
                if (isRetry) {
                    cb.onLog("► AI Auto-Fix applied. Re-attempting compilation…", LogLevel.INFO);
                }

                cb.onProgress("Preparing build tools…", 10);
                cb.onLog("► Verifying internal build tools…", LogLevel.INFO);
                prepareLocalTools(cb);

                File toolsDir      = new File(context.getFilesDir(), "build-tools");
                File ecjJar        = new File(toolsDir, "ecj.jar");
                File androidJar    = new File(toolsDir, "android.jar");
                File apksignerJar  = new File(toolsDir, "apksigner.jar");
                File debugKeystore = new File(toolsDir, "debug.keystore");
                
                cb.onLog("  ✓ Tools are ready in secure storage.", LogLevel.SUCCESS);

                cb.onProgress("Validating XML files…", 20);
                cb.onLog("► Checking XML layouts…", LogLevel.INFO);
                List<String> xmlErrors = validateXmlFiles(projectDir);
                if (!xmlErrors.isEmpty()) {
                    for (String err : xmlErrors) cb.onLog("  ✗ " + err, LogLevel.ERROR);
                    cb.onError("XML Validation Failed");
                    return;
                }
                cb.onLog("  ✓ All XML files valid.", LogLevel.SUCCESS);

                String manifestPath = new File(projectDir, "app/src/main/AndroidManifest.xml").getAbsolutePath();
                String resPath      = new File(projectDir, "app/src/main/res").getAbsolutePath();
                String srcPath      = new File(projectDir, "app/src/main/java").getAbsolutePath();
                
                String buildDir         = new File(projectDir, "app/build").getAbsolutePath();
                String genPath          = buildDir + "/generated";
                String objPath          = buildDir + "/obj";
                String intermediatesRes = buildDir + "/intermediates/res";
                String binDir           = buildDir + "/outputs/apk/release";

                deleteDir(new File(buildDir));
                new File(genPath).mkdirs();
                new File(objPath).mkdirs();
                new File(intermediatesRes).mkdirs();
                new File(binDir).mkdirs();

                // ── Step 1: AAPT2 Compile ────────────────────────────────────
                cb.onProgress("Compiling resources…", 40);
                cb.onLog("► [1/5] Compiling resources via AAPT2 Process...", LogLevel.INFO);

                String[] compileArgs = {
                    "compile",
                    "--dir", resPath,
                    "-o", intermediatesRes + "/resources.zip"
                };
                
                int compileResult = runAapt2Binary(compileArgs, cb);
                if (compileResult != 0) { 
                    cb.onError("AAPT2 Compile Process failed with exit code: " + compileResult); 
                    return; 
                }
                cb.onLog("  ✓ Resources compiled safely.", LogLevel.SUCCESS);

                // ── Step 2: AAPT2 Link ──────────────────────────────────────
                cb.onLog("► [2/5] Linking resources and generating R.java…", LogLevel.INFO);
                String unalignedApk = binDir + "/app-unaligned.apk";
                
                String[] linkArgs = {
                    "link", 
                    "-I", androidJar.getAbsolutePath(), 
                    "--manifest", manifestPath, 
                    "-o", unalignedApk, 
                    "--java", genPath, 
                    intermediatesRes + "/resources.zip"
                };
                
                int linkResult = runAapt2Binary(linkArgs, cb);
                if (linkResult != 0) { 
                    cb.onError("AAPT2 Link Process failed with exit code: " + linkResult); 
                    return; 
                }
                cb.onLog("  ✓ Resources linked safely.", LogLevel.SUCCESS);

                // ── Step 3: Java Compilation ─────────────────────────────────
                cb.onProgress("Compiling Java code…", 60);
                cb.onLog("► [3/5] Compiling Java source codes with In-App ECJ Tool…", LogLevel.INFO);
                
                List<String> ecjArgs = new ArrayList<>();
                ecjArgs.add("-d"); ecjArgs.add(objPath);
                ecjArgs.add("-cp"); ecjArgs.add(androidJar.getAbsolutePath() + File.pathSeparator + genPath);
                ecjArgs.add("-source"); ecjArgs.add("1.7");
                ecjArgs.add("-target"); ecjArgs.add("1.7");
                ecjArgs.add("-proc:none");
                ecjArgs.add("-nowarn");

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

                // ── Step 4: DEX Conversion ──────────────────────────────────
                cb.onProgress("Converting to DEX…", 80);
                cb.onLog("► [4/5] Converting class files to DEX via D8/R8…", LogLevel.INFO);
                String intermediatesDex = buildDir + "/intermediates/dex";
                new File(intermediatesDex).mkdirs();

                List<String> classFiles = new ArrayList<>();
                addAllFiles(new File(objPath), ".class", classFiles);

                if (classFiles.isEmpty()) {
                    cb.onLog("  ✗ No class files found to convert!", LogLevel.ERROR);
                    cb.onError("No class files to convert");
                    return;
                }

                cb.onLog("  ℹ Converting " + classFiles.size() + " class files to DEX", LogLevel.INFO);

                boolean dexSuccess = convertToDexWithFallback(toolsDir, androidJar, intermediatesDex, classFiles, cb);
                if (!dexSuccess) { 
                    cb.onError("DEX Conversion Failed"); 
                    return; 
                }

                File dexFile = new File(intermediatesDex, "classes.dex");
                if (!dexFile.exists() || dexFile.length() == 0) {
                    cb.onLog("  ✗ classes.dex not generated or empty!", LogLevel.ERROR);
                    cb.onError("DEX generation failed");
                    return;
                }
                cb.onLog("  ✓ classes.dex generated (" + dexFile.length() + " bytes)", LogLevel.SUCCESS);

                // ── Step 5: Inject DEX into APK ──────────────────────────────
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

                // ── Step 6: Sign APK ──────────────────────────────────────────
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
                    cb.onLog("  ⚠ Signing tools not found, using unsigned APK.", LogLevel.WARNING);
                    unalignedApkFile.renameTo(releaseApk);
                }

                File cacheDir = new File(context.getCacheDir(), "builds");
                cacheDir.mkdirs();
                File zipOut = new File(cacheDir, project.dirName() + ".zip");
                FileUtils.zipDirectory(projectDir, zipOut);

                cb.onProgress("Done.", 100);
                cb.onLog("━━━━━━━━━━━━━━━━━━━━━━━━", LogLevel.INFO);
                cb.onLog("✓ Build complete — Standalone App Ready!", LogLevel.SUCCESS);
                cb.onLog("✓ APK: " + releaseApk.getAbsolutePath(), LogLevel.SUCCESS);
                cb.onSuccess(zipOut, releaseApk);

            } catch (Exception e) {
                cb.onLog("✗ Build error: " + e.getMessage(), LogLevel.ERROR);
                cb.onError(e.getMessage());
            }
        });
    }

    private boolean runJarMainInApp(File jarFile, String mainClassName, String[] args, BuildCallback cb) {
        try {
            File optimizedDir = new File(context.getCacheDir(), "dex-opt");
            if (!optimizedDir.exists()) optimizedDir.mkdirs();

            DexClassLoader classLoader = new DexClassLoader(
                jarFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                jarFile.getParent(),
                context.getClassLoader()
            );

            java.io.StringWriter outWriter = new java.io.StringWriter();
            java.io.StringWriter errWriter = new java.io.StringWriter();
            java.io.PrintWriter outPrintWriter = new java.io.PrintWriter(outWriter);
            java.io.PrintWriter errPrintWriter = new java.io.PrintWriter(errWriter);

            if (mainClassName.contains("org.eclipse.jdt")) {
                Class<?> batchCompilerClass = classLoader.loadClass("org.eclipse.jdt.core.compiler.batch.BatchCompiler");
                Class<?> progressClass = classLoader.loadClass("org.eclipse.jdt.core.compiler.CompilationProgress");
                
                Method compileMethod = batchCompilerClass.getMethod("compile", 
                    String[].class, java.io.PrintWriter.class, java.io.PrintWriter.class, progressClass);
                
                boolean success = (boolean) compileMethod.invoke(null, args, outPrintWriter, errPrintWriter, null);
                flushLogsToCallback(outWriter, errWriter, cb);
                return success;

            } else if (mainClassName.contains("com.android.apksigner.ApkSignerTool")) {
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
                    String logOutput = baos.toString();
                    if (!logOutput.isBlank()) cb.onLog(logOutput, LogLevel.INFO);
                    return true;
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }

            } else {
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
