package pro.devstudio.mobile.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
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

        // ── 📁 PC DASHBOARD COLOR & TEXT STYLE ──────────────────────────────
        h.b.tvProjectName.setText(p.name);
        h.b.tvProjectName.setTypeface(null, Typeface.BOLD); // နာမည်ကို စာလုံးအထူပြောင်းသည်
        
        h.b.tvPackageName.setText(p.packageName);
        h.b.tvLastModified.setText(p.relativeTime());
        
        // Template Badge အား အရောင်နှင့် Icon ပေါင်းစပ်ပြီး ပိုမိုလှပအောင် ဖန်တီးခြင်း
        h.b.tvTemplate.setText(templateBadge(p.template));
        h.b.tvTemplate.setTextColor(Color.parseColor(templateColor(p.template)));

        // 🎨 Accent Border & Folder Text Icon Binding
        String targetAccent = "#CBA6F7"; // Default Latte Purple
        if (p.accentColor != null && !p.accentColor.isBlank()) {
            targetAccent = p.accentColor;
        }

        try {
            int parsedColor = Color.parseColor(targetAccent);
            h.b.accentBorder.setBackgroundColor(parsedColor);
            
            // 💡 အကယ်၍ သင့် layout (ItemProjectBinding) ထဲမှာ tvProjectIcon သို့မဟုတ် tvFolderIcon ပါခဲ့ရင် 
            // ပရောဂျက်ရဲ့ ပထမဆုံးစာလုံးကို Accent color နဲ့ လှလှပပ ပြသနိုင်ဖို့ ကုဒ်ထည့်ပေးထားပါတယ် (မပါရင် macro က ကျော်သွားပါမယ်)
            // Example: h.b.tvProjectIcon.setTextColor(parsedColor);
        } catch (Exception e) {
            h.b.accentBorder.setBackgroundColor(Color.parseColor("#CBA6F7"));
        }

        // Click Systems
        h.b.getRoot().setOnClickListener(v -> listener.onOpen(p));
        h.b.getRoot().setOnLongClickListener(v -> {
            listener.onDelete(p, h.getAdapterPosition());
            return true;
        });
    }

    @Override public int getItemCount() { return list.size(); }

    /**
     * Template အလိုက် ပေါ်မည့်စာသားနှင့် အိုင်ကွန်အတွဲအစပ်
     */
    private String templateBadge(String t) {
        if (t == null) return "📦 Empty";
        return switch (t.toLowerCase().trim()) {
            case "login" -> "🔑 Login Activity";
            case "basic" -> "📱 Basic Activity";
            case "list"  -> "📜 List View Activity";
            default      -> "📦 Empty Project";
        };
    }

    /**
     * Template Badge များအတွက် PC IDE စတိုင် သီးသန့်အရောင်များ
     */
    private String templateColor(String t) {
        if (t == null) return "#757575";
        return switch (t.toLowerCase().trim()) {
            case "login" -> "#E67E22"; // Orange
            case "basic" -> "#2ECC71"; // Emerald Green
            case "list"  -> "#3498DB"; // Dodger Blue
            default      -> "#7F8C8D"; // Asbestos Gray
        };
    }

    static class VH extends RecyclerView.ViewHolder {
        final ItemProjectBinding b;
        VH(ItemProjectBinding b) { super(b.getRoot()); this.b = b; }
    }
}
