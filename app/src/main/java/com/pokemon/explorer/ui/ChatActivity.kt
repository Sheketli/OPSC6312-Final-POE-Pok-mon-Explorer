package com.pokemon.explorer.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pokemon.explorer.data.db.MessageEntity
import com.pokemon.explorer.data.repository.SocialRepository
import com.pokemon.explorer.databinding.ActivityChatViewBinding
import com.pokemon.explorer.databinding.ItemChatMessageBinding
import com.pokemon.explorer.state.AppState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatViewBinding
    private val appState: AppState by viewModels()
    private lateinit var socialRepository: SocialRepository
    private lateinit var chatAdapter: ChatAdapter
    private var targetFriendUsername: String = ""
    private var currentUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        socialRepository = SocialRepository(this)
        targetFriendUsername = intent.getStringExtra("TARGET_FRIEND") ?: "Trainer"

        binding.tvChatHeaderName.text = targetFriendUsername
        binding.btnBackFromChat.setOnClickListener { finish() }

        chatAdapter = ChatAdapter(currentUsername)
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.rvMessageList.layoutManager = layoutManager
        binding.rvMessageList.adapter = chatAdapter

        appState.currentUser.observe(this) { user ->
            currentUsername = user?.username ?: ""
            chatAdapter.updateCurrentUser(currentUsername)
            loadMessages()
        }

        binding.btnSendMessage.setOnClickListener {
            val text = binding.etMessageInput.text.toString().trim()
            if (text.isNotEmpty() && currentUsername.isNotEmpty()) {
                lifecycleScope.launch {
                    val success = socialRepository.sendMessage(currentUsername, targetFriendUsername, text)
                    if (success) {
                        binding.etMessageInput.text.clear()
                        loadMessages()
                        Toast.makeText(this@ChatActivity, "Message delivered securely.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadMessages() {
        if (currentUsername.isBlank() || targetFriendUsername.isBlank()) return
        lifecycleScope.launch {
            val messages = socialRepository.getMessagesBetween(currentUsername, targetFriendUsername)
            chatAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessageList.smoothScrollToPosition(messages.size - 1)
            }
        }
    }
}

class ChatAdapter(private var currentUser: String) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private var messages = listOf<MessageEntity>()
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun updateCurrentUser(user: String) {
        currentUser = user
    }

    fun submitList(newList: List<MessageEntity>) {
        messages = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(private val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: MessageEntity) {
            binding.tvMessageContent.text = msg.messageText
            binding.tvTimestamp.text = dateFormat.format(Date(msg.timestamp))

            val isMe = msg.senderUsername.equals(currentUser, ignoreCase = true)
            val params = binding.cardBubble.layoutParams as LinearLayout.LayoutParams

            if (isMe) {
                binding.llMessageContainer.gravity = Gravity.END
                params.gravity = Gravity.END
                params.setMargins(100, 4, 12, 4)
                binding.cardBubble.layoutParams = params
                binding.cardBubble.setCardBackgroundColor(Color.parseColor("#E2FFC7"))
                binding.tvSenderName.visibility = View.GONE
            } else {
                binding.llMessageContainer.gravity = Gravity.START
                params.gravity = Gravity.START
                params.setMargins(12, 4, 100, 4)
                binding.cardBubble.layoutParams = params
                binding.cardBubble.setCardBackgroundColor(Color.WHITE)
                binding.tvSenderName.visibility = View.VISIBLE
                binding.tvSenderName.text = msg.senderUsername
            }
        }
    }
}
