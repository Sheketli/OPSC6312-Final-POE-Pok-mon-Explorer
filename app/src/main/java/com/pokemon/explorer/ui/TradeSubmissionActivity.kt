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
import com.pokemon.explorer.PokemonApplication
import com.pokemon.explorer.data.CollectionPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TradeSubmissionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTradeSubmissionBinding
    private val appState: AppState by viewModels()
    private lateinit var socialRepository: SocialRepository
    private lateinit var tradeAdapter: TradeRequestsAdapter
    private var currentUsername = ""
    private var currentUid = ""
    private var friendsList = emptyList<String>()
    private var caughtPokemonList = emptyList<Int>()
    private var observationJob: Job? = null
    private var actionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTradeSubmissionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        socialRepository = (application as PokemonApplication).socialRepository()
        binding.btnBackFromTrade.setOnClickListener { finish() }

        tradeAdapter = TradeRequestsAdapter(
            currentUsername = "",
            onAccept = { trade ->
                if (trade.tradeStatus == "PENDING") chooseReturnPokemon(trade)
                else confirmExchange(trade)
            },
            onDecline = { trade ->
                runTradeAction {
                    socialRepository.declineTradeRequest(trade.tradeId)
                    showMessage("Proposal closed.")
                }
            }
        )
        binding.rvIncomingTradeRequests.layoutManager = LinearLayoutManager(this)
        binding.rvIncomingTradeRequests.adapter = tradeAdapter

        appState.currentUser.observe(this) { user ->
            currentUsername = user?.username.orEmpty()
            currentUid = user?.userId.orEmpty()
            tradeAdapter.updateCurrentUser(currentUsername)
            observeTrading()
        }
        appState.caught.observe(this) { updateCollectionChoices() }

        binding.btnSubmitTradeProposal.setOnClickListener {
            val friend = friendsList.getOrNull(binding.spFriendPartner.selectedItemPosition)
            val offered = caughtPokemonList.getOrNull(binding.spPokemonOffer.selectedItemPosition)
            if (friend == null || offered == null) {
                showMessage("Choose an accepted friend and an available Pokémon from your collection.")
                return@setOnClickListener
            }
            val note = binding.etOptionalTradeDescription.text.toString().trim()
            runTradeAction {
                socialRepository.sendTradeRequest(currentUsername, friend, offered.toString(), note.ifBlank { null })
                binding.etOptionalTradeDescription.text.clear()
                showMessage("Proposal sent. Your friend can choose a Pokémon to offer in return.")
            }
        }
    }

    private fun observeTrading() {
        observationJob?.cancel()
        if (currentUid.isBlank()) return
        observationJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    socialRepository.observeFriendships(currentUsername).collect { friendships ->
                        val selected = friendsList.getOrNull(binding.spFriendPartner.selectedItemPosition)
                            ?: intent.getStringExtra("TRADE_PARTNER")
                        friendsList = friendships.filter { it.status == "ACCEPTED" }.map {
                            if (it.userOne.equals(currentUsername, true)) it.userTwo else it.userOne
                        }.distinct()
                        binding.spFriendPartner.adapter = ArrayAdapter(
                            this@TradeSubmissionActivity, android.R.layout.simple_spinner_dropdown_item,
                            friendsList.ifEmpty { listOf("No accepted friends available") }
                        )
                        val index = friendsList.indexOfFirst { it.equals(selected, true) }
                        if (index >= 0) binding.spFriendPartner.setSelection(index)
                    }
                }
                launch {
                    socialRepository.observeTradeRequests().collect { requests ->
                        tradeAdapter.submitList(requests)
                        updateCollectionChoices()
                    }
                }
                launch {
                    socialRepository.tradeSyncError.collect { it?.let { message -> showMessage(message) } }
                }
            }
        }
    }

    private fun updateCollectionChoices() {
        if (currentUid.isBlank()) return
        val selected = caughtPokemonList.getOrNull(binding.spPokemonOffer.selectedItemPosition)
            ?: intent.getStringExtra("OFFERED_POKEMON_ID")?.toIntOrNull()
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        caughtPokemonList = CollectionPreferences.caught(prefs, currentUid)
            .minus(CollectionPreferences.reserved(prefs, currentUid)).sorted()
        binding.spPokemonOffer.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            caughtPokemonList.map { "Pokémon #$it" }.ifEmpty { listOf("No available Pokémon") }
        )
        val index = caughtPokemonList.indexOf(selected)
        if (index >= 0) binding.spPokemonOffer.setSelection(index)
    }

    private fun chooseReturnPokemon(trade: TradeRequestEntity) {
        val prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE)
        val choices = CollectionPreferences.caught(prefs, currentUid)
            .minus(CollectionPreferences.reserved(prefs, currentUid)).sorted()
        if (choices.isEmpty()) {
            showMessage("Catch a Pokémon or cancel another proposal before choosing a return offer.")
            return
        }
        var selected = 0
        MaterialAlertDialogBuilder(this)
            .setTitle("Offer in return for Pokémon #${trade.offeredPokemonId}")
            .setSingleChoiceItems(choices.map { "Pokémon #$it" }.toTypedArray(), 0) { _, index -> selected = index }
            .setPositiveButton("Send return offer") { _, _ ->
                runTradeAction {
                    socialRepository.counterTradeRequest(trade.tradeId, choices[selected])
                    showMessage("Return offer sent. Waiting for ${trade.senderUsername} to confirm.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmExchange(trade: TradeRequestEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm exchange")
            .setMessage("Exchange your Pokémon #${trade.offeredPokemonId} for ${trade.receiverUsername}'s Pokémon #${trade.returnedPokemonId}?")
            .setPositiveButton("Exchange") { _, _ ->
                runTradeAction {
                    socialRepository.acceptTradeRequest(trade.tradeId)
                    showMessage("Exchange confirmed. Both collections will update.")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runTradeAction(action: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = lifecycleScope.launch {
            binding.btnSubmitTradeProposal.isEnabled = false
            try {
                action()
            } catch (e: TimeoutCancellationException) {
                showMessage("Could not confirm the trade update. Check its status before retrying.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(e.localizedMessage ?: "Could not update the proposal.")
            } finally {
                binding.btnSubmitTradeProposal.isEnabled = true
            }
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
            val canChoose = isIncoming && trade.tradeStatus == "PENDING"
            val canConfirm = !isIncoming && trade.tradeStatus == "COUNTERED"
            val isOpen = trade.tradeStatus in listOf("PENDING", "COUNTERED")
            val pokeId = trade.offeredPokemonId.toIntOrNull()

            binding.ivTradePokemonSprite.load(pokeId?.let { PokeApi.artworkUrl(it) })

            if (isIncoming) {
                binding.tvTradeTitle.text = "From: ${trade.senderUsername}"
            } else {
                binding.tvTradeTitle.text = "To: ${trade.receiverUsername}"
            }

            binding.tvTradePokemonDetail.text = if (trade.returnedPokemonId != null) {
                "Offered Pokémon #$pokeId ↔ Pokémon #${trade.returnedPokemonId}"
            } else "Offered Pokémon #$pokeId · Awaiting a return offer"
            if (!trade.optionalDescription.isNull_or_blank()) {
                binding.tvTradeNote.visibility = View.VISIBLE
                binding.tvTradeNote.text = "Note: ${trade.optionalDescription}"
            } else {
                binding.tvTradeNote.visibility = View.GONE
            }

            binding.tvTradeStatusBadge.visibility = View.VISIBLE
            binding.tvTradeStatusBadge.text = when (trade.tradeStatus) {
                "PENDING" -> "Waiting for a return offer"
                "COUNTERED" -> "Waiting for sender confirmation"
                "ACCEPTED" -> "Exchange completed"
                else -> "Proposal closed"
            }
            binding.btnAcceptTrade.visibility = if (canChoose || canConfirm) View.VISIBLE else View.GONE
            binding.btnAcceptTrade.text = if (canChoose) "Choose Pokémon" else "Confirm exchange"
            binding.btnAcceptTrade.setOnClickListener { onAccept(trade) }
            binding.btnDeclineTrade.visibility = if (isOpen) View.VISIBLE else View.GONE
            binding.btnDeclineTrade.text = if (isIncoming && canChoose) "Decline" else "Cancel proposal"
            binding.btnDeclineTrade.setOnClickListener { onDecline(trade) }
        }

        private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
    }
}
