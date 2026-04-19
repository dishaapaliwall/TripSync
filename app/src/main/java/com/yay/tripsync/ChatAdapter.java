package com.yay.tripsync;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;

import com.yay.tripsync.R;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    List<Message> messages;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        public ViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.messageText);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Message msg = messages.get(position);
        holder.text.setText(msg.text);

        // Align left/right
        if (msg.isSentByMe) {
            holder.text.setBackgroundResource(R.drawable.sent_bg);
            ((LinearLayout) holder.text.getParent()).setGravity(Gravity.END);
        } else {
            holder.text.setBackgroundResource(R.drawable.receive_bg);
            ((LinearLayout) holder.text.getParent()).setGravity(Gravity.START);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}