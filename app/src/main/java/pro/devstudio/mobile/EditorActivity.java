package pro.devstudio.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

import pro.devstudio.mobile.adapter.ChatAdapter;
import pro.devstudio.mobile.adapter.EditorTabAdapter;
import pro.devstudio.mobile.adapter.FileTreeAdapter;
import pro.devstudio.mobile.ai.GeminiClient;
import pro.devstudio.mobile.build.BuildManager;
import pro.devstudio.mobile.databinding.ActivityEditorBinding;
import pro.devstudio.mobile.model.ChatMessage;
import pro.devstudio.mobile.model.Project;
import pro.devstudio.mobile.util.FileUtils;

public class EditorActivity extends AppCompatActivity {

    private ActivityEditorBinding binding;

    // Editor state
    private File                   projectDir;
    private String                 projectName;
    private String                 accentColor;
    private final Map<String,String> openFiles    = new LinkedHashMap<>(); // abs-path → content
    private final List<String>       openPaths    = new ArrayList<>();    // display names (tab labels)
    private final List<String>       openAbsPaths = new ArrayList<>();    // parallel: absolute paths
    private       String             currentPath;

    // Adapters
    private EditorTabAdapter  tabAdapter;
    private FileTreeAdapter   fileTreeAdapter;
    private ChatAdapter       chatAdapter;
    private final List<ChatMessage> chatMessages = new ArrayList<>();

    // Services
    private BuildManager  buildManager;
    private GeminiClient  geminiClient;

    // Build panel state
    private boolean buildPanelExpanded = false;
    private final SpannableStringBuilder buildLog = new SpannableStringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Unpack intent
        projectName = getIntent().getStringExtra("project_name");
        accentColor = getIntent().getStringExtra("project_accent");
        projectDir  = new File(getIntent().getStringExtra("project_dir"));

        buildManager = new BuildManager(this);
        geminiClient = new GeminiClient(this);

        setupEdgeToEdge();
        setupToolbar();
        setupTabs();
        setupFileTree();
        setupEditor();
        setupAiPanel();
        setupBuildPanel();
        setupFabs();

