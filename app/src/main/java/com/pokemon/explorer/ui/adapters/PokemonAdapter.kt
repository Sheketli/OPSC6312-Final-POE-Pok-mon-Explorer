package com.pokemon.explorer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pokemon.explorer.R
import com.pokemon.explorer.data.PokeApi
import com.pokemon.explorer.data.PokemonSummary
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.ItemPokemonCardBinding
import java.util.Locale

class PokemonAdapter(
    private val isFavourite: (Int) -> Boolean,
    private val isCaught: (Int) -> Boolean,
    private val onToggleFavourite: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : ListAdapter<PokemonSummary, PokemonAdapter.PokemonViewHolder>(DiffCallback) {

    class PokemonViewHolder(val binding: ItemPokemonCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PokemonViewHolder {
        val binding = ItemPokemonCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PokemonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PokemonViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        val formattedId = String.format(Locale.US, "#%03d", item.id)
        binding.tvPokemonId.text = formattedId
        binding.tvPokemonName.text = item.name.replaceFirstChar { it.uppercase() }

        binding.ivArtwork.load(PokeApi.artworkUrl(item.id)) {
            crossfade(true)
        }

        val favourited = isFavourite(item.id)
        binding.ivFavourite.setImageResource(if (favourited) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        binding.ivFavourite.setOnClickListener {
            onToggleFavourite(item.id)
            notifyItemChanged(position)
        }

        val caught = isCaught(item.id)
        binding.ivCaught.visibility = if (caught) View.VISIBLE else View.GONE

        binding.llTypes.removeAllViews()
        val inflater = LayoutInflater.from(binding.root.context)
        item.types.forEach { type ->
            val color = typeColor(type)
            val badgeView = inflater.inflate(R.layout.item_type_badge, binding.llTypes, false) as TextView
            badgeView.text = type
            badgeView.setBackgroundColor(color)
            badgeView.setTextColor(com.pokemon.explorer.data.contrastOn(color))
            binding.llTypes.addView(badgeView)
        }

        binding.cardRoot.setOnClickListener {
            onItemClick(item.id)
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<PokemonSummary>() {
        override fun areItemsTheSame(oldItem: PokemonSummary, newItem: PokemonSummary): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PokemonSummary, newItem: PokemonSummary): Boolean =
            oldItem == newItem
    }
}
