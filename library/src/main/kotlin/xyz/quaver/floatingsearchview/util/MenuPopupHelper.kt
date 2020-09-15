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

@file:SuppressLint("PrivateResource", "RestrictedApi")

package xyz.quaver.floatingsearchview.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.view.*
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.appcompat.view.menu.*
import androidx.appcompat.widget.ListPopupWindow
import xyz.quaver.floatingsearchview.R
import kotlin.math.max
import kotlin.math.min

private const val TAG = "MenuPopupHelper"
private val ITEM_LAYOUT = R.layout.abc_popup_menu_item_layout

class MenuPopupHelper(
    private val context: Context,
    private val menu: MenuBuilder,
    var anchorView: View? = null,
    private val overflowOnly: Boolean = false,
    private val popupStyleAttr: Int = R.attr.popupMenuStyle,
    private val popupStyleRes: Int = 0
) : AdapterView.OnItemClickListener,
    View.OnKeyListener,
    ViewTreeObserver.OnGlobalLayoutListener,
    PopupWindow.OnDismissListener,
    MenuPresenter {

    private inner class MenuAdapter(val menu: MenuBuilder) : BaseAdapter() {

        private var expendedIndex: Int = findExpendedIndex()

        fun findExpendedIndex(): Int =
            menu.expandedItem?.let { expendedItem ->
                menu.nonActionItems.indexOfFirst { it == expendedItem }
            } ?: -1

        private val items: ArrayList<MenuItemImpl>
            get() = (if (overflowOnly) menu.nonActionItems else menu.visibleItems)

        override fun getCount(): Int = when {
            expendedIndex < 0 -> items.size
            else -> items.size - 1
        }

        override fun getItem(position: Int): MenuItemImpl =
            items[if (expendedIndex in 0..position) position + 1 else position]

        // Since a menu item's ID is optional, we'll use the position as an
        // ID for the item in the AdapterView
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            ((convertView ?: inflater.inflate(ITEM_LAYOUT, parent, false)) as ListMenuItemView).also {
                it.initialize(getItem(position), 0)
                if (forceShowIcon) it.setForceShowIcon(true)
            }

        override fun notifyDataSetChanged() {
            expendedIndex = findExpendedIndex()
            super.notifyDataSetChanged()
        }
    }

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val adapter = MenuAdapter(menu)
    private val popupMaxWidth = max(
        context.resources.displayMetrics.widthPixels / 2,
        context.resources.getDimensionPixelSize(R.dimen.abc_config_prefDialogWidth)
    )
    private var popup: ListPopupWindow? = null
    private var treeObserver: ViewTreeObserver? = null
    private var presenterCallback: MenuPresenter.Callback? = null
    private var measureParent: ViewGroup? = null
    private var contentWidth: Int? = null

    init {
        menu.addMenuPresenter(this, context)
    }

    var forceShowIcon: Boolean = false
    var dropDownGravity = Gravity.NO_GRAVITY

    fun show() = tryShow().also{ if (!it) error("MenuPopupHelper cannot be used without an anchor") }
    fun tryShow(): Boolean {
        val popup = ListPopupWindow(context, null, popupStyleAttr, popupStyleRes).also {
            this.popup = it
        }

        popup.setOnDismissListener(this)
        popup.setOnItemClickListener(this)
        popup.setAdapter(adapter)
        popup.isModal = true

        val anchor = (anchorView ?: return false)
        val contentWidth = contentWidth ?: measureContentWidth().also { contentWidth = it }

        popup.anchorView = anchor
        popup.setDropDownGravity(dropDownGravity)

        treeObserver = anchor.viewTreeObserver.also {
            treeObserver ?: it.addOnGlobalLayoutListener(this)
        }

        popup.setContentWidth(contentWidth)
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED

        val (verticalOffset, horizontalOffset) = when (Build.VERSION.SDK_INT) {
            in 0..20 ->
                Pair(-anchor.height - dpToPx(4), -contentWidth + anchor.width - dpToPx(8))
            else ->
                Pair(-anchor.height + dpToPx(4), -contentWidth + anchor.width)
        }

        popup.verticalOffset = verticalOffset
        popup.horizontalOffset = horizontalOffset
        popup.show()
        popup.listView?.setOnKeyListener(this)

        return true
    }

    val isShowing: Boolean
        get() = popup?.isShowing == true

    fun dismiss() {
        if (isShowing) popup?.dismiss()
    }

    override fun onDismiss() {
        popup = null
        menu.close()
        treeObserver?.let {
            if (!it.isAlive) treeObserver = anchorView?.viewTreeObserver

            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 16)
                it.removeGlobalOnLayoutListener(this)
            else
                it.removeOnGlobalLayoutListener(this)
            treeObserver = null
        }
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        adapter.menu.performItemAction(adapter.getItem(position), 0)
    }

    override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU && event?.action == KeyEvent.ACTION_UP) {
            dismiss()
            return true
        }
        return false
    }

    private fun measureContentWidth(): Int {
        var itemView: View? = null
        var itemType = 0

        return (0 until adapter.count).map { i ->
            adapter.getItemViewType(i).let {
                if (it != itemType) {
                    itemType = it
                    itemView = null
                }
            }

            adapter.getView(
                i,
                itemView,
                measureParent ?: FrameLayout(context).also { measureParent = it }).let {
                itemView = it

                it.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )

                min(it.measuredWidth, popupMaxWidth)
            }
        }.maxOrNull() ?: 0
    }

    override fun onGlobalLayout() {
        if (!isShowing)
            return

        if (anchorView?.isShown == true)
            popup?.show()
        else
            dismiss()
    }

    override fun initForMenu(context: Context?, menu: MenuBuilder?) {
        // Don't need to do anything; we added as a presenter in the constructor.
    }

    override fun getMenuView(root: ViewGroup?): MenuView {
        throw UnsupportedOperationException("MenuPopupHelpers manage their own views")
    }

    override fun updateMenuView(cleared: Boolean) {
        contentWidth = null
        adapter.notifyDataSetChanged()
    }

    override fun setCallback(cb: MenuPresenter.Callback?) {
        presenterCallback = cb
    }

    override fun onSubMenuSelected(subMenu: SubMenuBuilder?): Boolean {
        if (subMenu?.hasVisibleItems() == true) {
            MenuPopupHelper(context, subMenu, anchorView).apply {
                setCallback(presenterCallback)

                forceShowIcon = (0 until subMenu.size()).any { i ->
                    subMenu.getItem(i).let {
                        it.isVisible && it.icon != null
                    }
                }

                if (tryShow())
                    presenterCallback?.onOpenSubMenu(subMenu)

                return true
            }
        }

        return false
    }

    override fun onCloseMenu(menu: MenuBuilder?, allMenusAreClosing: Boolean) {
        if (menu != this.menu) return

        dismiss()
        presenterCallback?.onCloseMenu(menu, allMenusAreClosing)
    }

    override fun flagActionItems(): Boolean = false
    override fun expandItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false
    override fun collapseItemActionView(menu: MenuBuilder?, item: MenuItemImpl?): Boolean = false
    override fun getId(): Int = 0
    override fun onSaveInstanceState(): Parcelable? = null
    override fun onRestoreInstanceState(state: Parcelable?) {}

}