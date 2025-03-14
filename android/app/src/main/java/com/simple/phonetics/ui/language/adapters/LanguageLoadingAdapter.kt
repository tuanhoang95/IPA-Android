package com.simple.phonetics.ui.language.adapters

import com.simple.adapter.ViewItemAdapter
import com.simple.adapter.entities.ViewItem
import com.simple.coreapp.ui.view.Background
import com.simple.coreapp.ui.view.setBackground
import com.simple.phonetics.databinding.ItemLanguageLoadingBinding
import com.tuanha.adapter.annotation.AdapterPreview
import java.util.UUID

@AdapterPreview
open class LanguageLoadingAdapter() : ViewItemAdapter<LanguageLoadingViewItem, ItemLanguageLoadingBinding>() {

    override fun bind(binding: ItemLanguageLoadingBinding, viewType: Int, position: Int, item: LanguageLoadingViewItem) {
        super.bind(binding, viewType, position, item)

        binding.root.delegate.setBackground(item.background)

        binding.ivFlag.delegate.backgroundColor = item.loadingColor
        binding.tvName.delegate.backgroundColor = item.loadingColor
    }
}

class LanguageLoadingViewItem(
    var loadingColor: Int,
    var background: Background,
) : ViewItem {

    override fun areItemsTheSame(): List<Any> = listOf(
        UUID.randomUUID().toString()
    )
}