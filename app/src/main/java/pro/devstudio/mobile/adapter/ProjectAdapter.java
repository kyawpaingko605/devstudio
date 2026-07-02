package pro.devstudio.mobile.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.devstudio.mobile.databinding.ItemProjectBinding;
import pro.devstudio.mobile.model.Project;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {

    public interface Listener {
        void onOpen(Project p);
        void onDelete(Project p, int position);
    }

    private final List<Project> list;
    private final Listener      listener;

    public ProjectAdapter(List<Project> list, Listener listener) {
        this.list     = list;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemProjectBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Project p = list.get(pos);

        h.b.tvProjectName.setText(p.name);
        h.b.tvPackageName.setText(p.packageName);
        h.b.tvLastModified.setText(p.relativeTime());
        h.b.tvTemplate.setText(templateBadge(p.template));

        // Accent border color
        try {
            h.b.accentBorder.setBackgroundColor(Color.parseColor(p.accentColor));
        } catch (Exception e) {
            h.b.accentBorder.setBackgroundColor(Color.parseColor("#CBA6F7"));
        }

        h.b.getRoot().setOnClickListener(v -> listener.onOpen(p));
        h.b.getRoot().setOnLongClickListener(v -> {
            listener.onDelete(p, h.getAdapterPosition());
            return true;
        });
    }

    @Override public int getItemCount() { return list.size(); }

    private String templateBadge(String t) {
        return switch (t) {
            case "login" -> "Login";
            case "basic" -> "Basic";
            case "list"  -> "List";
            default      -> "Empty";
        };
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemProjectBinding b;
        VH(ItemProjectBinding b) { super(b.getRoot()); this.b = b; }
    }
}
