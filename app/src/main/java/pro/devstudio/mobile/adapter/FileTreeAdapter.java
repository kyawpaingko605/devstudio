package pro.devstudio.mobile.adapter;

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

        // Indent
        int indentDp = node.depth * 16;
        float density = h.b.getRoot().getContext().getResources().getDisplayMetrics().density;
        h.b.viewIndent.getLayoutParams().width = (int)(indentDp * density);
        h.b.viewIndent.requestLayout();

        h.b.tvIcon.setText(node.icon());
        h.b.tvFileName.setText(node.name());
        h.b.tvBadge.setText(node.languageBadge());
        h.b.tvBadge.setVisibility(node.languageBadge().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

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
