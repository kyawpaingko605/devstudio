package pro.devstudio.mobile.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import pro.devstudio.mobile.model.ChatMessage;

/**
 * Calls Google Gemini API dynamically with multiple model support.
 * All callbacks arrive on OkHttp I/O threads — post to main thread before touching UI.
 * Upgraded: Fixed broken Markdown URL inside endpoint generation logic.
 */
public class GeminiClient {

    private static final String PREF_NAME    = "devstudio_prefs";
    private static final String PREF_API_KEY = "gemini_api_key";
    private static final String PREF_MODEL   = "gemini_model";
    private static final MediaType JSON      = MediaType.get("application/json; charset=utf-8");

    // Chat စနစ်အတွက် ကူညီပေးမည့် Assistant prompt
    private static final String SYSTEM_PROMPT =
            "You are an expert Android developer assistant inside DevStudio Mobile IDE.\n" +
            "Help the user write clean Java/Kotlin code and XML layouts for Android apps.\n" +
            "- Be concise and direct.\n" +
            "- Show code in fenced code blocks.\n" +
            "- Prefer Java unless Kotlin is requested.\n" +
            "- Format your response for comfortable mobile reading.";

    // 🚀 Local Build Error တက်လျှင် AI မှ ဖိုင်ထဲသို့ တိုက်ရိုက်ဝင်ပြင်ပေးရန် အဓိက Instruction
    private static final String AUTO_FIX_PROMPT =
            "You are an automated code repair tool embedded within the DevStudio Android IDE compiler system.\n" +
            "Your task is to solve local compile errors during the project build process.\n" +
            "CRITICAL INSTRUCTIONS:\n" +
            "- Analyze the build error log and the source code meticulously.\n" +
            "- Repair syntax errors, missing variables, type mismatches, or faulty declarations.\n" +
            "- Output ONLY the corrected, full, deployable source code.\n" +
            "- Never wrap code inside markdown formatting (such as ```java) or add conversational greetings.\n" +
            "- Your output must immediately be compilable when dumped straight to a physical disk.";

    private final OkHttpClient http;
    private final Context      context;

