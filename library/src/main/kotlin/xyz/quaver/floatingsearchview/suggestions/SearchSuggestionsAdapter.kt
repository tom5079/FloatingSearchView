/*
 *     tom5079/FloatingSearchView was ported from arimorty/FloatingSearchView
 *
 *     Copyright 2015 Ari C.
 *     Copyright 2020 tom5079
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package xyz.quaver.floatingsearchview.suggestions

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import xyz.quaver.floatingsearchview.databinding.SearchSuggestionItemBinding
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion

typealias OnBindSuggestionCallback = (
    binding: SearchSuggestionItemBinding,
    item: SearchSuggestion,
    itemPosition: Int
) -> Unit
class SearchSuggestionsAdapter(
    private val context: Context,
    private val suggestionTextSize: Int,
    private val listener: Listener? = null
) : RecyclerView.Adapter<SearchSuggestionsAdapter.SearchSuggestionViewHolder>() {

    interface Listener {
        fun onItemSelected(item: SearchSuggestion?)
        fun onMoveItemToSearchClicked(item: SearchSuggestion?)
    }

    inner class SearchSuggestionViewHolder(val binding: SearchSuggestionItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var item: SearchSuggestion? = null

        init {
            binding.rightIcon.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION)
                    listener?.onMoveItemToSearchClicked(item)
            }

            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION)
                    listener?.onItemSelected(item)
            }
        }

        fun bind(item: SearchSuggestion, position: Int) {
            this.item = item

            with (binding.rightIcon) {
                isEnabled = showRightMoveUpBtn
                visibility = if (showRightMoveUpBtn) View.VISIBLE else View.INVISIBLE
            }

            binding.body.text = item.body

            onBindSuggestionCallback?.invoke(binding, item, position)
        }

    }

    var showRightMoveUpBtn = false
        set(value) {
            field.let {
                field = value

                if (it != value)
                    notifyDataSetChanged()
            }
        }

    val searchSuggestions = mutableListOf<SearchSuggestion>()
    var onBindSuggestionCallback: OnBindSuggestionCallback? = null

    var reverseList = true
        set(value) {
            field = value

            notifyDataSetChanged()
        }

    fun swapData(searchSuggestions: List<SearchSuggestion>) {
        this.searchSuggestions.clear()
        this.searchSuggestions.addAll(searchSuggestions)

        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchSuggestionsAdapter.SearchSuggestionViewHolder {
        return SearchSuggestionViewHolder(
            SearchSuggestionItemBinding.inflate(
                LayoutInflater.from(
                    parent.context
                ), parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: SearchSuggestionsAdapter.SearchSuggestionViewHolder, position: Int) {
        holder.bind(searchSuggestions[if (reverseList) searchSuggestions.size - position - 1 else position], position)
    }

    override fun getItemCount(): Int = searchSuggestions.size
}