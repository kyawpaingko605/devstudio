package pro.devstudio.mobile.adapter;

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

    private final List<String> fileNames;   // display names
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

        h.b.tvTabName.setText(name);
        h.b.tabIndicator.setVisibility(active
                ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        h.b.tvTabName.setAlpha(active ? 1f : 0.55f);

        h.b.getRoot().setOnClickListener(v -> {
            setActive(h.getAdapterPosition());
            listener.onTabSelected(h.getAdapterPosition());
        });
        h.b.btnClose.setOnClickListener(v -> listener.onTabClosed(h.getAdapterPosition()));
    }

    @Override public int getItemCount() { return fileNames.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final ItemEditorTabBinding b;
        VH(ItemEditorTabBinding b) { super(b.getRoot()); this.b = b; }
    }
}