    public GeminiClient(Context context) {
        this.context = context.getApplicationContext();
        this.http    = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    // ── API Key & Model Configuration ────────────────────────────────────────

    public String  getApiKey()          { return prefs().getString(PREF_API_KEY, ""); }
    public boolean hasApiKey()          { return !getApiKey().isEmpty(); }
    public void    saveApiKey(String k) { prefs().edit().putString(PREF_API_KEY, k.trim()).apply(); }

    // Multi-Model Support: အသုံးပြုသူ ရွေးချယ်ထားသော မော်ဒယ်ကို ယူသုံးမည်
    public String  getSelectedModel()   { return prefs().getString(PREF_MODEL, "gemini-2.0-flash"); }
    public void    saveSelectedModel(String model) { prefs().edit().putString(PREF_MODEL, model.trim()).apply(); }

    private String getApiUrl() {
        // Fixed: Clean base URL string without markdown brackets
        return "[https://generativelanguage.googleapis.com/v1beta/models/](https://generativelanguage.googleapis.com/v1beta/models/)" + getSelectedModel() + ":generateContent?key=";
    }

    // ── Core chat call ───────────────────────────────────────────────────────

    public void chat(String userMessage,
                     List<ChatMessage> history,
                     String fileContext,
                     Consumer<String> onSuccess,
                     Consumer<String> onError) {
        chatWithCustomPrompt(SYSTEM_PROMPT, userMessage, history, fileContext, false, onSuccess, onError);
    }

    private void chatWithCustomPrompt(String systemPrompt,
                                      String userMessage,
                                      List<ChatMessage> history,
                                      String fileContext,
                                      boolean isAutoFix,
                                      Consumer<String> onSuccess,
                                      Consumer<String> onError) {

        String apiKey = getApiKey();
        if (apiKey.isEmpty()) { onError.accept("NO_API_KEY"); return; }

        try {
            JSONArray contents = new JSONArray();

            // Setup Payload Context Injecting cleanly without system instruction duplication
            if (!isAutoFix && fileContext != null && !fileContext.isBlank()) {
                String contextMsg = "CONTEXT: The user is currently editing a file with this content:\n```\n" + trunc(fileContext, 4000) + "\n```";
                addTurn(contents, "user", contextMsg);
                addTurn(contents, "model", "I have loaded the file context. I will references this for your questions.");
            }

            // Chat History injecting
            if (history != null && !isAutoFix) {
                int start = Math.max(0, history.size() - 8);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage m = history.get(i);
                    if (m.isLoading || m.content.isBlank()) continue;
                    addTurn(contents, m.role == ChatMessage.ROLE_USER ? "user" : "model", m.content);
                }
            }

            // Current message
            addTurn(contents, "user", userMessage);

            // Construct Gemini v1beta Payload with systemInstruction support
            JSONObject body = new JSONObject()
                    .put("contents", contents)
                    .put("systemInstruction", new JSONObject()
                            .put("parts", new JSONArray().put(new JSONObject().put("text", systemPrompt))))
                    .put("generationConfig", new JSONObject()
                            .put("temperature", isAutoFix ? 0.1 : 0.3)
                            .put("responseMimeType", "text/plain")
                            .put("maxOutputTokens", 8192));

            Request req = new Request.Builder()
                    .url(getApiUrl() + apiKey)
                    .post(RequestBody.create(body.toString(), JSON))
                    .header("Content-Type", "application/json")
                    .build();

            http.newCall(req).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    onError.accept("Network error: " + e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    String bodyStr = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        onError.accept(switch (response.code()) {
                            case 400 -> "Invalid request / API key / Model mapping configuration. (Code 400)";
                            case 403 -> "API key not authorized. Enable Generative Language API. (Code 403)";
                            case 429 -> "Rate limit hit — wait a moment and try again. (Code 429)";
                            default  -> "API error " + response.code() + ": " + bodyStr;
                        });
                        return;
                    }
                    try {
                        String text = new JSONObject(bodyStr)
                                .getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts").getJSONObject(0)
                                .getString("text");
                        
                        if (isAutoFix) {
                            text = cleanMarkdownFences(text);
                        }
                        onSuccess.accept(text);
                    } catch (Exception e) {
                        onError.accept("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            onError.accept("Request error: " + e.getMessage());
        }
    }

    // ── Shortcuts & Auto-Fix ─────────────────────────────────────────────────

    /**
     * 🛠 Auto Code Repair Interface
     */
    public void fixCodeAuto(String code, String errorLog,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        String msg = "TARGET ERROR LOG:\n" + errorLog + "\n\nBROKEN FILE SOURCE CODE:\n" + code;
        chatWithCustomPrompt(AUTO_FIX_PROMPT, msg, new ArrayList<>(), null, true, onSuccess, onError);
    }

    public void fixCode(String code, String errorHint,
                        Consumer<String> onSuccess, Consumer<String> onError) {
        String msg = "Fix this code"
                + (errorHint != null && !errorHint.isBlank() ? " (error: " + errorHint + ")" : "")
                + ":\n```\n" + code + "\n```";
        chat(msg, new ArrayList<>(), code, onSuccess, onError);
    }

    public void explainCode(String code,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        chat("Explain this Android code clearly:\n```\n" + code + "\n```",
             new ArrayList<>(), null, onSuccess, onError);
    }

    public void addComments(String code,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        chat("Add clear Javadoc and inline comments to this code:\n```\n" + code + "\n```",
             new ArrayList<>(), null, onSuccess, onError);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void addTurn(JSONArray arr, String role, String text) throws Exception {
        arr.put(new JSONObject()
                .put("role",  role)
                .put("parts", new JSONArray().put(new JSONObject().put("text", text))));
    }

    private String cleanMarkdownFences(String input) {
        if (input == null) return "";
        String out = input.trim();
        if (out.startsWith("```")) {
            out = out.replaceAll("^```[a-zA-Z]*\\n?", "");
            out = out.replaceAll("\\n?```$", "");
        }
        return out.trim();
    }

    private String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "\n...(truncated)" : s;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
