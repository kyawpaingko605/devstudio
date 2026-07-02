package pro.devstudio.mobile.model;

public class ChatMessage {
    public static final int ROLE_USER = 0;
    public static final int ROLE_AI   = 1;
    public static final int ROLE_SYS  = 2; // system info / progress

    public int    role;
    public String content;
    public long   timestamp;
    public boolean isLoading; // spinner placeholder

    public ChatMessage(int role, String content) {
        this.role      = role;
        this.content   = content;
        this.timestamp = System.currentTimeMillis();
    }

    public static ChatMessage user(String text)    { return new ChatMessage(ROLE_USER, text); }
    public static ChatMessage ai(String text)      { return new ChatMessage(ROLE_AI,   text); }
    public static ChatMessage system(String text)  { return new ChatMessage(ROLE_SYS,  text); }
    public static ChatMessage loading() {
        ChatMessage m = new ChatMessage(ROLE_AI, "");
        m.isLoading = true;
        return m;
    }
}
