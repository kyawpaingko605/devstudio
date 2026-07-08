package pro.devstudio.mobile.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import pro.devstudio.mobile.databinding.ItemFileTreeBinding;
import pro.devstudio.mobile.model.FileNode;

public class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.VH> {

    public interface Listener {
        void onFileClick(File file);
        void onFileLongClick(File file);
    }

    private final List<FileNode> nodes = new ArrayList<>();
    private final Listener       listener;

    public FileTreeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRoot(File rootDir) {
        nodes.clear();
        addNodes(rootDir, 0, true);
        notifyDataSetChanged();
    }

    private void addNodes(File dir, int depth, boolean expanded) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator
                .<File, Boolean>comparing(f -> !f.isDirectory())   // dirs first
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File f : children) {
            // Skip hidden and build dirs
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory() && f.getName().equals("build")) continue;

            FileNode node = new FileNode(f, depth);
            node.isExpanded = expanded && depth < 2; // auto-expand first 2 levels
            nodes.add(node);
            if (f.isDirectory() && node.isExpanded) addNodes(f, depth + 1, true);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemFileTreeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FileNode node = nodes.get(pos);

        // Indent 計算
        int indentDp = node.depth * 16;
        float density = h.b.getRoot().getContext().getResources().getDisplayMetrics().density;
        h.b.viewIndent.getLayoutParams().width = (int)(indentDp * density);
        h.b.viewIndent.requestLayout();

        // ── 🎨 PC STYLE FILE COLOR & ICON SYSTEM ─────────────────────────────
        File file = node.file;
        String fileName = node.name();
        String lowName = fileName.toLowerCase();
        
        String iconStr = "📄"; // Default File
        String colorHex = "#757575"; // Default Gray
        
        if (node.isDirectory()) {
            iconStr = node.isExpanded ? "📂" : "📁";
            colorHex = "#FFB300"; // Android Studio Folder Gold
            h.b.tvFileName.setTypeface(null, Typeface.BOLD);
            h.b.tvFileName.setTextColor(Color.parseColor("#212121"));
        } else {
            h.b.tvFileName.setTypeface(null, Typeface.NORMAL);
            h.b.tvFileName.setTextColor(Color.parseColor("#424242"));
            
            // File Type Extensions Checking
            if (lowName.equals("androidmanifest.xml")) {
                iconStr = "⚙️"; 
                colorHex = "#00897B"; // Manifest Teal
            } else if (lowName.endsWith(".java")) {
                iconStr = "☕"; 
                colorHex = "#1E88E5"; // Java Blue
            } else if (lowName.endsWith(".xml")) {
                iconStr = "🎨"; 
                colorHex = "#F4511E"; // Layout/Resource Orange
            } else if (lowName.endsWith(".gradle") || lowName.startsWith("gradle")) {
                iconStr = "🐘"; 
                colorHex = "#0288D1"; // Gradle Elephant Blue
            } else if (lowName.endsWith(".png") || lowName.endsWith(".jpg") || lowName.endsWith(".webp")) {
                iconStr = "🖼️";
                colorHex = "#43A047"; // Image Green
            } else if (lowName.endsWith(".txt") || lowName.endsWith(".md")) {
                iconStr = "📝";
                colorHex = "#616161";
            }
        }

        // Apply Icon & Colors directly to ViewBinding fields
        h.b.tvIcon.setText(iconStr);
        h.b.tvIcon.setTextColor(Color.parseColor(colorHex));
        h.b.tvFileName.setText(fileName);
        
        // ── BADGE CONFIGURATION ──────────────────────────────────────────────
        h.b.tvBadge.setText(node.languageBadge());
        h.b.tvBadge.setVisibility(node.languageBadge().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

        // Click Listeners
        h.b.getRoot().setOnClickListener(v -> {
            if (node.isDirectory()) {
                toggleExpand(node, pos);
            } else {
                listener.onFileClick(node.file);
            }
        });
        h.b.getRoot().setOnLongClickListener(v -> {
            listener.onFileLongClick(node.file);
            return true;
        });
    }

    private void toggleExpand(FileNode node, int pos) {
        node.isExpanded = !node.isExpanded;
        if (node.isExpanded) {
            // Insert children
            List<FileNode> children = new ArrayList<>();
            File[] files = node.file.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator
                        .<File, Boolean>comparing(f -> !f.isDirectory())
                        .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File f : files) {
                    if (!f.getName().startsWith(".") && !(f.isDirectory() && f.getName().equals("build")))
                        children.add(new FileNode(f, node.depth + 1));
                }
            }
            nodes.addAll(pos + 1, children);
            notifyItemRangeInserted(pos + 1, children.size());
        } else {
            // Remove all descendants
            int end = pos + 1;
            while (end < nodes.size() && nodes.get(end).depth > node.depth) end++;
            int count = end - pos - 1;
            nodes.subList(pos + 1, end).clear();
            notifyItemRangeRemoved(pos + 1, count);
        }
        notifyItemChanged(pos);
    }

    @Override public int getItemCount() { return nodes.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemFileTreeBinding b;
        VH(ItemFileTreeBinding b) { super(b.getRoot()); this.b = b; }
    }
}
