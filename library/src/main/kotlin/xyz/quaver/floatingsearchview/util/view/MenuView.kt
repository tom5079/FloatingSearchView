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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.content.ContextCompat
import xyz.quaver.floatingsearchview.R
import xyz.quaver.floatingsearchview.util.MenuPopupHelper
import xyz.quaver.floatingsearchview.util.dpToPx

private const val HIDE_IF_ROOM_ITEMS_ANIM_DURATION = 400L
private const val SHOW_IF_ROOM_ITEMS_ANIM_DURATION = 450L

class MenuView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

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
    private val actionItems = mutableListOf<MenuItemImpl>()
    private val actionShowAlwaysItems = mutableListOf<MenuItemImpl>()

    private var hasOverflow = false

    var onVisibleWidthChangedListener: ((Int) -> Unit)? = null
    var visibleWidth: Int = 0
        private set

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

        actionShowAlwaysItems.clear()
        actionItems.clear()
        menuItems.clear()
        menuBuilder = MenuBuilder(context)
        menuPopupHelper = MenuPopupHelper(context, menuBuilder, this)

        // clean view and re-inflate
        removeAllViews()
        getMenuInflater().inflate(menu, menuBuilder)

        menuItems += (menuBuilder.actionItems + menuBuilder.nonActionItems)
            .sortedBy { it.order }

        val localActionItems = menuItems.filter {
            it.icon != null && (it.requiresActionButton() || it.requestsActionButton())
        }

        hasOverflow = localActionItems.size < menuItems.size ||
                availWidth/actionDimension < localActionItems.size

        val availItemRoom = availWidth / actionDimension - if (hasOverflow) 1 else 0

        val actionItemsIds = mutableListOf<Int>()
        localActionItems.filterNot { it.icon == null }.take(availItemRoom).forEach {
            addView(createActionView().apply {
                contentDescription = it.title
                setImageDrawable(it.icon)

                setOnClickListener { _ ->
                    menuCallback?.onMenuItemSelected(menuBuilder, it)
                }
            })

            actionItems.add(it)
            actionItemsIds.add(it.itemId)
        }

        if (hasOverflow) {
            addView(createOverflowActionView().apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.dots_vertical))

                setOnClickListener {
                    menuPopupHelper.show()
                }
            })

            menuBuilder.setCallback(menuCallback)
        }

        actionItemsIds.forEach { menuBuilder.removeItem(it) }

        onVisibleWidthChangedListener?.invoke(
            actionDimension * childCount - if (hasOverflow) dpToPx(8) else 0
        )
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
    fun hideIfRoomItems(withAnim: Boolean) {
        if (menu < 0) return

        actionShowAlwaysItems.clear()

        val actionItemSize = menuItems.filter {
            it.icon != null && it.requiresActionButton()
        }.take(actionItems.size).also {
            it.forEachIndexed { i, item ->
                if (actionItems[i].itemId != item.itemId) {
                    (getChildAt(i) as ImageView).apply {
                        setImageDrawable(item.icon)
                        setOnClickListener {
                            menuCallback?.onMenuItemSelected(menuBuilder, item)
                        }
                    }
                }
            }
        }.size

        val diff = actionItems.size - actionItemSize + if (hasOverflow) 1 else 0

        cancelAnimation()

        //add anims for moving showAlwaysItem views to the right
        val destTransX = ((actionDimension*diff) - if (hasOverflow) dpToPx(8) else 0).toFloat()
        (0 until actionItemSize).map { getChildAt(it) }.forEach {
            it.animate().apply {
                duration = if (withAnim) HIDE_IF_ROOM_ITEMS_ANIM_DURATION else 0
                interpolator = AccelerateInterpolator()
                translationX(destTransX)
            }
        }

        //add anims for moving to right and/or zooming out previously shown items
        (actionItemSize until actionItemSize + diff).map { Pair(it, getChildAt(it)) }.forEach { (i, view) ->
            view.isClickable = false

            view.animate().apply {
                duration = if (withAnim) HIDE_IF_ROOM_ITEMS_ANIM_DURATION else 0
                if (i != childCount - 1)
                    translationXBy(actionDimension.toFloat())
                scaleX(.5F)
                scaleY(.5F)
                alpha(0F)

                if (i == actionItemSize + diff - 1)
                    setListener(object: AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            onVisibleWidthChangedListener?.invoke(
                                actionDimension * childCount - if (hasOverflow) dpToPx(8) else 0
                            )
                        }
                    })
            }
        }
    }

    fun showIfRoomItems(withAnim: Boolean) {
        if (menu < 0) return

        cancelAnimation()

        if (menuItems.isEmpty()) return

        (0 until childCount).map { getChildAt(it) }.forEachIndexed { i, view ->

            if (i < actionItems.size) {
                val item = actionItems[i]

                (view as ImageView).apply {
                    setImageDrawable(item.icon)
                    setOnClickListener {
                        menuCallback?.onMenuItemSelected(menuBuilder, item)
                    }
                }
            }

            view.isClickable = true

            view.animate().apply {
                duration = if (withAnim) SHOW_IF_ROOM_ITEMS_ANIM_DURATION else 0

                interpolator = if (i > actionShowAlwaysItems.size - 1)
                    LinearInterpolator()
                else
                    DecelerateInterpolator()

                translationX(0F)
                scaleX(1.0F)
                scaleY(1.0F)
                alpha(1.0F)

                if (i == childCount - 1)
                    setListener(object: AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            onVisibleWidthChangedListener?.invoke(
                                actionDimension * childCount - if (hasOverflow) dpToPx(8) else 0
                            )
                        }
                    })
            }
        }
    }

    private fun getMenuInflater(): MenuInflater =
        menuInflater ?: SupportMenuInflater(context).also {
            menuInflater = it
        }

    private fun cancelAnimation() {
        for (i in 0 until childCount)
            getChildAt(i).clearAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        cancelAnimation()
    }

}