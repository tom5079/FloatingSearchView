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

@file:SuppressLint("RestrictedApi")

package xyz.quaver.floatingsearchview.util.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.view.menu.SubMenuBuilder
import androidx.core.content.ContextCompat
import xyz.quaver.floatingsearchview.R
import xyz.quaver.floatingsearchview.util.MenuPopupHelper
import xyz.quaver.floatingsearchview.util.setIconColor
import kotlin.math.min

private const val HIDE_IF_ROOM_ITEMS_ANIM_DURATION = 400L
private const val SHOW_IF_ROOM_ITEMS_ANIM_DURATION = 450L

class MenuView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : LinearLayout(context, attrs, defStyle) {

    private val actionDimension : Int = resources.getDimensionPixelSize(R.dimen.square_button_size)

    private var menu = -1
    private var menuBuilder = MenuBuilder(context)
    private var menuInflater: SupportMenuInflater? = null
    private var menuPopupHelper = MenuPopupHelper(context, menuBuilder, this)

    /**
     * Callback that will be called when menu items are selected.
     */
    var menuCallback: MenuBuilder.Callback? = null

    // all menu items
    val menuItems = mutableListOf<MenuItemImpl>()
    // items that are currently presented as actions
    private val actionItems = mutableListOf<Pair<MenuItemImpl, ImageView>>()

    private var hasOverflow = false

    var visibleWidth: Int = 0
        private set

    var actionIconColor: Int = -1
        set(color) {
            field = color
            refreshColor()
        }

    var overflowColor: Int = -1
        set(color) {
            field = color
            refreshColor()
        }

    private fun refreshColor() {
        repeat(childCount) { i ->
            val child = getChildAt(i) as ImageView

            child.setIconColor(
                if (hasOverflow && i == childCount-1) overflowColor
                else actionIconColor
            )
        }
    }

    /**
     * Resets the the view to fit into a new
     * available width.
     * <p/>
     * <p>This clears and then re-inflates the menu items
     * , removes all of its associated action views, and re-creates
     * the menu and action items to fit in the new width.</p>
     *
     * @param availWidth the width available for the menu to use. If
     *                   there is room, menu items that are flagged with
     *                   android:showAsAction="ifRoom" or android:showAsAction="always"
     *                   will show as actions.
     */
    fun reset(menu: Int, availWidth: Int) {
        this.menu = menu.also {
            if (it < 0)
                return
        }

        actionItems.clear()
        menuItems.clear()
        menuBuilder = MenuBuilder(context)
        menuPopupHelper = MenuPopupHelper(context, menuBuilder, this)

        // clean view and re-inflate
        removeAllViews()
        getMenuInflater().inflate(menu, menuBuilder)

        menuItems += (menuBuilder.actionItems + menuBuilder.nonActionItems)
            .sortedBy { it.order }

        val actionItemsSize = menuItems.count {
            it.icon != null && (it.requiresActionButton() || it.requestsActionButton())
        }

        hasOverflow = actionItemsSize < menuItems.size ||
                availWidth/actionDimension < actionItemsSize

        var availItemRoom = availWidth / actionDimension - if (hasOverflow) 1 else 0

        val localActionItems = MutableList<MenuItemImpl?>(min(actionItemsSize, availItemRoom)) { null }.apply {
            while (availItemRoom > 0) {
                this.add(menuItems.firstOrNull {
                    it.icon != null && it.requiresActionButton() && !this.contains(it)
                } ?: break)
                availItemRoom--
            }

            while (availItemRoom > 0) {
                val item = menuItems.firstOrNull {
                    it.icon != null && it.requestsActionButton() && !this.contains(it)
                } ?: break

                val index = menuItems.indexOfFirst {
                    it.requiresActionButton() && menuItems.indexOf(item) < menuItems.indexOf(it)
                }

                if (index < 0)
                    this.add(item)
                else
                    this.add(index, item)

                availItemRoom--
            }
        }.filterNotNull()

        localActionItems.forEach {
            addView(createActionView().apply {
                contentDescription = it.title
                setImageDrawable(it.icon)
                setIconColor(actionIconColor)

                setOnClickListener { _ ->
                    (it.subMenu as? SubMenuBuilder)?.let { subMenu ->
                        MenuPopupHelper(context, subMenu, this@MenuView).show()
                    }
                    menuCallback?.onMenuItemSelected(menuBuilder, it)
                }

                actionItems.add(Pair(it, this))
            })

            menuBuilder.removeItem(it.itemId)
        }

        if (hasOverflow) {
            addView(createOverflowActionView().apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.dots_vertical))
                setIconColor(overflowColor)

                setOnClickListener {
                    menuPopupHelper.show()
                }
            })

            menuBuilder.setCallback(menuCallback)
        }
    }

    private fun createActionView(): ImageView =
        LayoutInflater.from(context).inflate(R.layout.action_item_layout, this, false) as ImageView

    private fun createOverflowActionView(): ImageView =
        LayoutInflater.from(context).inflate(R.layout.overflow_action_item_layout, this, false) as ImageView

    /**
     * Hides all the menu items flagged with "ifRoom"
     *
     * @param withAnim
     */
    fun hideIfRoomItems() {
        if (menu < 0) return

        actionItems.filter { (menuItem, _ ) ->
            menuItem.requestsActionButton()
        }.forEach { (_, view) ->
            view.visibility = View.GONE
        }

        if (hasOverflow)
            getChildAt(childCount-1).visibility = View.GONE
    }

    fun showIfRoomItems() {
        if (menu < 0) return

        actionItems.forEach {
            it.second.visibility = View.VISIBLE
        }

        if (hasOverflow)
            getChildAt(childCount-1).visibility = View.VISIBLE
    }

    private fun getMenuInflater(): MenuInflater =
        menuInflater ?: SupportMenuInflater(context).also {
            menuInflater = it
        }

}