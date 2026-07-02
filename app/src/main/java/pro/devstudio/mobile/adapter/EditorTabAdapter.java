package pro.devstudio.mobile.adapter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.devstudio.mobile.databinding.ItemEditorTabBinding;

public class EditorTabAdapter extends RecyclerView.Adapter<EditorTabAdapter.VH> {

    public interface Listener {
        void onTabSelected(int position);
        void onTabClosed(int position);
    }

    private final List<String> fileNames;
    private final Listener     listener;
    private int                activeIndex;

    public EditorTabAdapter(List<String> fileNames, Listener listener) {
        this.fileNames   = fileNames;
        this.listener    = listener;
        this.activeIndex = 0;
    }

    public void setActive(int index) {
        int old = activeIndex;
        activeIndex = index;
        notifyItemChanged(old);
        notifyItemChanged(activeIndex);
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(ItemEditorTabBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        String name   = fileNames.get(pos);
        boolean active = pos == activeIndex;

        // ── 💻 PC IDE STYLE TAB LOGIC ──────────────────────────────────────
        h.b.tvTabName.setText(name);
        
        // Active ဖြစ်ရင် ပိုမိုထင်ရှားအောင် စာလုံးအထူနှင့် Alpha ပြောင်းခြင်း
        h.b.tvTabName.setAlpha(active ? 1f : 0.6f);
        h.b.tvTabName.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        
        // Tab အောက်က Indicator အရောင်ကို Active ဆိုရင် ပိုတောက်ပေးခြင်း
        h.b.tabIndicator.setVisibility(android.view.View.VISIBLE);
        h.b.tabIndicator.setBackgroundColor(Color.parseColor(active ? "#64B5F6" : "#424242"));

        // 🖼️ ဖိုင်အမျိုးအစားအလိုက် Icon အလိုအလျောက်ပြသခြင်း
        if (h.b.getRoot().findViewById(android.R.id.icon) != null) {
            // သင့် XML ထဲမှာ icon view ပါရင် ဒီမှာ logic ထည့်ပါ
            // h.b.tvIcon.setText(getIconByName(name));
        }

        h.b.getRoot().setOnClickListener(v -> {
            setActive(h.getAdapterPosition());
            listener.onTabSelected(h.getAdapterPosition());
        });
        h.b.btnClose.setOnClickListener(v -> listener.onTabClosed(h.getAdapterPosition()));
    }

    /**
     * PC IDE စတိုင် အိုင်ကွန်များ
     */
    private String getIconByName(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".java")) return "☕";
        if (n.endsWith(".xml")) return "🎨";
        if (n.equals("androidmanifest.xml")) return "⚙️";
        return "📄";
    }

    @Override public int getItemCount() { return fileNames.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemEditorTabBinding b;
        VH(ItemEditorTabBinding b) { super(b.getRoot()); this.b = b; }
    }
}
