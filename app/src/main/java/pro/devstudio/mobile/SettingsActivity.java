package pro.devstudio.mobile;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import pro.devstudio.mobile.ai.GeminiClient;
import pro.devstudio.mobile.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySettingsBinding binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setTitle("Settings");
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        
        private GeminiClient geminiClient;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            geminiClient = new GeminiClient(requireContext());

            // ── API Key ──────────────────────────────────────────────────────
            EditTextPreference apiKey = findPreference("gemini_api_key");
            if (apiKey != null) {
                apiKey.setSummaryProvider(pref -> {
                    String k = geminiClient.getApiKey();
                    return k.isEmpty() ? "Not set — tap to add" : "••••••••" + k.substring(Math.max(0, k.length() - 4));
                });
                apiKey.setOnPreferenceChangeListener((pref, val) -> {
                    String newKey = (String) val;
                    if (newKey != null && !newKey.trim().isEmpty()) {
                        geminiClient.saveApiKey(newKey.trim());
                        Toast.makeText(requireContext(), "API key saved.", Toast.LENGTH_SHORT).show();
                        pref.setSummaryProvider(pref.getSummaryProvider());
                    }
                    return false;
                });
            }

            // ── AI Model Selector ────────────────────────────────────────────
            ListPreference modelSelector = findPreference("gemini_model");
            if (modelSelector != null) {
                // ✅ Model list ကို set လုပ်ပါ
                modelSelector.setEntries(GeminiClient.MODEL_DISPLAY_NAMES);
                modelSelector.setEntryValues(GeminiClient.AVAILABLE_MODELS);
                
                // ✅ လက်ရှိ model ကို set လုပ်ပါ
                String currentModel = geminiClient.getSelectedModel();
                modelSelector.setValue(currentModel);
                
                // ✅ Summary ကို update လုပ်ပါ
                modelSelector.setSummaryProvider(pref -> {
                    String model = ((ListPreference) pref).getValue();
                    if (model == null) model = "gemini-2.0-flash";
                    return geminiClient.getModelDisplayName(model);
                });
                
                // ✅ Model ပြောင်းတဲ့အခါ save လုပ်ပါ
                modelSelector.setOnPreferenceChangeListener((pref, val) -> {
                    String newModel = (String) val;
                    if (newModel != null && !newModel.isEmpty()) {
                        geminiClient.saveSelectedModel(newModel);
                        Toast.makeText(requireContext(), 
                            "Model: " + geminiClient.getModelDisplayName(newModel), 
                            Toast.LENGTH_SHORT).show();
                        pref.setSummaryProvider(pref.getSummaryProvider());
                    }
                    return false;
                });
            }

            // ── AI Auto-Fix ──────────────────────────────────────────────────
            SwitchPreferenceCompat autoFix = findPreference("ai_auto_fix");
            if (autoFix != null) {
                autoFix.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Enabled" : "Disabled";
                });
            }

            // ── AI Chat History ─────────────────────────────────────────────
            SwitchPreferenceCompat chatHistory = findPreference("ai_chat_history");
            if (chatHistory != null) {
                chatHistory.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "History saved" : "History cleared";
                });
            }

            // ── Font Size ────────────────────────────────────────────────────
            SeekBarPreference fontSize = findPreference("editor_font_size");
            if (fontSize != null) {
                fontSize.setSummaryProvider(pref -> {
                    int val = ((SeekBarPreference) pref).getValue();
                    return val + "sp";
                });
            }

            // ── Word Wrap ────────────────────────────────────────────────────
            SwitchPreferenceCompat wordWrap = findPreference("word_wrap");
            if (wordWrap != null) {
                wordWrap.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "On" : "Off";
                });
            }

            // ── Line Numbers ─────────────────────────────────────────────────
            SwitchPreferenceCompat lineNumbers = findPreference("line_numbers");
            if (lineNumbers != null) {
                lineNumbers.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "On" : "Off";
                });
            }

            // ── Auto Save ────────────────────────────────────────────────────
            SwitchPreferenceCompat autoSave = findPreference("auto_save");
            if (autoSave != null) {
                autoSave.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Auto save on" : "Auto save off";
                });
            }

            // ── AMOLED Black ─────────────────────────────────────────────────
            SwitchPreferenceCompat amoledBlack = findPreference("amoled_black");
            if (amoledBlack != null) {
                amoledBlack.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Pure black background" : "Default dark background";
                });
            }

            // ── Editor Theme ─────────────────────────────────────────────────
            ListPreference editorTheme = findPreference("editor_theme");
            if (editorTheme != null) {
                editorTheme.setSummaryProvider(pref -> {
                    String val = ((ListPreference) pref).getValue();
                    String[] values = getResources().getStringArray(R.array.editor_theme_values);
                    String[] entries = getResources().getStringArray(R.array.editor_themes);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(val)) return entries[i];
                    }
                    return "Dark";
                });
            }

            // ── Accent Color ─────────────────────────────────────────────────
            ListPreference accentColor = findPreference("accent_color");
            if (accentColor != null) {
                accentColor.setSummaryProvider(pref -> {
                    String val = ((ListPreference) pref).getValue();
                    String[] values = getResources().getStringArray(R.array.accent_color_values);
                    String[] entries = getResources().getStringArray(R.array.accent_colors);
                    for (int i = 0; i < values.length; i++) {
                        if (values[i].equals(val)) return entries[i];
                    }
                    return "Catppuccin Mauve";
                });
            }

            // ── Build Debug Logs ─────────────────────────────────────────────
            SwitchPreferenceCompat debugLogs = findPreference("build_debug_logs");
            if (debugLogs != null) {
                debugLogs.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Detailed logs" : "Minimal logs";
                });
            }

            // ── Debug Signing ────────────────────────────────────────────────
            SwitchPreferenceCompat debugSigning = findPreference("debug_signing");
            if (debugSigning != null) {
                debugSigning.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Using debug.keystore" : "Using release.keystore";
                });
            }

            // ── Auto Build ───────────────────────────────────────────────────
            SwitchPreferenceCompat autoBuild = findPreference("auto_build");
            if (autoBuild != null) {
                autoBuild.setSummaryProvider(pref -> {
                    boolean enabled = ((SwitchPreferenceCompat) pref).isChecked();
                    return enabled ? "Auto build on" : "Auto build off";
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (geminiClient != null) {
                ListPreference modelSelector = findPreference("gemini_model");
                if (modelSelector != null) {
                    modelSelector.setValue(geminiClient.getSelectedModel());
                    modelSelector.setSummaryProvider(modelSelector.getSummaryProvider());
                }
                
                EditTextPreference apiKey = findPreference("gemini_api_key");
                if (apiKey != null) {
                    apiKey.setSummaryProvider(apiKey.getSummaryProvider());
                }
            }
        }
    }
}
