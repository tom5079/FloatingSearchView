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
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.search_suggestion_item.view.*
import xyz.quaver.floatingsearchview.R
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion

typealias OnBindSuggestionCallback = (
    suggestionView: View,
    leftIcon: ImageView,
    textView: TextView,
    item: SearchSuggestion,
    itemPosition: Int
) -> Unit
class SearchSuggestionsAdapter(
    private val context: Context,
    private val suggestionTextSize: Int,
    private val listener: Listener? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Listener {
        fun onItemSelected(item: SearchSuggestion?)
        fun onMoveItemToSearchClicked(item: SearchSuggestion?)
    }

    class SearchSuggestionViewHolder(val view: View, private val listener: Listener? = null) : RecyclerView.ViewHolder(view) {
        interface Listener {
            fun onItemClicked(adapterPosition: Int)
            fun onMoveItemToSearchClicked(adapterPosition: Int)
        }

        init {
            view.right_icon.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION)
                    listener?.onMoveItemToSearchClicked(adapterPosition)
            }

            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION)
                    listener?.onItemClicked(adapterPosition)
            }
        }

    }

    private val rightIconDrawable = ContextCompat.getDrawable(context, R.drawable.arrow_left)
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

    fun swapData(searchSuggestions: List<SearchSuggestion>) {
        with(this.searchSuggestions) {
            clear()
            addAll(searchSuggestions)
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return LayoutInflater.from(parent.context).inflate(R.layout.search_suggestion_item, parent, false).let {
            it.right_icon.setImageDrawable(rightIconDrawable)

            SearchSuggestionViewHolder(it, object: SearchSuggestionViewHolder.Listener {
                override fun onItemClicked(adapterPosition: Int) {
                    listener?.onItemSelected(searchSuggestions[adapterPosition])
                }

                override fun onMoveItemToSearchClicked(adapterPosition: Int) {
                    listener?.onMoveItemToSearchClicked(searchSuggestions[adapterPosition])
                }
            })
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val view = (holder as SearchSuggestionViewHolder).view

        with(view.right_icon) {
            isEnabled = showRightMoveUpBtn
            visibility = if (showRightMoveUpBtn) View.VISIBLE else View.INVISIBLE
        }

        view.body.text = searchSuggestions[position].body

        onBindSuggestionCallback?.invoke(view, view.left_icon, view.body, searchSuggestions[position], position)
    }

    override fun getItemCount(): Int = searchSuggestions.size

    fun reverseList() {
        searchSuggestions.reverse()
        notifyDataSetChanged()
    }
}