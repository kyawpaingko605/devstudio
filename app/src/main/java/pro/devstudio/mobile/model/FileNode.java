package pro.devstudio.mobile.model;

import java.io.File;

public class FileNode {
    public File   file;
    public int    depth;
    public boolean isExpanded;

    public FileNode(File file, int depth) {
        this.file       = file;
        this.depth      = depth;
        this.isExpanded = false;
    }

    public boolean isDirectory() { return file.isDirectory(); }
    public String  name()        { return file.getName(); }

    /** Language badge text derived from file extension. */
    public String languageBadge() {
        String n = name().toLowerCase();
        if (n.endsWith(".java"))  return "Java";
        if (n.endsWith(".kt"))    return "Kotlin";
        if (n.endsWith(".xml"))   return "XML";
        if (n.endsWith(".gradle"))return "Gradle";
        if (n.endsWith(".json"))  return "JSON";
        if (n.endsWith(".md"))    return "MD";
        return "";
    }

    /** Icon glyph (text emoji) for the file tree. */
    public String icon() {
        if (isDirectory()) return isExpanded ? "▾" : "▸";
        String n = name().toLowerCase();
        if (n.endsWith(".java") || n.endsWith(".kt")) return "☕";
        if (n.endsWith(".xml"))                       return "✦";
        if (n.endsWith(".gradle"))                    return "⚙";
        if (n.endsWith(".json"))                      return "{}";
        return "·";
    }
}
