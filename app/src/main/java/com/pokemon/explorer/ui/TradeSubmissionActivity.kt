package com.pokemon.explorer.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.db.TradeRequestEntity
import com.pokemon.explorer.data.repository.SocialRepository
import com.pokemon.explorer.databinding.ActivityTradeSubmissionBinding
import com.pokemon.explorer.databinding.ItemTradeRequestRowBinding
import com.pokemon.explorer.state.AppState
import kotlinx.coroutines.launch

class TradeSubmissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTradeSubmissionBinding
    private val appState: AppState by viewModels()
    private lateinit var socialRepository: SocialRepository
    private lateinit var tradeAdapter: TradeRequestsAdapter
    private var currentUsername: String = ""
    private var friendsList = listOf<String>()
    private var caughtPokemonList = listOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTradeSubmissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        socialRepository = SocialRepository(this)
        binding.btnBackFromTrade.setOnClickListener { finish() }

        tradeAdapter = TradeRequestsAdapter(
            currentUsername = "",
            onAccept = { trade ->
                lifecycleScope.launch {
                    val pokeId = trade.offeredPokemonId.toIntOrNull() ?: 1
                    appState.addCaught(pokeId)
                    socialRepository.acceptTradeRequest(
                        tradeId = trade.tradeId,
                        recipientUsername = currentUsername,
                        senderUsername = trade.senderUsername,
                        pokemonId = trade.offeredPokemonId
                    )
                    Toast.makeText(this@TradeSubmissionActivity, "Trade accepted! Pokémon #$pokeId added to your collection!", Toast.LENGTH_LONG).show()
                    loadTradeRequests()
                }
            },
            onDecline = { trade ->
                lifecycleScope.launch {
                    socialRepository.declineTradeRequest(trade.tradeId)
                    Toast.makeText(this@TradeSubmissionActivity, "Trade request declined.", Toast.LENGTH_SHORT).show()
                    loadTradeRequests()
                }
            }
        )

        binding.rvIncomingTradeRequests.layoutManager = LinearLayoutManager(this)
        binding.rvIncomingTradeRequests.adapter = tradeAdapter

        appState.currentUser.observe(this) { user ->
            currentUsername = user?.username ?: ""
            tradeAdapter.updateCurrentUser(currentUsername)
            loadFriendsAndCollection()
            loadTradeRequests()
        }

        // Handle pre-selected partner from Intent if any
        val preselectedPartner = intent.getStringExtra("TRADE_PARTNER")
        val preselectedPokemonId = intent.getStringExtra("OFFERED_POKEMON_ID")

        binding.btnSubmitTradeProposal.setOnClickListener {
            val selectedFriendIndex = binding.spFriendPartner.selectedItemPosition
            val selectedPokemonIndex = binding.spPokemonOffer.selectedItemPosition

            if (friendsList.isEmpty() || selectedFriendIndex < 0 || selectedFriendIndex >= friendsList.size) {
                Toast.makeText(this, "Please add a friend first before trading!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (caughtPokemonList.isEmpty() || selectedPokemonIndex < 0 || selectedPokemonIndex >= caughtPokemonList.size) {
                Toast.makeText(this, "You need to catch at least one Pokémon to make a trade!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val targetFriend = friendsList[selectedFriendIndex]
            val pokemonIdToOffer = caughtPokemonList[selectedPokemonIndex].toString()
            val description = binding.etOptionalTradeDescription.text.toString().trim()

            lifecycleScope.launch {
                val success = socialRepository.sendTradeRequest(
                    senderUsername = currentUsername,
                    receiverUsername = targetFriend,
                    offeredPokemonId = pokemonIdToOffer,
                    optionalDescription = if (description.isNotBlank()) description else null
                )
                if (success) {
                    Toast.makeText(this@TradeSubmissionActivity, "Trade proposal dispatched to $targetFriend successfully!", Toast.LENGTH_LONG).show()
                    binding.etOptionalTradeDescription.text.clear()
                    loadTradeRequests()
                } else {
                    Toast.makeText(this@TradeSubmissionActivity, "Failed to send trade proposal.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadFriendsAndCollection() {
        if (currentUsername.isBlank()) return
        lifecycleScope.launch {
            val friendships = socialRepository.getFriendships(currentUsername).filter { it.status == "ACCEPTED" }
            friendsList = friendships.map { if (it.userOne == currentUsername) it.userTwo else it.userOne }

            val friendAdapter = ArrayAdapter(
                this@TradeSubmissionActivity,
                android.R.layout.simple_spinner_item,
                if (friendsList.isNotEmpty()) friendsList else listOf("No Accepted Friends Available")
            )
            friendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spFriendPartner.adapter = friendAdapter

            val preselectedPartner = intent.getStringExtra("TRADE_PARTNER")
            if (!preselectedPartner.isNull_or_empty()) {
                val index = friendsList.indexOfFirst { it.equals(preselectedPartner, ignoreCase = true) }
                if (index >= 0) {
                    binding.spFriendPartner.setSelection(index)
                }
            }

            // Load caught pokemons
            val caughtSet = appState.caught.value ?: setOf(25, 1, 6, 7, 4)
            caughtPokemonList = caughtSet.toList()
            val pokemonLabels = caughtPokemonList.map { "Pokémon #$it" }

            val pokeSpinnerAdapter = ArrayAdapter(
                this@TradeSubmissionActivity,
                android.R.layout.simple_spinner_item,
                if (pokemonLabels.isNotEmpty()) pokemonLabels else listOf("No Pokémons Caught")
            )
            pokeSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spPokemonOffer.adapter = pokeSpinnerAdapter
        }
    }

    private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()

    private fun loadTradeRequests() {
        if (currentUsername.isBlank()) return
        lifecycleScope.launch {
            val requests = socialRepository.getTradeRequestsForUser(currentUsername)
            tradeAdapter.submitList(requests)
        }
    }
}

class TradeRequestsAdapter(
    private var currentUsername: String,
    private val onAccept: (TradeRequestEntity) -> Unit,
    private val onDecline: (TradeRequestEntity) -> Unit
) : RecyclerView.Adapter<TradeRequestsAdapter.TradeViewHolder>() {

    private var items = listOf<TradeRequestEntity>()

    fun updateCurrentUser(user: String) {
        currentUsername = user
    }

    fun submitList(newList: List<TradeRequestEntity>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
        val binding = ItemTradeRequestRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TradeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TradeViewHolder(private val binding: ItemTradeRequestRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(trade: TradeRequestEntity) {
            val isIncoming = trade.receiverUsername.equals(currentUsername, ignoreCase = true)
            val isPending = trade.tradeStatus == "PENDING"
            val pokeId = trade.offeredPokemonId.toIntOrNull() ?: 1

            binding.ivTradePokemonSprite.load(PokeApi.artworkUrl(pokeId))

            if (isIncoming) {
                binding.tvTradeTitle.text = "From: ${trade.senderUsername}"
            } else {
                binding.tvTradeTitle.text = "To: ${trade.receiverUsername}"
            }

            binding.tvTradePokemonDetail.text = "Offered: Pokémon #$pokeId"
            if (!trade.optionalDescription.isNull_or_blank()) {
                binding.tvTradeNote.visibility = View.VISIBLE
                binding.tvTradeNote.text = "Note: ${trade.optionalDescription}"
            } else {
                binding.tvTradeNote.visibility = View.GONE
            }

            if (isIncoming && isPending) {
                binding.tvTradeStatusBadge.visibility = View.GONE
                binding.btnAcceptTrade.visibility = View.VISIBLE
                binding.btnDeclineTrade.visibility = View.VISIBLE
                binding.btnAcceptTrade.setOnClickListener { onAccept(trade) }
                binding.btnDeclineTrade.setOnClickListener { onDecline(trade) }
            } else {
                binding.btnAcceptTrade.visibility = View.GONE
                binding.btnDeclineTrade.visibility = View.GONE
                binding.tvTradeStatusBadge.visibility = View.VISIBLE
                binding.tvTradeStatusBadge.text = "Status: ${trade.tradeStatus}"
            }
        }

        private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
    }
}
