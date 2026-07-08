package pro.devstudio.mobile;

import android.app.Application;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import android.util.Log;

import pro.devstudio.mobile.util.ToolInstaller;

public class DevStudioApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Pre-load sora textmate grammars for faster first launch
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                new AssetsFileResolver(getAssets()));
        } catch (Exception ignored) {}

        // Install per-ABI native tools from assets/tools/ into internal files dir
        try {
            ToolInstaller.installAllFromAssets(this);
        } catch (Exception e) {
            Log.w("DevStudioApp", "ToolInstaller failed", e);
        }
    }
}
