package pro.devstudio.mobile.model;

import java.io.Serializable;

/**
 * Project Model - Represents an Android project inside DevStudio.
 * Implements Serializable for easy storage and Intent passing.
 */
public class Project implements Serializable {

    public String name;
    public String packageName;
    public String template;      // "empty" | "basic" | "login" | "list"
    public String accentColor;   // hex string e.g. "#CBA6F7"
    public long   createdAt;
    public long   lastModified;

    // Empty constructor for JSON/Persistence libraries
    public Project() {
        this.createdAt = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }

    public Project(String name, String packageName, String template, String accentColor) {
        this.name        = name;
        this.packageName = packageName;
        this.template    = (template == null) ? "empty" : template;
        this.accentColor = (accentColor == null || accentColor.isEmpty()) ? "#CBA6F7" : accentColor;
        this.createdAt   = System.currentTimeMillis();
        this.lastModified = this.createdAt;
    }

    /** * Directory-safe project name (spaces/special chars -> underscores). 
     */
    public String dirName() {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    /** * Returns human-readable relative time string. 
     */
    public String relativeTime() {
        long diff = System.currentTimeMillis() - lastModified;
        if (diff < 60_000)           return "just now";
        if (diff < 3_600_000)        return (diff / 60_000) + " min ago";
        if (diff < 86_400_000)       return (diff / 3_600_000) + " hr ago";
        return (diff / 86_400_000) + " days ago";
    }

    /** * UI အတွက် လှပသော Template Label ကို ပြန်ပေးမည်။ 
     */
    public String getTemplateDisplayName() {
        if (template == null) return "Empty";
        return template.substring(0, 1).toUpperCase() + template.substring(1);
    }
    
    /**
     * ပရောဂျက်တစ်ခုကို နောက်တစ်ခါပြန်ဖွင့်သည့်အခါ time ကို Update လုပ်ရန်
     */
    public void updateModifiedTime() {
        this.lastModified = System.currentTimeMillis();
    }
}
