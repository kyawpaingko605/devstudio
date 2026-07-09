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
 */
public class GeminiClient {

    private static final String PREF_NAME    = "devstudio_prefs";
    private static final String PREF_API_KEY = "gemini_api_key";
    private static final String PREF_MODEL   = "gemini_model";
    private static final MediaType JSON      = MediaType.get("application/json; charset=utf-8");

    // ✅ ရနိုင်တဲ့ Gemini မော်ဒယ်များ (Static)
    public static final String[] AVAILABLE_MODELS = {
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-1.0-pro"
    };

    public static final String[] MODEL_DISPLAY_NAMES = {
        "Gemini 2.0 Flash (Fast, Best)",
        "Gemini 2.0 Flash Lite (Fast, Cheap)",
        "Gemini 1.5 Flash (Balanced)",
        "Gemini 1.5 Pro (Powerful)",
        "Gemini 1.0 Pro (Legacy)"
    };

    private static final String SYSTEM_PROMPT =
            "You are an expert Android developer assistant inside DevStudio Mobile IDE.\n" +
            "Help the user write clean Java/Kotlin code and XML layouts for Android apps.\n" +
            "- Be concise and direct.\n" +
            "- Show code in fenced code blocks.\n" +
            "- Prefer Java unless Kotlin is requested.\n" +
            "- When fixing bugs, briefly explain the root cause first.\n" +
            "- Format your response for comfortable mobile reading.";

    private static final String AUTO_FIX_PROMPT =
            "You are an expert Android developer tool.\n" +
            "Analyze the provided Android build error log and the source code.\n" +
            "Fix the error and return ONLY the corrected, complete, deployable Java/Kotlin source code.\n" +
            "STRICT RULES:\n" +
            "- Do NOT include any introductory or concluding text.\n" +
            "- Do NOT explain the changes or the bug.\n" +
            "- Do NOT use markdown code fences (like ```java or ```).\n" +
            "- Reply ONLY with the raw source code text that can be saved directly into a file.";

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

    public String  getApiKey()       { return prefs().getString(PREF_API_KEY, ""); }
    public boolean hasApiKey()       { return !getApiKey().isEmpty(); }
    public void    saveApiKey(String k) { prefs().edit().putString(PREF_API_KEY, k.trim()).apply(); }

    public String  getSelectedModel() { 
        String model = prefs().getString(PREF_MODEL, "gemini-2.0-flash");
        if (!isValidModel(model)) {
            model = "gemini-2.0-flash";
            saveSelectedModel(model);
        }
        return model;
    }
    
    public void    saveSelectedModel(String model) { 
        if (isValidModel(model)) {
            prefs().edit().putString(PREF_MODEL, model.trim()).apply();
        }
    }
    
    public boolean isValidModel(String model) {
        for (String m : AVAILABLE_MODELS) {
            if (m.equals(model)) return true;
        }
        return false;
    }
    
    public List<String> getAvailableModels() {
        List<String> list = new ArrayList<>();
        for (String m : AVAILABLE_MODELS) {
            list.add(m);
        }
        return list;
    }
    
    // ✅ getModelDisplayName() method
    public String getModelDisplayName(String model) {
        for (int i = 0; i < AVAILABLE_MODELS.length; i++) {
            if (AVAILABLE_MODELS[i].equals(model)) {
                return MODEL_DISPLAY_NAMES[i];
            }
        }
        return model;
    }

    private String getApiUrl() {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + getSelectedModel() + ":generateContent?key=";
    }

    // ── Core chat call ───────────────────────────────────────────────────────

    public void chat(String userMessage,
                     List<ChatMessage> history,
                     String fileContext,
                     Consumer<String> onSuccess,
                     Consumer<String> onError) {
        chatWithCustomPrompt(SYSTEM_PROMPT, userMessage, history, fileContext, onSuccess, onError);
    }

    private void chatWithCustomPrompt(String systemPrompt,
                                      String userMessage,
                                      List<ChatMessage> history,
                                      String fileContext,
                                      Consumer<String> onSuccess,
                                      Consumer<String> onError) {

        String apiKey = getApiKey();
        if (apiKey.isEmpty()) { onError.accept("NO_API_KEY"); return; }

        try {
            JSONArray contents = new JSONArray();

            String sysText = systemPrompt;
            if (fileContext != null && !fileContext.isBlank()) {
                sysText = sysText + "\n\nCURRENT FILE:\n```\n" + trunc(fileContext, 4000) + "\n```";
            }
            addTurn(contents, "user",  sysText);
            addTurn(contents, "model", "Ready. How can I help with your Android project?");

            if (history != null) {
                int start = Math.max(0, history.size() - 8);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage m = history.get(i);
                    if (m.isLoading || m.content.isBlank()) continue;
                    addTurn(contents, m.role == ChatMessage.ROLE_USER ? "user" : "model", m.content);
                }
            }

            addTurn(contents, "user", userMessage);

            JSONObject body = new JSONObject()
                    .put("contents", contents)
                    .put("generationConfig", new JSONObject()
                            .put("temperature", 0.3)
                            .put("maxOutputTokens", 4096));

            String fullUrl = getApiUrl() + apiKey;

            Request req = new Request.Builder()
                    .url(fullUrl)
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
                        String message;
                        int code = response.code();
                        if (code == 400) {
                            message = "Invalid request / API key.";
                        } else if (code == 403) {
                            message = "API key not authorized. Enable Generative Language API.";
                        } else if (code == 429) {
                            message = "Rate limit hit — wait a moment and try again.";
                        } else {
                            message = "API error " + code + " - " + bodyStr;
                        }
                        onError.accept(message);
                        return;
                    }
                    try {
                        String text = new JSONObject(bodyStr)
                                .getJSONArray("candidates").getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts").getJSONObject(0)
                                .getString("text");
                        onSuccess.accept(text);
                    } catch (Exception e) {
                        onError.accept("Parse error: " + e.getMessage() + " - raw: " + bodyStr);
                    }
                }
            });
        } catch (Exception e) {
            onError.accept("Request error: " + e.getMessage());
        }
    }

    // ── Shortcuts & Auto-Fix ─────────────────────────────────────────────────

    public void fixCodeAuto(String code, String errorLog,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        String msg = "ERROR LOG:\n" + errorLog + "\n\nSOURCE CODE TO FIX:\n" + code;
        chatWithCustomPrompt(AUTO_FIX_PROMPT, msg, List.of(), null, onSuccess, onError);
    }

    public void fixCode(String code, String errorHint,
                        Consumer<String> onSuccess, Consumer<String> onError) {
        String msg = "Fix this code"
                + (errorHint != null && !errorHint.isBlank() ? " (error: " + errorHint + ")" : "")
                + ":\n```\n" + code + "\n```";
        chat(msg, List.of(), code, onSuccess, onError);
    }

    public void explainCode(String code,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        chat("Explain this Android code clearly:\n```\n" + code + "\n```",
             List.of(), null, onSuccess, onError);
    }

    public void addComments(String code,
                            Consumer<String> onSuccess, Consumer<String> onError) {
        chat("Add clear Javadoc and inline comments to this code:\n```\n" + code + "\n```",
             List.of(), null, onSuccess, onError);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void addTurn(JSONArray arr, String role, String text) throws Exception {
        arr.put(new JSONObject()
                .put("role",  role)
                .put("parts", new JSONArray().put(new JSONObject().put("text", text))));
    }

    private String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "\n...(truncated)" : s;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}
