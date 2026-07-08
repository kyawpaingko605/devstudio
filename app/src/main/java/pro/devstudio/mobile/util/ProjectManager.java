package pro.devstudio.mobile.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import pro.devstudio.mobile.model.Project;

/**
 * Saves/loads the project list and creates project directory scaffolding.
 */
public class ProjectManager {

    private static final String PREF_NAME    = "devstudio_projects";
    private static final String KEY_PROJECTS = "projects_json";
    private static final Gson   GSON         = new Gson();

    private final Context context;

    public ProjectManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // ── Project list ─────────────────────────────────────────────────────────

    public List<Project> loadProjects() {
        String json = prefs().getString(KEY_PROJECTS, "[]");
        Type type = new TypeToken<List<Project>>() {}.getType();
        List<Project> list = GSON.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void saveProjects(List<Project> projects) {
        prefs().edit().putString(KEY_PROJECTS, GSON.toJson(projects)).apply();
    }

    public void addProject(Project p, List<Project> list) {
        list.add(0, p);
        saveProjects(list);
    }

    public void deleteProject(Project p, List<Project> list) {
        list.remove(p);
        saveProjects(list);
        FileUtils.deleteRecursive(projectDir(p));
    }

    public void touchProject(Project p, List<Project> list) {
        p.lastModified = System.currentTimeMillis();
        saveProjects(list);
    }

    // ── File system ──────────────────────────────────────────────────────────

    public File projectDir(Project p) {
        return new File(context.getFilesDir(), "projects/" + p.dirName());
    }

    /**
     * Creates the project directory and starter files based on the chosen template.
     */
    public void scaffoldProject(Project p) throws IOException {
        File root = projectDir(p);
        File javaDir = new File(root, "app/src/main/java/" + p.packageName.replace('.', '/'));
        File resLayout = new File(root, "app/src/main/res/layout");
        File resValues = new File(root, "app/src/main/res/values");
        javaDir.mkdirs();
        resLayout.mkdirs();
        resValues.mkdirs();

        // Manifest (Holo theme)
        FileUtils.writeFile(new File(root, "app/src/main/AndroidManifest.xml"),
                manifestXml(p));

        // Main activity (Activity, not AppCompatActivity)
        FileUtils.writeFile(new File(javaDir, "MainActivity.java"),
                mainActivityJava(p));

        // Layout (with ids for button and textview)
        FileUtils.writeFile(new File(resLayout, "activity_main.xml"),
                templateLayout(p.template));

        // strings.xml
        FileUtils.writeFile(new File(resValues, "strings.xml"),
                "<resources>\n    <string name=\"app_name\">" + p.name + "</string>\n</resources>\n");

        // app/build.gradle
        FileUtils.writeFile(new File(root, "app/build.gradle"), appGradle(p));

        // Root build.gradle
        FileUtils.writeFile(new File(root, "build.gradle"),
                "plugins {\n    id 'com.android.application' version '8.2.2' apply false\n}\n");

        // settings.gradle
        FileUtils.writeFile(new File(root, "settings.gradle"),
                "rootProject.name = \"" + p.name + "\"\ninclude ':app'\n");
    }

    // ── Templates ────────────────────────────────────────────────────────────

    // ✅ Manifest - Holo theme
    private String manifestXml(Project p) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"" + p.packageName + "\">\n" +
               "    <application\n" +
               "        android:allowBackup=\"true\"\n" +
               "        android:label=\"" + p.name + "\"\n" +
               "        android:theme=\"@android:style/Theme.Holo.Light.DarkActionBar\">\n" +
               "        <activity\n" +
               "            android:name=\".MainActivity\"\n" +
               "            android:exported=\"true\">\n" +
               "            <intent-filter>\n" +
               "                <action android:name=\"android.intent.action.MAIN\" />\n" +
               "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
               "            </intent-filter>\n" +
               "        </activity>\n" +
               "    </application>\n" +
               "</manifest>\n";
    }

    // ✅ MainActivity - uses Activity, getIdentifier, no Lambda
    private String mainActivityJava(Project p) {
        return "package " + p.packageName + ";\n\n" +
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
               "}\n";
    }

    // ✅ Layout with ids
    private String templateLayout(String template) {
        return switch (template) {
            case "login" -> loginLayout();
            case "basic" -> basicLayout();
            case "list"  -> listLayout();
            default      -> emptyLayout();
        };
    }

