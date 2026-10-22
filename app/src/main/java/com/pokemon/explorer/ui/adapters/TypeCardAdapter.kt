package com.pokemon.explorer.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pokemon.explorer.data.TYPE_ICONS
import com.pokemon.explorer.data.contrastOn
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.ItemTypeCardBinding

class TypeCardAdapter(
    private val types: List<String>,
    private val onTypeClick: (String) -> Unit
) : RecyclerView.Adapter<TypeCardAdapter.TypeViewHolder>() {

    class TypeViewHolder(val binding: ItemTypeCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TypeViewHolder {
        val binding = ItemTypeCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TypeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TypeViewHolder, position: Int) {
        val type = types[position]
        val binding = holder.binding

        val color = typeColor(type)
        binding.cardTypeRoot.setCardBackgroundColor(color)
        binding.tvTypeIcon.text = TYPE_ICONS[type] ?: "?"
        binding.tvTypeName.text = type
        binding.tvTypeName.setTextColor(contrastOn(color))

        binding.cardTypeRoot.setOnClickListener {
            onTypeClick(type)
        }
    }

    override fun getItemCount(): Int = types.size
}
