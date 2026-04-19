package com.yay.tripsync;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;

import com.yay.tripsync.R;

import java.util.*;

public class ChatFragment extends Fragment {

    RecyclerView recyclerView;
    EditText input;
    Button sendBtn;

    List<Message> messageList;
    ChatAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        recyclerView = view.findViewById(R.id.chatRecyclerView);
        input = view.findViewById(R.id.messageInput);
        sendBtn = view.findViewById(R.id.sendBtn);

        messageList = new ArrayList<>();
        adapter = new ChatAdapter(messageList);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString();

            if (!text.isEmpty()) {
                messageList.add(new Message(text, true));
                adapter.notifyItemInserted(messageList.size() - 1);
                recyclerView.scrollToPosition(messageList.size() - 1);
                input.setText("");
            }
        });

        return view;
    }
}