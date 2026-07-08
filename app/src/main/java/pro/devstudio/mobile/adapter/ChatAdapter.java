package pro.devstudio.mobile.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.devstudio.mobile.databinding.ItemChatAiBinding;
import pro.devstudio.mobile.databinding.ItemChatUserBinding;
import pro.devstudio.mobile.model.ChatMessage;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI   = 1;

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int pos) {
        return messages.get(pos).role == ChatMessage.ROLE_USER ? TYPE_USER : TYPE_AI;
    }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserVH(ItemChatUserBinding.inflate(inf, parent, false));
        } else {
            return new AiVH(ItemChatAiBinding.inflate(inf, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
        ChatMessage msg = messages.get(pos);
        
        if (holder instanceof UserVH u) {
            // User Chat Bubble Styling
            u.b.tvMessage.setText(msg.content);
            u.b.tvMessage.setBackgroundColor(Color.parseColor("#E3F2FD")); // Light Blue Bubble
            u.b.tvMessage.setTextColor(Color.parseColor("#1565C0"));
            
        } else if (holder instanceof AiVH a) {
            // AI Chat Bubble Styling
            if (msg.isLoading) {
                a.b.progressBar.setVisibility(View.VISIBLE);
                a.b.tvMessage.setVisibility(View.GONE);
            } else {
                a.b.progressBar.setVisibility(View.GONE);
                a.b.tvMessage.setVisibility(View.VISIBLE);
                a.b.tvMessage.setText(msg.content);
                a.b.tvMessage.setTextColor(Color.parseColor("#212121"));
                // AI ရဲ့ စာသားကို အနည်းငယ်ပိုရှင်းအောင် လုပ်ပေးထားသည်
            }
        }
    }

    @Override public int getItemCount() { return messages.size(); }

    // User View Holder
    static class UserVH extends RecyclerView.ViewHolder {
        final ItemChatUserBinding b;
        UserVH(ItemChatUserBinding b) { super(b.getRoot()); this.b = b; }
    }

    // AI View Holder
    static class AiVH extends RecyclerView.ViewHolder {
        final ItemChatAiBinding b;
        AiVH(ItemChatAiBinding b) { super(b.getRoot()); this.b = b; }
    }
}