        // Open first file
        openFirstFile();
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private void setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            var bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.toolbar.setPadding(0, bars.top, 0, 0);
            return insets;
        });
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(projectName);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        binding.toolbar.inflateMenu(R.menu.menu_editor);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_save)         { saveCurrentFile(); toast("Saved."); return true; }
            if (id == R.id.action_new_file)      { showNewFileDialog(); return true; }
            if (id == R.id.action_export_zip)    { exportZipOnly(); return true; }
            if (id == R.id.action_settings)      {
                startActivity(new Intent(this, SettingsActivity.class)); return true; }
            if (id == R.id.action_toggle_tree)   {
                binding.drawerLayout.openDrawer(GravityCompat.START); return true; }
            return false;
        });

        // Accent line under toolbar
        try {
            binding.accentLine.setBackgroundColor(Color.parseColor(accentColor));
        } catch (Exception e) {
            binding.accentLine.setBackgroundColor(Color.parseColor("#CBA6F7"));
        }
    }

    private void setupTabs() {
        tabAdapter = new EditorTabAdapter(openPaths, new EditorTabAdapter.Listener() {
            @Override public void onTabSelected(int pos) {
                switchToFile(openPaths.get(pos));
            }
            @Override public void onTabClosed(int pos) {
                closeTab(pos);
            }
        });
        binding.tabsRecycler.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.tabsRecycler.setAdapter(tabAdapter);
    }

    private void setupFileTree() {
        fileTreeAdapter = new FileTreeAdapter(new FileTreeAdapter.Listener() {
            @Override public void onFileClick(File file) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                openFile(file);
            }
            @Override public void onFileLongClick(File file) {
                showFileOptions(file);
            }
        });
        binding.fileTreeRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.fileTreeRecycler.setAdapter(fileTreeAdapter);
        fileTreeAdapter.setRoot(projectDir);

        binding.tvProjectTitle.setText(projectName);
    }

    private void setupEditor() {
        binding.codeEditor.setEditorLanguage(new JavaLanguage());
        binding.codeEditor.setTextSize(14f);
        binding.codeEditor.setWordwrap(false);

        // Dark color scheme
        EditorColorScheme scheme = binding.codeEditor.getColorScheme();
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND,        0xFF1E1E2E);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND,  0xFF181825);
        scheme.setColor(EditorColorScheme.LINE_NUMBER,             0xFF6C7086);
        scheme.setColor(EditorColorScheme.LINE_DIVIDER,            0xFF313244);
        scheme.setColor(EditorColorScheme.CURRENT_LINE,            0xFF313244);
        scheme.setColor(EditorColorScheme.TEXT_NORMAL,             0xFFCDD6F4);
        scheme.setColor(EditorColorScheme.SELECTION_INSERT,        0xFFCBA6F7);
        scheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND,0x44CBA6F7);
        binding.codeEditor.setColorScheme(scheme);
    }

    private void setupAiPanel() {
        chatAdapter = new ChatAdapter(chatMessages);
        binding.chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecycler.setAdapter(chatAdapter);

        // Welcome message
        addAiMessage("👋 Hi! I'm your AI assistant.\n\n" +
                "I can help you:\n• Fix bugs\n• Explain code\n• Generate snippets\n\n" +
                "Use the shortcuts below or ask me anything!");

        binding.btnSend.setOnClickListener(v -> sendChat());
        binding.etChatInput.setOnEditorActionListener((tv, action, ev) -> {
            sendChat(); return true;
        });

        // Shortcut chips
        binding.chipFixCode.setOnClickListener(v -> {
            String code = binding.codeEditor.getText().toString();
            if (code.isEmpty()) { toast("Editor is empty."); return; }
            addUserMessage("Fix my code (current file)");
            addLoadingMessage();
            geminiClient.fixCode(code, "", (text) -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage(text);
            }), error -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage("❌ " + handleAiError(error));
            }));
        });

        binding.chipExplain.setOnClickListener(v -> {
            String code = binding.codeEditor.getText().toString();
            if (code.isEmpty()) { toast("Editor is empty."); return; }
            addUserMessage("Explain this code");
            addLoadingMessage();
            geminiClient.explainCode(code, text -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage(text);
            }), error -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage("❌ " + handleAiError(error));
            }));
        });

        binding.chipAddComments.setOnClickListener(v -> {
            String code = binding.codeEditor.getText().toString();
            if (code.isEmpty()) { toast("Editor is empty."); return; }
            addUserMessage("Add comments to my code");
            addLoadingMessage();
            geminiClient.addComments(code, text -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage(text);
            }), error -> runOnUiThread(() -> {
                removeLoading();
                addAiMessage("❌ " + handleAiError(error));
            }));
        });

        binding.btnCloseAi.setOnClickListener(v ->
                binding.drawerLayout.closeDrawer(GravityCompat.END));
    }

    private void setupBuildPanel() {
        binding.buildPanelHandle.setOnClickListener(v -> toggleBuildPanel());
        updateBuildPanelVisibility();
        appendBuildLog("► DevStudio Build System ready.", BuildManager.LogLevel.INFO);
        appendBuildLog("  Tap 🔨 Build to start.", BuildManager.LogLevel.INFO);
    }

    private void setupFabs() {
        binding.fabBuild.setOnClickListener(v -> startBuild());
        binding.fabAi.setOnClickListener(v -> {
            if (!binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                binding.drawerLayout.openDrawer(GravityCompat.END);
                if (!geminiClient.hasApiKey()) showApiKeyPrompt();
            } else {
                binding.drawerLayout.closeDrawer(GravityCompat.END);
            }
        });
        binding.fabFiles.setOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));
    }

    // ── File management ──────────────────────────────────────────────────────

    private void openFirstFile() {
        // Open MainActivity.java or first available file
        File preferred = findFile(projectDir, "MainActivity.java");
        if (preferred == null) preferred = findFirstFile(projectDir);
        if (preferred != null) openFile(preferred);
    }

    private void openFile(File file) {
        String path = file.getAbsolutePath();
        if (!openFiles.containsKey(path)) {
            String content = FileUtils.readFile(file);
            openFiles.put(path, content);
            openPaths.add(getFileName(path));
            openAbsPaths.add(path);            // keep parallel list in sync
            tabAdapter.notifyItemInserted(openPaths.size() - 1);
        }
        int idx = openAbsPaths.indexOf(path);  // index by abs-path, no collision risk
        switchToFile(path);
        tabAdapter.setActive(idx);
        binding.tabsRecycler.scrollToPosition(idx);
        updateEditorLanguage(path);
    }

    private void switchToFile(String path) {
        // Save current
        if (currentPath != null && openFiles.containsKey(currentPath)) {
            openFiles.put(currentPath, binding.codeEditor.getText().toString());
        }
        // Load new
        currentPath = path;
        String content = openFiles.getOrDefault(path, "");
        binding.codeEditor.setText(content);
        if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(getFileName(path));
        updateEditorLanguage(path);
    }

    private void closeTab(int pos) {
        if (openAbsPaths.size() <= 1) return;
        saveCurrentFile();

        String pathToClose = openAbsPaths.get(pos);
        openFiles.remove(pathToClose);
        openPaths.remove(pos);
        openAbsPaths.remove(pos);
        tabAdapter.notifyItemRemoved(pos);

        int newIdx = Math.min(pos, openAbsPaths.size() - 1);
        tabAdapter.setActive(newIdx);
        if (!openAbsPaths.isEmpty()) switchToFile(openAbsPaths.get(newIdx));
    }

    private void saveCurrentFile() {
        if (currentPath == null) return;
        String content = binding.codeEditor.getText().toString();
        openFiles.put(currentPath, content);
        try {
            FileUtils.writeFile(new File(currentPath), content);
        } catch (IOException e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    private void showNewFileDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("e.g. SecondActivity.java");
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setPadding(p * 2, p, p * 2, p);
        ll.addView(et);

        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("New File")
                .setView(ll)
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    File dir = currentPath != null
                            ? new File(currentPath).getParentFile() : projectDir;
                    File newFile = new File(dir, name);
                    try {
                        FileUtils.writeFile(newFile, templateForFile(name));
                        fileTreeAdapter.setRoot(projectDir);
                        openFile(newFile);
                    } catch (IOException e) { toast("Error: " + e.getMessage()); }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showFileOptions(File file) {
        String[] options = {"Open", "Rename", "Delete", "Copy Path"};
        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0 -> openFile(file);
                        case 1 -> renameFile(file);
                        case 2 -> deleteFile(file);
                        case 3 -> {
                            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("path", file.getAbsolutePath()));
                            toast("Path copied.");
                        }
                    }
                }).show();
    }

    private void renameFile(File file) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(file.getName());
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setPadding(p * 2, p, p * 2, p);
        ll.addView(et);

        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("Rename").setView(ll)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = et.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        File dest = new File(file.getParentFile(), newName);
                        file.renameTo(dest);
                        fileTreeAdapter.setRoot(projectDir);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void deleteFile(File file) {
        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("Delete \"" + file.getName() + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    FileUtils.deleteRecursive(file);
                    fileTreeAdapter.setRoot(projectDir);
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ── Build ────────────────────────────────────────────────────────────────

    private void startBuild() {
        saveCurrentFile();
        buildLog.clear();
        binding.tvBuildOutput.setText("");
        showBuildPanel(true);

        Project p = new Project(projectName, "", "", accentColor);

        buildManager.build(p, projectDir, new BuildManager.BuildCallback() {
            @Override public void onProgress(String msg, int pct) {
                runOnUiThread(() -> binding.tvBuildStatus.setText(msg));
            }
            @Override public void onLog(String line, BuildManager.LogLevel level) {
                runOnUiThread(() -> appendBuildLog(line, level));
            }
            @Override public void onSuccess(File zip, File apk) {
                runOnUiThread(() -> showBuildResult(zip, apk));
            }
            @Override public void onError(String msg) {
                runOnUiThread(() -> toast("Build error: " + msg));
            }
        });
    }

    private void appendBuildLog(String line, BuildManager.LogLevel level) {
        int color = switch (level) {
            case SUCCESS -> 0xFFA6E3A1;
            case ERROR   -> 0xFFF38BA8;
            case WARNING -> 0xFFF9E2AF;
            default      -> 0xFFCDD6F4;
        };
        SpannableString ss = new SpannableString(line + "\n");
        ss.setSpan(new ForegroundColorSpan(color), 0, ss.length(), 0);
        buildLog.append(ss);
        binding.tvBuildOutput.setText(buildLog);
        binding.buildOutputScroll.post(() ->
                binding.buildOutputScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void showBuildResult(File zip, File apk) {
        binding.tvBuildStatus.setText(apk != null ? "✓ APK ready" : "✓ ZIP ready");

        BottomSheetDialog sheet = new BottomSheetDialog(this, R.style.Dialog_DevStudio);
        View v = getLayoutInflater().inflate(R.layout.layout_build_result, null);
        sheet.setContentView(v);

        v.findViewById(R.id.btn_share_zip).setOnClickListener(x -> shareFile(zip));
        v.findViewById(R.id.btn_copy_path).setOnClickListener(x -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("path", zip.getAbsolutePath()));
            toast("Path copied."); sheet.dismiss();
        });
        if (apk != null) {
            v.findViewById(R.id.btn_share_apk).setVisibility(View.VISIBLE);
            v.findViewById(R.id.btn_share_apk).setOnClickListener(x -> shareFile(apk));
        }
        sheet.show();
    }

    private void exportZipOnly() {
        saveCurrentFile();
        toast("Exporting ZIP…");
        new Thread(() -> {
            try {
                File zip = new File(getCacheDir(), projectName + ".zip");
                FileUtils.zipDirectory(projectDir, zip);
                runOnUiThread(() -> shareFile(zip));
            } catch (Exception e) {
                runOnUiThread(() -> toast("Export failed: " + e.getMessage()));
            }
        }).start();
    }

    private void shareFile(File file) {
        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("application/zip");
        share.putExtra(Intent.EXTRA_STREAM, uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(share, "Share " + file.getName()));
    }

    // ── Build panel UI ───────────────────────────────────────────────────────

    private void toggleBuildPanel() {
        buildPanelExpanded = !buildPanelExpanded;
        updateBuildPanelVisibility();
    }

    private void showBuildPanel(boolean show) {
        buildPanelExpanded = show;
        updateBuildPanelVisibility();
    }

    private void updateBuildPanelVisibility() {
        binding.buildOutputScroll.setVisibility(buildPanelExpanded ? View.VISIBLE : View.GONE);
        binding.ivPanelToggle.setRotation(buildPanelExpanded ? 180f : 0f);
    }

    // ── AI Chat ──────────────────────────────────────────────────────────────

    private void sendChat() {
        String text = binding.etChatInput.getText().toString().trim();
        if (text.isEmpty()) return;
        binding.etChatInput.setText("");
        hideKeyboard();

        if (!geminiClient.hasApiKey()) { showApiKeyPrompt(); return; }

        addUserMessage(text);
        addLoadingMessage();

        String fileCtx = binding.codeEditor.getText().toString();
        geminiClient.chat(text, new ArrayList<>(chatMessages), fileCtx,
                response -> runOnUiThread(() -> { removeLoading(); addAiMessage(response); }),
                error    -> runOnUiThread(() -> { removeLoading(); addAiMessage("❌ " + handleAiError(error)); }));
    }

    private void addUserMessage(String text) {
        chatMessages.add(ChatMessage.user(text));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollChatToBottom();
    }

    private void addAiMessage(String text) {
        chatMessages.add(ChatMessage.ai(text));
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollChatToBottom();
    }

    private void addLoadingMessage() {
        chatMessages.add(ChatMessage.loading());
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        scrollChatToBottom();
    }

    private void removeLoading() {
        for (int i = chatMessages.size() - 1; i >= 0; i--) {
            if (chatMessages.get(i).isLoading) {
                chatMessages.remove(i);
                chatAdapter.notifyItemRemoved(i);
                break;
            }
        }
    }

    private void scrollChatToBottom() {
        binding.chatRecycler.scrollToPosition(chatMessages.size() - 1);
    }

    private String handleAiError(String error) {
        if ("NO_API_KEY".equals(error)) {
            showApiKeyPrompt();
            return "Please set your Gemini API key first.";
        }
        return error;
    }

    private void showApiKeyPrompt() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("AIza…");
        et.setText(geminiClient.getApiKey());
        int p = (int)(16 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setPadding(p * 2, p, p * 2, p);
        ll.addView(et);

        new MaterialAlertDialogBuilder(this, R.style.Dialog_DevStudio)
                .setTitle("Gemini API Key")
                .setMessage("Get a free key at aistudio.google.com/app/apikey")
                .setView(ll)
                .setPositiveButton("Save", (d, w) -> {
                    String key = et.getText().toString().trim();
                    if (!key.isEmpty()) {
                        geminiClient.saveApiKey(key);
                        toast("API key saved.");
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void updateEditorLanguage(String path) {
        // JavaLanguage handles both .java and .xml reasonably
        // In a full implementation we'd switch to XmlLanguage for .xml
        binding.codeEditor.setEditorLanguage(new JavaLanguage());
    }

    private String getFileName(String path) {
        return new File(path).getName();
    }

    private File findFile(File dir, String name) {
        if (dir.getName().equals(name) && !dir.isDirectory()) return dir;
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            File found = findFile(f, name);
            if (found != null) return found;
        }
        return null;
    }

    private File findFirstFile(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (!f.isDirectory()) return f;
            File found = findFirstFile(f);
            if (found != null) return found;
        }
        return null;
    }

    private String templateForFile(String name) {
        String ext = FileUtils.extensionOf(name);
        return switch (ext) {
            case "java" -> "package com.example;\n\npublic class " +
                           name.replace(".java","") + " {\n\n}\n";
            case "xml"  -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                           "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    android:layout_width=\"match_parent\"\n" +
                           "    android:layout_height=\"match_parent\"\n" +
                           "    android:orientation=\"vertical\">\n\n</LinearLayout>\n";
            default     -> "";
        };
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentFile();
    }
}
