package pro.devstudio.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import pro.devstudio.mobile.adapter.ProjectAdapter;
import pro.devstudio.mobile.databinding.ActivityMainBinding;
import pro.devstudio.mobile.databinding.DialogNewProjectBinding;
import pro.devstudio.mobile.model.Project;
import pro.devstudio.mobile.util.ProjectManager;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ProjectAdapter      adapter;
    private ProjectManager      manager;
    private List<Project>       projects;

    // Accent palette for new project cards
    private static final String[] ACCENTS = {
            "#CBA6F7", "#89B4FA", "#A6E3A1", "#F38BA8", "#F9E2AF", "#94E2D5"
    };

    // ── Folder Picker Launcher (Android 11+ သဟဇာတဖြစ်အောင် OPEN_DOCUMENT_TREE သုံးထားသည်) ──
    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
                        // Folder ရွေးချယ်ပြီးနောက် Persistent Permission ရယူခြင်း
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION 
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                        
                        handleOpenExternalProject(treeUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ✅ CodeAssist ပုံစံအတိုင်း- App စပွင့်ချင်း Background Thread ဖြင့် assets zip ကို ဖြည်ချဆောက်ပေးခြင်း
        new Thread(() -> {
            try {
                File internalToolsDir = new File(getFilesDir(), "build-tools");
                // ဖုန်းထဲမှာ build-tools folder မရှိသေးရင် အသစ်ဆောက်ပြီး zip ကို ဖြည်ချပါမည်
                if (!internalToolsDir.exists()) {
                    internalToolsDir.mkdirs();
                    extractAssetsZip("build-tools/build-tools.zip", internalToolsDir);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            var sysBar = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.recyclerView.setPadding(0, 0, 0, sysBar.bottom + 80);
            return insets;
        });

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle("DevStudio");
        binding.toolbar.setSubtitle("Mobile IDE");

        manager  = new ProjectManager(this);
        projects = manager.loadProjects();

        adapter = new ProjectAdapter(projects, new ProjectAdapter.Listener() {
            @Override public void onOpen(Project p) {
                manager.touchProject(p, projects);
                openEditor(p);
            }
            @Override public void onDelete(Project p, int pos) {
                confirmDelete(p, pos);
            }
        });

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerView.setAdapter(adapter);

        updateEmptyState();

        // ── Welcome Screen Buttons Click Listeners ──
        binding.btnNewProject.setOnClickListener(v -> showNewProjectDialog());
        binding.btnOpenProject.setOnClickListener(v -> openSystemFolderPicker());

        // မူရင်း FAB အလုပ်လုပ်ပုံ
        binding.fabNewProject.setOnClickListener(v -> showNewProjectDialog());

        // Toolbar menu
        binding.toolbar.inflateMenu(R.menu.menu_main);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    // ✅ Assets Zip ကို ဖတ်ပြီး စနစ်တကျ Internal Storage ထဲ ဖြည်ချပေးမည့် Method
    private void extractAssetsZip(String zipFilePath, File targetDir) throws IOException {
        try (InputStream is = getAssets().open(zipFilePath);
             ZipInputStream zis = new ZipInputStream(is)) {
            
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outputFile = new File(targetDir, entry.getName());
                
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    
                    // Executable permission သတ်မှတ်ပေးခြင်း (Jar မဟုတ်သော runtime binaries များအတွက်)
                    if (!entry.getName().endsWith(".jar") && !entry.getName().endsWith(".keystore")) {
                        outputFile.setExecutable(true, false);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // ── New project ──────────────────────────────────────────────────────────

    private void showNewProjectDialog() {
        DialogNewProjectBinding d = DialogNewProjectBinding.inflate(getLayoutInflater());

        String[] templates = {"Empty Activity", "Basic Activity", "Login Screen", "List + Detail"};
        String[] keys      = {"empty", "basic", "login", "list"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, templates);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        d.spinnerTemplate.setAdapter(spinAdapter);

        d.etProjectName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String name = s.toString().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                if (!name.isEmpty()) d.etPackageName.setText("com.example." + name);
            }
        });

        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("New Project")
                .setView(d.getRoot())
                .setPositiveButton("Create", (dlg, w) -> {
                    String name = d.etProjectName.getText().toString().trim();
                    String pkg  = d.etPackageName.getText().toString().trim();
                    int    tplI = d.spinnerTemplate.getSelectedItemPosition();

                    if (name.isEmpty()) { toast("Project name is required."); return; }
                    if (pkg.isEmpty())  { toast("Package name is required."); return; }

                    createProject(name, pkg, keys[tplI]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createProject(String name, String pkg, String template) {
        String accent = ACCENTS[new Random().nextInt(ACCENTS.length)];
        Project p = new Project(name, pkg, template, accent);

        try {
            manager.scaffoldProject(p);
            manager.addProject(p, projects);
            adapter.notifyItemInserted(0);
            binding.recyclerView.scrollToPosition(0);
            updateEmptyState();
            toast("Project \"" + name + "\" created!");
            openEditor(p);
        } catch (IOException e) {
            toast("Error creating project: " + e.getMessage());
        }
    }

    // ── External Folder Picker Logic (Android 11+ Storage စနစ်အတိုင်း) ──

    private void openSystemFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        folderPickerLauncher.launch(intent);
    }

    private void handleOpenExternalProject(Uri treeUri) {
        String projectUriString = treeUri.toString();
        toast("Loading External Project...");

        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("project_name", "External Project");
        intent.putExtra("project_dir", projectUriString); 
        intent.putExtra("project_accent", "#3574F0");
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    private void confirmDelete(Project p, int pos) {
        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("Delete \"" + p.name + "\"?")
                .setMessage("This will permanently delete all project files.")
                .setPositiveButton("Delete", (d, w) -> {
                    manager.deleteProject(p, projects);
                    adapter.notifyItemRemoved(pos);
                    updateEmptyState();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Navigation ───────────────────────────────────────────────────────────

    private void openEditor(Project p) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("project_name",    p.name);
        intent.putExtra("project_package", p.packageName);
        intent.putExtra("project_dir",     manager.projectDir(p).getAbsolutePath());
        intent.putExtra("project_accent",  p.accentColor);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void updateEmptyState() {
        boolean empty = projects.isEmpty();
        binding.emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerView.setVisibility(empty ? View.GONE  : View.VISIBLE);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}