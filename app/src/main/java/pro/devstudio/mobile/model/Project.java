package pro.devstudio.mobile.model;

public class Project {
    public String name;
    public String packageName;
    public String template;      // "empty" | "basic" | "login" | "list"
    public String accentColor;   // hex string e.g. "#CBA6F7"
    public long   createdAt;
    public long   lastModified;

    public Project() {}

    public Project(String name, String packageName, String template, String accentColor) {
        this.name        = name;
        this.packageName = packageName;
        this.template    = template;
        this.accentColor = accentColor;
        this.createdAt   = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }

    /** Directory-safe project name (spaces → underscores). */
    public String dirName() {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    public String relativeTime() {
        long diff = System.currentTimeMillis() - lastModified;
        if (diff < 60_000)           return "just now";
        if (diff < 3_600_000)        return (diff / 60_000) + " min ago";
        if (diff < 86_400_000)       return (diff / 3_600_000) + " hr ago";
        return (diff / 86_400_000) + " days ago";
    }
}
