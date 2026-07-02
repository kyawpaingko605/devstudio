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

public class BuildManager {

    public interface BuildCallback {
        void onProgress(String message, int percent);
        void onLog(String line, LogLevel level);
        void onSuccess(File zipFile, File apkFile);
        void onError(String message);
    }

    public enum LogLevel { INFO, SUCCESS, WARNING, ERROR }

    private final Context context;
    private final GeminiClient geminiClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BuildManager(Context context) {
        this.context = context.getApplicationContext();
        this.geminiClient = new GeminiClient(this.context);
    }

    // ── INTERACTIVE PROJECT GENERATION ────────────────────────────────────────────
    public File createNewProjectStructure(String projectName, String packageName) throws Exception {
        File projectsDir = new File(context.getFilesDir(), "projects");
        if (!projectsDir.exists()) projectsDir.mkdirs();

        File projectRoot = new File(projectsDir, projectName);
        String packagePath = packageName.replace(".", "/");
        
        File javaDir = new File(projectRoot, "app/src/main/java/" + packagePath);
        File resLayoutDir = new File(projectRoot, "app/src/main/res/layout");
        File resValuesDir = new File(projectRoot, "app/src/main/res/values");
        
        javaDir.mkdirs(); resLayoutDir.mkdirs(); resValuesDir.mkdirs();

        // 1. AndroidManifest
        String manifest = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + packageName + "\">\n" +
                "    <application android:label=\"@string/app_name\" android:theme=\"@android:style/Theme.Material.Light.DarkActionBar\">\n" +
                "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>";
        writeProjectFile(new File(projectRoot, "app/src/main/AndroidManifest.xml"), manifest);

        // 2. MainActivity (Interactive)
        String javaCode = "package " + packageName + ";\n\n" +
                "import android.app.Activity;\n" +
                "import android.os.Bundle;\n" +
                "import android.widget.Button;\n" +
                "import android.widget.TextView;\n\n" +
                "public class MainActivity extends Activity {\n" +
                "    @Override\n" +
                "    protected void onCreate(Bundle savedInstanceState) {\n" +
                "        super.onCreate(savedInstanceState);\n" +
                "        setContentView(getResources().getIdentifier(\"activity_main\", \"layout\", getPackageName()));\n\n" +
                "        TextView tv = findViewById(getResources().getIdentifier(\"tv_title\", \"id\", getPackageName()));\n" +
                "        Button btn = findViewById(getResources().getIdentifier(\"btn_click\", \"id\", getPackageName()));\n\n" +
                "        btn.setOnClickListener(v -> tv.setText(\"DevStudio APK Works!\"));\n" +
                "    }\n" +
                "}";
        writeProjectFile(new File(javaDir, "MainActivity.java"), javaCode);

        // 3. Layout (Button + Text)
        String layout = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"\n" +
                "    android:orientation=\"vertical\" android:gravity=\"center\" android:padding=\"20dp\">\n" +
                "    <TextView android:id=\"@+id/tv_title\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Welcome to DevStudio\" android:textSize=\"20sp\" android:layout_marginBottom=\"20dp\"/>\n" +
                "    <Button android:id=\"@+id/btn_click\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\"\n" +
                "        android:text=\"Click Me\" />\n" +
                "</LinearLayout>";
        writeProjectFile(new File(resLayoutDir, "activity_main.xml"), layout);

        // 4. Strings
        String stringsXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n    <string name=\"app_name\">" + projectName + "</string>\n</resources>";
        writeProjectFile(new File(resValuesDir, "strings.xml"), stringsXml);

        return projectRoot;
    }

    private void writeProjectFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) { writer.write(content); }
    }

    // [Build Logic Section] 
    // အပေါ်တွင် ပေးထားခဲ့သော buildInternal၊ prepareLocalTools၊ runJarMainInApp၊ 
    // runProcess နှင့် helper methods အားလုံးကို ဤနေရာတွင် ဆက်၍ထည့်သွင်းပါ
    
    // ... (Your previously validated Build Pipeline code here) ...
}
