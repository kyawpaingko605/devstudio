package pro.devstudio.mobile;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
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

import java.io.BufferedInputStream;
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

    private static final String[] ACCENTS = {
            "#CBA6F7", "#89B4FA", "#A6E3A1", "#F38BA8", "#F9E2AF", "#94E2D5"
    };

    private final ActivityResultLauncher<Intent> folderPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri treeUri = result.getData().getData();
                    if (treeUri != null) {
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

        // ✅ build-tools ကို အရင်ဆုံး စစ်ဆေးပြီး extract လုပ်ပါ
        checkAndExtractTools();

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

        binding.btnNewProject.setOnClickListener(v -> showNewProjectDialog());
        binding.btnOpenProject.setOnClickListener(v -> openSystemFolderPicker());
        binding.fabNewProject.setOnClickListener(v -> showNewProjectDialog());

        binding.toolbar.inflateMenu(R.menu.menu_main);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    // ✅ Build Tools ဖြည်ချခြင်း (aapt2 ကို သေချာစစ်ပါ)
    private void checkAndExtractTools() {
        File internalToolsDir = new File(getFilesDir(), "build-tools");
        File aapt2File = new File(internalToolsDir, "aapt2");
        
        // ✅ aapt2 ရှိပြီးသားဆိုရင် ဘာမှမလုပ်ပါ
        if (aapt2File.exists() && aapt2File.length() > 0) {
            Log.i("DevStudio", "✅ aapt2 already exists: " + aapt2File.getAbsolutePath());
            return;
        }
        
        // ✅ aapt2 မရှိရင် extract လုပ်ပါ
        if (!internalToolsDir.exists()) {
            internalToolsDir.mkdirs();
        }

        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("System Setup");
        progressDialog.setMessage("Extracting build tools...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.setMax(100);
        progressDialog.show();

        new Thread(() -> {
            boolean success = false;
            try {
                // ✅ build-tools.zip ကို extract လုပ်ပါ
                try (InputStream is = getAssets().open("build-tools/build-tools.zip");
                     ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
                    
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];
                    int totalFiles = 0;
                    int extractedFiles = 0;

                    // Total files ကိုရေတွက်ပါ
                    try (InputStream countIs = getAssets().open("build-tools/build-tools.zip");
                         ZipInputStream countZis = new ZipInputStream(countIs)) {
                        while (countZis.getNextEntry() != null) {
                            if (!countZis.getNextEntry().isDirectory()) {
                                totalFiles++;
                            }
                            countZis.closeEntry();
                        }
                    }
                    if (totalFiles == 0) totalFiles = 1;

                    while ((entry = zis.getNextEntry()) != null) {
                        File outputFile = new File(internalToolsDir, entry.getName());
                        
                        if (entry.isDirectory()) {
                            outputFile.mkdirs();
                        } else {
                            File parent = outputFile.getParentFile();
                            if (parent != null && !parent.exists()) parent.mkdirs();

                            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                                int len;
                                while ((len = zis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            
                            // ✅ aapt2 အတွက် permission ပေးပါ
                            if (entry.getName().equals("aapt2")) {
                                outputFile.setExecutable(true, false);
                                outputFile.setReadable(true, false);
                                outputFile.setWritable(true, false);
                                Log.i("DevStudio", "✅ aapt2 extracted: " + outputFile.getAbsolutePath());
                            }
                            
                            if (!entry.getName().endsWith(".jar") && !entry.getName().endsWith(".keystore")) {
                                outputFile.setExecutable(true, false);
                            }
                            
                            extractedFiles++;
                            final int percent = (extractedFiles * 100) / totalFiles;
                            final String fileName = entry.getName();
                            runOnUiThread(() -> {
                                progressDialog.setProgress(Math.min(percent, 100));
                                progressDialog.setMessage("Extracting: " + fileName);
                            });
                        }
                        zis.closeEntry();
                    }
                    success = true;
                }
            } catch (IOException e) {
                Log.e("DevStudio", "Extraction error: " + e.getMessage());
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (progressDialog.isShowing()) progressDialog.dismiss();
                
                // ✅ aapt2 ရှိမရှိ ပြန်စစ်ပါ
                File checkAapt2 = new File(internalToolsDir, "aapt2");
                if (checkAapt2.exists() && checkAapt2.length() > 0) {
                    Toast.makeText(MainActivity.this, "✓ System tools configured successfully!", Toast.LENGTH_SHORT).show();
                    Log.i("DevStudio", "✅ aapt2 confirmed at: " + checkAapt2.getAbsolutePath());
                } else {
                    Toast.makeText(MainActivity.this, "✗ aapt2 not found! Please reinstall.", Toast.LENGTH_LONG).show();
                    Log.e("DevStudio", "❌ aapt2 NOT found after extraction!");
                }
            });
        }).start();
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

    // ── External Folder Picker ──

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
