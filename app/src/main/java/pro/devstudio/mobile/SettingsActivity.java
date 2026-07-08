package pro.devstudio.mobile;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            GeminiClient client = new GeminiClient(requireContext());

            // API key
            EditTextPreference apiKey = findPreference("gemini_api_key");
            if (apiKey != null) {
                apiKey.setSummaryProvider(pref -> {
                    String k = client.getApiKey();
                    return k.isEmpty() ? "Not set — tap to add" : "••••••••" + k.substring(Math.max(0, k.length() - 4));
                });
                apiKey.setOnPreferenceChangeListener((pref, val) -> {
                    client.saveApiKey((String) val);
                    Toast.makeText(requireContext(), "API key saved.", Toast.LENGTH_SHORT).show();
                    pref.setSummaryProvider(pref.getSummaryProvider()); // refresh
                    return false; // we handle storage ourselves
                });
            }

            // Font size preview
            SeekBarPreference fontSize = findPreference("editor_font_size");
            if (fontSize != null) {
                fontSize.setMin(8);
                fontSize.setMax(24);
                fontSize.setShowSeekBarValue(true);
            }
        }
    }
}