    private String emptyLayout() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
               "    android:layout_width=\"match_parent\"\n" +
               "    android:layout_height=\"match_parent\"\n" +
               "    android:orientation=\"vertical\"\n" +
               "    android:gravity=\"center\"\n" +
               "    android:background=\"#FFFFFF\"\n" +
               "    android:padding=\"20dp\">\n\n" +
               "    <TextView\n" +
               "        android:id=\"@+id/tv_title\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"Welcome to DevStudio Local Build!\"\n" +
               "        android:textSize=\"20sp\"\n" +
               "        android:textColor=\"#000000\"\n" +
               "        android:layout_marginBottom=\"24dp\"\n" +
               "        android:textStyle=\"bold\"/>\n\n" +
               "    <Button\n" +
               "        android:id=\"@+id/btn_click\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"Test Interaction\" />\n\n" +
               "</LinearLayout>\n";
    }

    private String loginLayout() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
               "    android:layout_width=\"match_parent\"\n" +
               "    android:layout_height=\"match_parent\"\n" +
               "    android:gravity=\"center\"\n" +
               "    android:orientation=\"vertical\"\n" +
               "    android:padding=\"24dp\"\n" +
               "    android:background=\"#FFFFFF\">\n\n" +
               "    <TextView\n" +
               "        android:id=\"@+id/tv_title\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"Sign In\"\n" +
               "        android:textSize=\"32sp\"\n" +
               "        android:textStyle=\"bold\"\n" +
               "        android:layout_marginBottom=\"32dp\" />\n\n" +
               "    <EditText\n" +
               "        android:id=\"@+id/et_email\"\n" +
               "        android:layout_width=\"match_parent\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:hint=\"Email\"\n" +
               "        android:inputType=\"textEmailAddress\"\n" +
               "        android:layout_marginBottom=\"12dp\" />\n\n" +
               "    <EditText\n" +
               "        android:id=\"@+id/et_password\"\n" +
               "        android:layout_width=\"match_parent\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:hint=\"Password\"\n" +
               "        android:inputType=\"textPassword\"\n" +
               "        android:layout_marginBottom=\"24dp\" />\n\n" +
               "    <Button\n" +
               "        android:id=\"@+id/btn_login\"\n" +
               "        android:layout_width=\"match_parent\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"Sign In\" />\n\n" +
               "</LinearLayout>\n";
    }

    private String basicLayout() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<androidx.coordinatorlayout.widget.CoordinatorLayout\n" +
               "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
               "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
               "    android:layout_width=\"match_parent\"\n" +
               "    android:layout_height=\"match_parent\"\n" +
               "    android:background=\"#FFFFFF\">\n\n" +
               "    <com.google.android.material.appbar.AppBarLayout\n" +
               "        android:layout_width=\"match_parent\"\n" +
               "        android:layout_height=\"wrap_content\">\n" +
               "        <com.google.android.material.appbar.MaterialToolbar\n" +
               "            android:layout_width=\"match_parent\"\n" +
               "            android:layout_height=\"?attr/actionBarSize\"\n" +
               "            app:title=\"My App\" />\n" +
               "    </com.google.android.material.appbar.AppBarLayout>\n\n" +
               "    <TextView\n" +
               "        android:id=\"@+id/tv_title\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"Hello!\"\n" +
               "        android:textSize=\"24sp\"\n" +
               "        app:layout_behavior=\"@string/appbar_scrolling_view_behavior\" />\n\n" +
               "    <com.google.android.material.floatingactionbutton.FloatingActionButton\n" +
               "        android:id=\"@+id/fab\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:layout_gravity=\"bottom|end\"\n" +
               "        android:layout_margin=\"16dp\"\n" +
               "        app:srcCompat=\"@android:drawable/ic_input_add\" />\n\n" +
               "</androidx.coordinatorlayout.widget.CoordinatorLayout>\n";
    }

    private String listLayout() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
               "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
               "    android:layout_width=\"match_parent\"\n" +
               "    android:layout_height=\"match_parent\"\n" +
               "    android:orientation=\"vertical\"\n" +
               "    android:background=\"#FFFFFF\">\n\n" +
               "    <TextView\n" +
               "        android:id=\"@+id/tv_title\"\n" +
               "        android:layout_width=\"wrap_content\"\n" +
               "        android:layout_height=\"wrap_content\"\n" +
               "        android:text=\"My List\"\n" +
               "        android:textSize=\"24sp\"\n" +
               "        android:textStyle=\"bold\"\n" +
               "        android:layout_margin=\"16dp\" />\n\n" +
               "    <androidx.recyclerview.widget.RecyclerView\n" +
               "        android:id=\"@+id/recyclerView\"\n" +
               "        android:layout_width=\"match_parent\"\n" +
               "        android:layout_height=\"match_parent\"\n" +
               "        android:padding=\"8dp\" />\n\n" +
               "</LinearLayout>\n";
    }

    private String appGradle(Project p) {
        return "plugins {\n    id 'com.android.application'\n}\n\n" +
               "android {\n" +
               "    namespace '" + p.packageName + "'\n" +
               "    compileSdk 34\n\n" +
               "    defaultConfig {\n" +
               "        applicationId \"" + p.packageName + "\"\n" +
               "        minSdk 26\n" +
               "        targetSdk 34\n" +
               "        versionCode 1\n" +
               "        versionName \"1.0\"\n" +
               "    }\n" +
               "    compileOptions {\n" +
               "        sourceCompatibility JavaVersion.VERSION_17\n" +
               "        targetCompatibility JavaVersion.VERSION_17\n" +
               "    }\n" +
               "}\n\n" +
               "dependencies {\n" +
               "    implementation 'androidx.appcompat:appcompat:1.6.1'\n" +
               "    implementation 'com.google.android.material:material:1.11.0'\n" +
               "}\n";
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
            }
