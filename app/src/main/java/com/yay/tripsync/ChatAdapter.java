package com.yay.tripsync;

import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import de.hdodenhof.circleimageview.CircleImageView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<Message> messages;
    private String currentUserId;
    private SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    private int[] avatars = {
            R.drawable.panda, R.drawable.jaguar, R.drawable.ganesha,
            R.drawable.cow, R.drawable.cat, R.drawable.bird, R.drawable.apteryx
    };

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? 
                             FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView myText, myTime, otherText, otherName, otherTime;
        CircleImageView otherAvatar;
        LinearLayout myMessageRoot, otherMessageRoot;

        public ViewHolder(View itemView) {
            super(itemView);
            myText = itemView.findViewById(R.id.myText);
            myTime = itemView.findViewById(R.id.myTime);
            otherText = itemView.findViewById(R.id.otherText);
            otherName = itemView.findViewById(R.id.otherName);
            otherTime = itemView.findViewById(R.id.otherTime);
            otherAvatar = itemView.findViewById(R.id.otherAvatar);
            myMessageRoot = itemView.findViewById(R.id.myMessageRoot);
            otherMessageRoot = itemView.findViewById(R.id.otherMessageRoot);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message msg = messages.get(position);
        String timeStr = "";
        if (msg.getTimestamp() != null) {
            timeStr = sdf.format(msg.getTimestamp().toDate());
        }

        if (msg.getSenderId().equals(currentUserId)) {
            holder.myMessageRoot.setVisibility(View.VISIBLE);
            holder.otherMessageRoot.setVisibility(View.GONE);
            holder.myText.setText(msg.getText());
            holder.myTime.setText(timeStr);
        } else {
            holder.myMessageRoot.setVisibility(View.GONE);
            holder.otherMessageRoot.setVisibility(View.VISIBLE);
            holder.otherText.setText(msg.getText());
            holder.otherName.setText(msg.getSenderName());
            holder.otherTime.setText(timeStr);

            // Avatar logic
            int avatarIndex = Math.abs(msg.getSenderId().hashCode()) % avatars.length;
            holder.otherAvatar.setImageResource(avatars[avatarIndex]);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
}
