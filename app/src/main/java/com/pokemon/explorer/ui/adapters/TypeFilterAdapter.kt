package com.pokemon.explorer.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pokemon.explorer.R
import com.pokemon.explorer.data.contrastOn
import com.pokemon.explorer.data.typeColor
import com.pokemon.explorer.databinding.ItemTypeChipBinding

class TypeFilterAdapter(
    private val filters: List<String>,
    private var selectedFilter: String,
    private val onSelectFilter: (String) -> Unit
) : RecyclerView.Adapter<TypeFilterAdapter.ChipViewHolder>() {

    class ChipViewHolder(val binding: ItemTypeChipBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemTypeChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val filter = filters[position]
        val binding = holder.binding

        val isSelected = filter == selectedFilter
        binding.tvChipLabel.text = if (filter == "All") "✦ All" else filter

        if (isSelected) {
            val bg = if (filter == "All") Color.parseColor("#FFCB05") else typeColor(filter)
            binding.tvChipLabel.setBackgroundColor(bg)
            val textColor = if (filter == "All") Color.parseColor("#1A1200") else contrastOn(bg)
            binding.tvChipLabel.setTextColor(textColor)
        } else {
            binding.tvChipLabel.setBackgroundResource(R.drawable.bg_chip_rounded)
            binding.tvChipLabel.setTextColor(binding.root.context.getColor(R.color.text_medium))
        }

        binding.tvChipLabel.setOnClickListener {
            selectedFilter = filter
            onSelectFilter(filter)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = filters.size
}
