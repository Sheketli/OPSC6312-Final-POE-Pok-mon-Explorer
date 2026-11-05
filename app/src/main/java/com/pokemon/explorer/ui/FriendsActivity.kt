package com.pokemon.explorer.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pokemon.explorer.data.repository.SocialRepository
import com.pokemon.explorer.databinding.ActivityFriendsBinding
import com.pokemon.explorer.databinding.ItemFriendRowBinding
import com.pokemon.explorer.state.AppState
import com.pokemon.explorer.PokemonApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect

class FriendsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendsBinding
    private val appState: AppState by viewModels()
    private lateinit var socialRepository: SocialRepository
    private lateinit var friendsAdapter: FriendsAdapter
    private var currentUsername: String = ""
    private var listJob: Job? = null
    private var actionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        socialRepository = (application as PokemonApplication).socialRepository()

        binding.btnBackFromFriends.setOnClickListener { finish() }

        friendsAdapter = FriendsAdapter(
            onAccept = { friendUsername ->
                runFriendAction {
                    socialRepository.acceptFriendRequest(currentUsername, friendUsername)
                    Toast.makeText(this@FriendsActivity, "Friend request accepted!", Toast.LENGTH_SHORT).show()
                    loadFriends()
                }
            },
            onDecline = { friendUsername ->
                runFriendAction {
                    socialRepository.declineFriendRequest(currentUsername, friendUsername)
                    Toast.makeText(this@FriendsActivity, "Friend request declined.", Toast.LENGTH_SHORT).show()
                    loadFriends()
                }
            },
            onCancelRequest = { friendUsername ->
                runFriendAction {
                    socialRepository.cancelFriendRequest(currentUsername, friendUsername)
                    Toast.makeText(this@FriendsActivity, "Friend request unrequested.", Toast.LENGTH_SHORT).show()
                    loadFriends()
                }
            },
            onRemove = { friendUsername ->
                runFriendAction {
                    socialRepository.removeFriend(currentUsername, friendUsername)
                    Toast.makeText(this@FriendsActivity, "Removed $friendUsername from friends.", Toast.LENGTH_SHORT).show()
                    loadFriends()
                }
            },
            onMessage = { friendUsername ->
                val intent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("TARGET_FRIEND", friendUsername)
                }
                startActivity(intent)
            },
            onTrade = { friendUsername ->
                val intent = Intent(this, TradeSubmissionActivity::class.java).apply {
                    putExtra("TRADE_PARTNER", friendUsername)
                }
                startActivity(intent)
            },
            onAddFriend = { targetUsername ->
                runFriendAction {
                    val success = socialRepository.sendFriendRequest(currentUsername, targetUsername)
                    if (success) {
                        Toast.makeText(this@FriendsActivity, "Friend request sent to $targetUsername!", Toast.LENGTH_SHORT).show()
                        binding.etFriendSearchInput.text.clear()
                        loadFriends()
                    } else {
                        Toast.makeText(this@FriendsActivity, "Already friends or pending request exists.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        binding.rvFriendsList.layoutManager = LinearLayoutManager(this)
        binding.rvFriendsList.adapter = friendsAdapter

        appState.currentUser.observe(this) { user ->
            currentUsername = user?.username ?: ""
            loadFriends()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                socialRepository.friendshipSyncError.collect { message ->
                    if (message != null) {
                        Toast.makeText(this@FriendsActivity, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnSendFriendRequest.setOnClickListener {
            val query = binding.etFriendSearchInput.text.toString().trim()
            if (query.isNotEmpty() && currentUsername.isNotEmpty()) {
                runFriendAction {
                    val success = socialRepository.sendFriendRequest(currentUsername, query)
                    if (success) {
                        Toast.makeText(this@FriendsActivity, "Friend request sent to $query!", Toast.LENGTH_SHORT).show()
                        binding.etFriendSearchInput.text.clear()
                        loadFriends()
                    } else {
                        Toast.makeText(this@FriendsActivity, "Could not send friend request to $query.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.etFriendSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    searchUsers(query)
                } else {
                    loadFriends()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun runFriendAction(action: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = lifecycleScope.launch {
            binding.btnSendFriendRequest.isEnabled = false
            try {
                action()
            } catch (e: TimeoutCancellationException) {
                Toast.makeText(this@FriendsActivity,
                    "Could not confirm the request. Check your connection and refresh before retrying.",
                    Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@FriendsActivity,
                    e.localizedMessage ?: "Could not update the friend request.",
                    Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSendFriendRequest.isEnabled = true
            }
        }
    }

    private fun loadFriends() {
        listJob?.cancel()
        if (currentUsername.isBlank()) return
        listJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                socialRepository.observeFriendships(currentUsername).collect { list ->
                    val items = list.map { entity ->
                        val friendName = if (entity.userOne.equals(currentUsername, true)) entity.userTwo else entity.userOne
                        val isReceived = entity.userTwo.equals(currentUsername, true) && entity.status == "PENDING_SENDER_ONE"
                        val isSent = entity.userOne.equals(currentUsername, true) && entity.status == "PENDING_SENDER_ONE"
                        FriendItem(
                            username = friendName,
                            status = entity.status,
                            isPendingReceived = isReceived,
                            isPendingSent = isSent,
                            isAccepted = entity.status == "ACCEPTED",
                            isSearchResult = false
                        )
                    }.sortedWith(compareByDescending<FriendItem> { it.isPendingReceived }.thenByDescending { it.isPendingSent })

                    if (items.isEmpty()) {
                        binding.tvEmptyFriendsMessage.visibility = View.VISIBLE
                        binding.tvEmptyFriendsMessage.text = "No friends or pending requests yet.\nSearch a trainer username above to send a request!"
                        binding.rvFriendsList.visibility = View.GONE
                    } else {
                        binding.tvEmptyFriendsMessage.visibility = View.GONE
                        binding.rvFriendsList.visibility = View.VISIBLE
                    }
                    friendsAdapter.submitList(items)
                }
            }
        }
    }

    private fun searchUsers(query: String) {
        listJob?.cancel()
        if (currentUsername.isBlank()) return
        listJob = lifecycleScope.launch {
            val foundUsers = try {
                socialRepository.searchUsers(query).filter { !it.username.equals(currentUsername, ignoreCase = true) }
            } catch (e: TimeoutCancellationException) {
                showSearchError("Trainer search timed out. Check your connection.")
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showSearchError("Could not search trainers: ${e.localizedMessage}")
                return@launch
            }
            socialRepository.observeFriendships(currentUsername).collect { existingFriendships ->

                val items = foundUsers.map { user ->
                    val existing = existingFriendships.find { it.userOne.equals(user.username, ignoreCase = true) || it.userTwo.equals(user.username, ignoreCase = true) }
                    val isAccepted = existing?.status == "ACCEPTED"
                    val isSent = existing?.userOne.equals(currentUsername, true) && existing?.status == "PENDING_SENDER_ONE"
                    val isReceived = existing?.userTwo.equals(currentUsername, true) && existing?.status == "PENDING_SENDER_ONE"

                    FriendItem(
                        username = user.username,
                        status = existing?.status ?: "NONE",
                        isPendingReceived = isReceived,
                        isPendingSent = isSent,
                        isAccepted = isAccepted,
                        isSearchResult = existing == null
                    )
                }.sortedWith(compareByDescending<FriendItem> { it.isPendingReceived }.thenByDescending { it.isPendingSent })

                if (items.isEmpty()) {
                    binding.tvEmptyFriendsMessage.visibility = View.VISIBLE
                    binding.tvEmptyFriendsMessage.text = "No trainer found matching \"$query\""
                    binding.rvFriendsList.visibility = View.GONE
                } else {
                    binding.tvEmptyFriendsMessage.visibility = View.GONE
                    binding.rvFriendsList.visibility = View.VISIBLE
                }
                friendsAdapter.submitList(items)
            }
        }
    }

    private fun showSearchError(message: String) {
        binding.tvEmptyFriendsMessage.visibility = View.VISIBLE
        binding.tvEmptyFriendsMessage.text = message
        binding.rvFriendsList.visibility = View.GONE
    }
}

data class FriendItem(
    val username: String,
    val status: String,
    val isPendingReceived: Boolean,
    val isPendingSent: Boolean,
    val isAccepted: Boolean,
    val isSearchResult: Boolean
)

class FriendsAdapter(
    private val onAccept: (String) -> Unit,
    private val onDecline: (String) -> Unit,
    private val onCancelRequest: (String) -> Unit,
    private val onRemove: (String) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onTrade: (String) -> Unit,
    private val onAddFriend: (String) -> Unit
) : RecyclerView.Adapter<FriendsAdapter.FriendViewHolder>() {

    private var items = listOf<FriendItem>()

    fun submitList(newList: List<FriendItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class FriendViewHolder(private val binding: ItemFriendRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FriendItem) {
            binding.tvFriendUsername.text = item.username

            binding.btnPrimaryAction.visibility = View.GONE
            binding.btnSecondaryAction.visibility = View.GONE
            binding.btnRemoveAction.visibility = View.GONE

            if (item.isSearchResult) {
                binding.tvFriendStatus.text = "Trainer Account"
                binding.btnPrimaryAction.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = "ADD FRIEND"
                binding.btnPrimaryAction.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E3350D"))
                binding.btnPrimaryAction.setOnClickListener { onAddFriend(item.username) }
            } else if (item.isPendingReceived) {
                binding.tvFriendStatus.text = "Sent you a friend request!"
                binding.btnPrimaryAction.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = "ACCEPT REQUEST"
                binding.btnPrimaryAction.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
                binding.btnPrimaryAction.setOnClickListener { onAccept(item.username) }

                binding.btnSecondaryAction.visibility = View.VISIBLE
                binding.btnSecondaryAction.text = "DECLINE"
                binding.btnSecondaryAction.setOnClickListener { onDecline(item.username) }
            } else if (item.isPendingSent) {
                binding.tvFriendStatus.text = "Friend request pending..."
                binding.btnPrimaryAction.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = "UNREQUEST"
                binding.btnPrimaryAction.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#757575"))
                binding.btnPrimaryAction.setOnClickListener { onCancelRequest(item.username) }
            } else if (item.isAccepted) {
                binding.tvFriendStatus.text = "Friend"
                binding.btnPrimaryAction.visibility = View.VISIBLE
                binding.btnPrimaryAction.text = "MESSAGE"
                binding.btnPrimaryAction.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E3350D"))
                binding.btnPrimaryAction.setOnClickListener { onMessage(item.username) }

                binding.btnSecondaryAction.visibility = View.VISIBLE
                binding.btnSecondaryAction.text = "TRADE"
                binding.btnSecondaryAction.setOnClickListener { onTrade(item.username) }

                binding.btnRemoveAction.visibility = View.VISIBLE
                binding.btnRemoveAction.setOnClickListener { onRemove(item.username) }
            }
        }
    }
}
