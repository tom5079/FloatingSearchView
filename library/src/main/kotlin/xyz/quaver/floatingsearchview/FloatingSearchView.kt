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

package xyz.quaver.floatingsearchview

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.*
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.content.ContextCompat
import androidx.core.view.*
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.floating_search_layout.view.*
import kotlinx.android.synthetic.main.search_query_section.view.*
import kotlinx.android.synthetic.main.search_suggestions_section.view.*
import xyz.quaver.floatingsearchview.suggestions.OnBindSuggestionCallback
import xyz.quaver.floatingsearchview.suggestions.SearchSuggestionsAdapter
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion
import xyz.quaver.floatingsearchview.util.*
import xyz.quaver.floatingsearchview.util.adapter.GestureDetectorListenerAdapter
import xyz.quaver.floatingsearchview.util.adapter.OnItemTouchListenerAdapter
import kotlin.math.abs
import kotlin.math.min

private const val CLEAR_BTN_FADE_ANIM_DURATION = 500L
private const val CLEAR_BTN_WIDTH_DP = 48

private const val LEFT_MENU_WIDTH_AND_MARGIN_START_DP = 52

private const val MENU_BUTTON_PROGRESS_ARROW = 1.0f
private const val MENU_BUTTON_PROGRESS_HAMBURGER = 0.0f

private const val BACKGROUND_DRAWABLE_ALPHA_SEARCH_FOCUSED = 150
private const val BACKGROUND_DRAWABLE_ALPHA_SEARCH_NOT_FOCUSED = 0

private const val BACKGROUND_FADE_ANIM_DURATION = 250L
private const val MENU_ICON_ANIM_DURATION = 250L

private val SUGGEST_ITEM_ADD_ANIM_INTERPOLATOR: Interpolator = LinearInterpolator()

open class FloatingSearchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    enum class LeftActionMode(val value: Int){
        NO_ACTION(-1),
        HAMBURGER(0),
        SEARCH(1),
        HOME(2)
    }

    private object Defaults {
        val leftActionMode = LeftActionMode.NO_ACTION
        val searchHint = R.string.abc_search_hint
        const val showMoveUpSuggestion = false
        const val dismissOnOutsideClick = true
        const val closeSearchOnKeyboardDismiss = false
        const val showSearchKey = true
        const val queryTextSize = 18
        const val suggestionTextSize = 18
        const val dimBackground = true
        const val suggestionAnimDuration = 250L
        const val searchBarMargin = 0
        const val dismissFocusOnItemSelection = false
    }

    //region listener
    interface OnSearchListener {
        fun onSuggestionClicked(searchSuggestion: SearchSuggestion?)
        fun onSearchAction(currentQuery: String?)
    }
    interface OnMenuStatusChangeListener {
        fun onMenuOpened()
        fun onMenuClosed()
    }
    interface OnFocusChangeListener {
        fun onFocus()
        fun onFocusCleared()
    }
    //endregion

    // region attributes
    private val backgroundDrawable: Drawable = ColorDrawable(Color.BLACK)

    val currentMenuItems: List<MenuItemImpl>
        get() = menu_view.menuItems.toList()

    var dimBackground = false
        set(value) {
            field = value
            refreshDimBackground()
        }

    var dismissFocusOnItemSelection: Boolean = Defaults.dismissFocusOnItemSelection
    var closeSearchOnKeyboardDismiss = Defaults.closeSearchOnKeyboardDismiss
    var dismissOnOutsideClick = Defaults.dismissOnOutsideClick
        set(value) {
            field = value

            search_suggestions_section.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP)
                    v.performClick()

                //todo check if this is called twice
                if (value && isSearchFocused)
                    isSearchFocused = false

                true
            }
        }

    var isSearchFocused = false
        private set(value) {
            field = value

            if (value) {
                search_bar_text.requestFocus()
                moveSuggestionListToInitialPos()
                search_suggestions_section.visibility = View.VISIBLE
                if (dimBackground) animateBackground(BackgroundAnimation.FADE_IN)
                menu_view.hideIfRoomItems(true)
                transitionInLeftSection(true)
                context.showSoftKeyboard(search_bar_text)
                if (menuOpen)
                    closeMenu(false)

                with(search_bar_text) {
                    if (isTitleSet) {
                        skipTextChangeEvent = true
                        setText("")
                    } else
                        setSelection(text?.length ?: 0)
                    isLongClickable = true

                    this@FloatingSearchView.clear_btn.visibility = if (this.text?.isEmpty() == true)
                        View.INVISIBLE
                    else
                        View.VISIBLE
                }

                onFocusChangeListener?.onFocus()
            } else {
                search_bar.requestFocus()
                clearSuggestions()
                if (dimBackground) animateBackground(BackgroundAnimation.FADE_OUT)
                menu_view.showIfRoomItems(true)
                transitionOutLeftSection(true)
                clear_btn.visibility = View.GONE
                context.hostActivity?.closeSoftKeyboard()

                with(search_bar_text) {
                    if (isTitleSet) {
                        skipTextChangeEvent = true
                        setText(titleText)
                    }
                    isLongClickable = false
                }
                onFocusChangeListener?.onFocusCleared()
            }

            search_suggestions_section.isEnabled = value
        }

    var queryTextSize: Int = Defaults.queryTextSize
        set(value) {
            field = value
            search_bar_text.textSize = value.toFloat()
        }

    private var titleText: CharSequence? = null
    private var isTitleSet = false

    var query = ""
        private set

    val menuBtnDrawable: DrawerArrowDrawable = DrawerArrowDrawable(context)
    val iconBackArrow: Drawable? = ContextCompat.getDrawable(context, R.drawable.arrow_left)
    val iconClear: Drawable? = ContextCompat.getDrawable(context, R.drawable.close)
    val iconSearch: Drawable? = ContextCompat.getDrawable(context, R.drawable.magnify)

    var leftActionMode: LeftActionMode = LeftActionMode.NO_ACTION
        set(value) {
            field = value
            refreshLeftIcon()
        }

    var searchHint: CharSequence = resources.getText(Defaults.searchHint)
        set(value) {
            field = value
            search_bar_text.hint = value
        }
    var showSearchKey: Boolean = Defaults.showSearchKey
        set(value) {
            field = value

            search_bar_text.imeOptions =
                if (value)
                    EditorInfo.IME_ACTION_SEARCH
                else
                    EditorInfo.IME_ACTION_NONE
        }

    private var elevation: Int = dpToPx(4)
    private var menuOpen = false
    private var menuId = -1
    private var skipQueryFocusChangeEvent = false
    private var skipTextChangeEvent = false

    private var suggestionsAdapter: SearchSuggestionsAdapter? = null
    var onBindSuggestionCallback: OnBindSuggestionCallback? = null
        set(value) {
            field = value
            suggestionsAdapter?.onBindSuggestionCallback = value
        }
    var suggestionTextSize = 0
    private var isInitialLayout = true
    private var isSuggestionsSectionHeightSet = false
    var showMoveUpSuggestion: Boolean = Defaults.showMoveUpSuggestion
        set(value) {
            field = value
            suggestionsAdapter?.showRightMoveUpBtn = value
        }
    private var suggestionAnimDuration: Long = 0

    var queryText: CharSequence?
        get() = search_bar_text.text.toString()
        set(value) {
            with(search_bar_text) {
                setText(value)
                setSelection(this.text?.length ?: 0)
            }
        }

    var onFocusChangeListener: OnFocusChangeListener? = null
    var onSearchListener: OnSearchListener? = null
    var onQueryChangeListener: ((oldQuery: String, newQuery: String) -> Unit)? = null
    var onMenuStatusChangeListener: OnMenuStatusChangeListener? = null
    var onHomeActionClickListener: (() -> Unit)? = null
    var onLeftMenuClickListener: OnClickListener? = null
    var onMenuItemClickListener: ((item: MenuItem) -> Unit)? = null
    var onClearSearchActionListener: (() -> Unit)? = null
    var onSuggestionsListHeightChanged: ((newHeight: Float) -> Unit)? = null
    private var suggestionSecHeightListener: (() -> Unit)? = null
    //endregion

    //region init
    init {
        inflate(context, R.layout.floating_search_layout, this)

        attrs?.let { applyAttributes(
            context.obtainStyledAttributes(
                it,
                R.styleable.FloatingSearchView
            )
        ) }

        ViewCompat.setBackground(this, backgroundDrawable)

        setupQueryBar()

        if (!isInEditMode)
            setupSuggestionSection()
    }

    private fun setupQueryBar() {
        if (!isInEditMode)
            context.hostActivity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        search_query_section.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                search_query_section.removeGlobalLayoutObserver(this)

                inflateOverflowMenu(menuId)
            }
        })

        with(menu_view) {
            menuCallback = object: MenuBuilder.Callback {
                override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean {
                    onMenuItemClickListener?.invoke(item)

                    return false
                }

                override fun onMenuModeChange(menu: MenuBuilder) {}
            }
        }

        with(clear_btn) {
            visibility = View.INVISIBLE
            setImageDrawable(iconClear)
            setOnClickListener {
                this@FloatingSearchView.search_bar_text.setText("")
                onClearSearchActionListener?.invoke()
            }
        }

        with(search_bar_text) {
            addTextChangedListener(onTextChanged = { s, start, before, count ->
                val clear_btn = this@FloatingSearchView.clear_btn

                if (skipTextChangeEvent || !isSearchFocused)
                    skipTextChangeEvent = false
                else {
                    if (this.text?.isNotEmpty() == true && clear_btn.visibility == View.INVISIBLE)
                        ViewCompat.animate(clear_btn)
                            .setDuration(CLEAR_BTN_FADE_ANIM_DURATION)
                            .withStartAction {
                                clear_btn.alpha = 0F
                                clear_btn.visibility = View.VISIBLE
                            }
                            .alpha(1F)
                    else if (this.text?.isEmpty() == true)
                        clear_btn.visibility = View.INVISIBLE

                    val newQuery = this.text.toString()

                    if (isSearchFocused && query != newQuery)
                        onQueryChangeListener?.invoke(query, newQuery)

                    query = newQuery
                }
            })

            setOnFocusChangeListener { v, hasFocus ->
                if (skipQueryFocusChangeEvent)
                    skipQueryFocusChangeEvent = false
                else if (hasFocus != isSearchFocused)
                    isSearchFocused = hasFocus
            }

            onKeyboardDismissedListener = {
                if (closeSearchOnKeyboardDismiss) isSearchFocused = false
            }

            onSearchKeyListener = {
                onSearchListener?.onSearchAction(query)

                skipTextChangeEvent = true

                if (isTitleSet)
                    setSearchBarTitle(query)
                else
                    setSearchText(query)

                isSearchFocused = false
            }
        }

        left_action.setOnClickListener {
            if (isSearchFocused)
                isSearchFocused = false
            else
                when (leftActionMode) {
                    LeftActionMode.HAMBURGER ->
                        if (onLeftMenuClickListener != null)
                            onLeftMenuClickListener?.onClick(left_action)
                        else
                            toggleLeftMenu()
                    LeftActionMode.SEARCH -> isSearchFocused = true
                    LeftActionMode.HOME -> onHomeActionClickListener?.invoke()
                    else -> { /*do nothing*/ }
                }
        }

        refreshLeftIcon()
    }

    private fun setupSuggestionSection() {
        with(suggestions_list) {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true)
            itemAnimator = null

            addOnItemTouchListener(object : OnItemTouchListenerAdapter() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    GestureDetectorCompat(context, object : GestureDetectorListenerAdapter() {
                        override fun onScroll(
                            e1: MotionEvent?,
                            e2: MotionEvent?,
                            x: Float,
                            y: Float
                        ): Boolean {
                            context.hostActivity?.closeSoftKeyboard()

                            return false
                        }
                    })

                    return false
                }
            })

            suggestionsAdapter = SearchSuggestionsAdapter(
                context,
                suggestionTextSize,
                object : SearchSuggestionsAdapter.Listener {
                    override fun onItemSelected(item: SearchSuggestion?) {
                        onSearchListener?.onSuggestionClicked(item)
                        if (dismissFocusOnItemSelection) {
                            isSearchFocused = false
                            skipTextChangeEvent = true
                            if (isTitleSet)
                                setSearchBarTitle(item?.body)
                            else
                                setSearchText(item?.body)

                            isSearchFocused = false
                        }
                    }

                    override fun onMoveItemToSearchClicked(item: SearchSuggestion?) {
                        queryText = item?.body
                    }
                })

            suggestionsAdapter?.apply {
                showRightMoveUpBtn = showMoveUpSuggestion
                adapter = suggestionsAdapter
            }

            suggestions_list.adapter = suggestionsAdapter
        }
    }

    private fun applyAttributes(attrs: TypedArray) = try {
        elevation = attrs.getDimensionPixelSize(
            R.styleable.FloatingSearchView_arrelevation,
            dpToPx(4)
        ).also { elevation ->
            search_query_section.cardElevation = elevation.toFloat()
            suggestions_list_container.cardElevation = elevation.toFloat()
            search_query_section.maxCardElevation = elevation.toFloat()
            suggestions_list_container.maxCardElevation = elevation.toFloat()

            search_query_section_parent.updateLayoutParams<LinearLayout.LayoutParams> {
                setMargins(0, 0, 0, -3*elevation/2)
            }
        }

        listOf(
            R.styleable.FloatingSearchView_searchBarMarginLeft,
            R.styleable.FloatingSearchView_searchBarMarginLeft,
            R.styleable.FloatingSearchView_searchBarMarginRight
        ).map { attrs.getDimensionPixelSize(it, Defaults.searchBarMargin) }.let { (left, top, right) ->
            search_query_section.updateLayoutParams<LayoutParams> {
                setMargins(left, top, right, 0)
            }
            search_suggestions_section.updateLayoutParams<LinearLayout.LayoutParams> {
                setMargins(left, 0, right, 0)
            }
            divider.updateLayoutParams<LayoutParams> {
                val cornerRadius = search_query_section.radius.toInt()
                setMargins(left+elevation+cornerRadius, 0, right+elevation+cornerRadius, 3*elevation/2)
            }
        }

        queryTextSize = attrs.getDimensionPixelSize(
            R.styleable.FloatingSearchView_searchInputTextSize,
            Defaults.queryTextSize
        )
        searchHint = attrs.getText(R.styleable.FloatingSearchView_searchHint)
        showSearchKey = attrs.getBoolean(
            R.styleable.FloatingSearchView_showSearchKey,
            Defaults.showSearchKey
        )
        closeSearchOnKeyboardDismiss = attrs.getBoolean(
            R.styleable.FloatingSearchView_close_search_on_keyboard_dismiss,
            Defaults.closeSearchOnKeyboardDismiss
        )
        dismissOnOutsideClick = attrs.getBoolean(
            R.styleable.FloatingSearchView_dismissOnOutsideTouch,
            Defaults.dismissOnOutsideClick
        )
        dismissFocusOnItemSelection = attrs.getBoolean(
            R.styleable.FloatingSearchView_dismissFocusOnItemSelection,
            Defaults.dismissFocusOnItemSelection
        )
        suggestionTextSize = attrs.getDimensionPixelSize(
            R.styleable.FloatingSearchView_suggestionTextSize,
            spToPx(Defaults.suggestionTextSize)
        )
        leftActionMode = LeftActionMode.values().firstOrNull {
            it.value == attrs.getInt(R.styleable.FloatingSearchView_leftActionMode, Defaults.leftActionMode.value)
        } ?: Defaults.leftActionMode
        menuId = attrs.getResourceId(
            R.styleable.FloatingSearchView_menu,
            -1
        )
        dimBackground = attrs.getBoolean(
            R.styleable.FloatingSearchView_dimBackground,
            Defaults.dimBackground
        )
        showMoveUpSuggestion = attrs.getBoolean(
            R.styleable.FloatingSearchView_showMoveUpSuggestion,
            Defaults.showMoveUpSuggestion
        )
        suggestionAnimDuration = attrs.getInt(
            R.styleable.FloatingSearchView_suggestionAnimDuration,
            Defaults.suggestionAnimDuration.toInt()
        ).toLong()

        // TODO("COLORS?")
    } finally { attrs.recycle() }
    //endregion
    
    //region override
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        
        if (isInitialLayout) {
            val addedHeight = (3*(elevation+search_query_section.radius)).toInt()
            val finalHeight = search_suggestions_section.height + addedHeight
            
            with(search_suggestions_section) {
                layoutParams.height = finalHeight
                requestLayout()
            }
            
            suggestions_list_container.viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (search_suggestions_section.height == finalHeight) {
                        suggestions_list_container.removeGlobalLayoutObserver(this)

                        isSuggestionsSectionHeightSet = true
                        moveSuggestionListToInitialPos()
                        suggestionSecHeightListener?.invoke()
                        suggestionSecHeightListener = null
                    }
                }
            })
            
            isInitialLayout = false
            
            refreshDimBackground()
            
            if (isInEditMode) inflateOverflowMenu(menuId)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        //remove any ongoing animations to prevent leaks
        //todo investigate if correct
        suggestions_list_container.animate().cancel()
    }

    class SavedState : BaseSavedState {
        var suggestions: List<SearchSuggestion?> = mutableListOf()
        var isSearchFocused = false
        var query: String? = null
        var queryTextSize = 0
        var suggestionTextSize = 0
        var searchHint: CharSequence? = null
        var dismissOnOutsideClick = false
        var showMoveUpSuggestion = false
        var showSearchKey = false
        var isTitleSet = false
        var menuId = 0
        var leftActionMode: LeftActionMode = LeftActionMode.NO_ACTION
        var dimBackground = false
        var suggestionAnimDuration: Long = 0
        var closeSearchOnKeyboardDismiss = false
        var dismissFocusOnSuggestionItemClick = false

        constructor(superState: Parcelable?) : super(superState)
        private constructor(`in`: Parcel) : super(`in`) {
            with(`in`) {
                readList(suggestions, javaClass.classLoader)
                isSearchFocused = readInt() != 0
                query = readString()
                queryTextSize = readInt()
                suggestionTextSize = readInt()
                searchHint = readString()
                dismissOnOutsideClick = readInt() != 0
                showMoveUpSuggestion = readInt() != 0
                showSearchKey = readInt() != 0
                isTitleSet = readInt() != 0
                menuId = readInt()
                leftActionMode = readInt().let { value ->
                    LeftActionMode.values().firstOrNull {
                        it.value == value
                    } ?: LeftActionMode.NO_ACTION
                }
                dimBackground = readInt() != 0
                suggestionAnimDuration = readLong()
                closeSearchOnKeyboardDismiss = readInt() != 0
                dismissFocusOnSuggestionItemClick = readInt() != 0
            }
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            with(out) {
                writeList(suggestions)
                writeInt(if (isSearchFocused) 1 else 0)
                writeString(query)
                writeInt(queryTextSize)
                writeInt(suggestionTextSize)
                writeString(searchHint?.toString())
                writeInt(if (dismissOnOutsideClick) 1 else 0)
                writeInt(if (showMoveUpSuggestion) 1 else 0)
                writeInt(if (showSearchKey) 1 else 0)
                writeInt(if (isTitleSet) 1 else 0)
                writeInt(menuId)
                writeInt(leftActionMode.value)
                writeInt(if (dimBackground) 1 else 0)
                writeLong(suggestionAnimDuration)
                writeInt(if (closeSearchOnKeyboardDismiss) 1 else 0)
                writeInt(if (dismissFocusOnSuggestionItemClick) 1 else 0)
            }
        }

        companion object CREATOR : Parcelable.Creator<SavedState?> {
            override fun createFromParcel(`in`: Parcel): SavedState? {
                return SavedState(`in`)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
    override fun onSaveInstanceState(): Parcelable? =
        SavedState(super.onSaveInstanceState()).also {
            it.suggestions = suggestionsAdapter?.searchSuggestions ?: mutableListOf()
            it.isSearchFocused = isSearchFocused
            it.query = query
            it.suggestionTextSize = suggestionTextSize
            it.searchHint = searchHint
            it.dismissOnOutsideClick = dismissOnOutsideClick
            it.showMoveUpSuggestion = showMoveUpSuggestion
            it.showSearchKey = showSearchKey
            it.isTitleSet = isTitleSet
            it.menuId = menuId
            it.leftActionMode = leftActionMode
            it.queryTextSize = queryTextSize
            it.dimBackground = dimBackground
            it.closeSearchOnKeyboardDismiss = closeSearchOnKeyboardDismiss
            it.dismissFocusOnSuggestionItemClick = dismissFocusOnItemSelection
            it.suggestionAnimDuration = suggestionAnimDuration
        }

    override fun onRestoreInstanceState(state: Parcelable?) {
        (state as SavedState).let {
            super.onRestoreInstanceState(it.superState)

            isSearchFocused = it.isSearchFocused
            isTitleSet = it.isTitleSet
            menuId = it.menuId
            query = it.query ?: ""
            setSearchText(query)
            suggestionAnimDuration = it.suggestionAnimDuration
            suggestionTextSize = it.suggestionTextSize
            dismissOnOutsideClick = it.dismissOnOutsideClick
            showMoveUpSuggestion = it.showMoveUpSuggestion
            showSearchKey = it.showSearchKey
            searchHint = it.searchHint ?: ""
            leftActionMode = it.leftActionMode
            dimBackground = it.dimBackground
            closeSearchOnKeyboardDismiss = it.closeSearchOnKeyboardDismiss
            dismissFocusOnItemSelection = it.dismissFocusOnSuggestionItemClick
        }

        if (isSearchFocused) {
            backgroundDrawable.alpha = BACKGROUND_DRAWABLE_ALPHA_SEARCH_FOCUSED
            skipTextChangeEvent = true
            skipQueryFocusChangeEvent = true

            search_suggestions_section.visibility = View.VISIBLE

            suggestionSecHeightListener = {
                swapSuggestions(state.suggestions, false)
                suggestionSecHeightListener = null

                //todo refactor move to a better location
                transitionInLeftSection(false)
            }

            clear_btn.visibility = if (state.query.isNullOrEmpty())
                View.INVISIBLE
            else
                View.VISIBLE
            left_action.visibility = View.VISIBLE

            context.showSoftKeyboard(search_bar_text)
        }
    }
    //endregion
    
    //region public methods
    fun inflateOverflowMenu(menuId: Int) {
        this.menuId = menuId
        menu_view.reset(menuId, actionMenuAvailWidth())

        if (isSearchFocused) menu_view.hideIfRoomItems(false)
    }

    fun setSearchBarTitle(title: CharSequence?) {
        titleText = title.toString()
        isTitleSet = true
        search_bar_text.setText(title)
    }

    fun setSearchText(text: CharSequence?) {
        isTitleSet = false
        queryText = text
    }

    var menuIconProgress: Float
        get() = menuBtnDrawable.progress
        set(value) {
            menuBtnDrawable.progress = value
            if (value == 0F)
                closeMenu(false)
            else if (value == 1F)
                openMenu(false)
        }

    /**
     * Mimics a menu click that opens the menu. Useful for navigation
     * drawers when they open as a result of dragging.
     */
    fun openMenu(withAnim: Boolean) {
        menuOpen = true
        openMenuDrawable(menuBtnDrawable, withAnim)
        onMenuStatusChangeListener?.onMenuOpened()
    }

    /**
     * Mimics a menu click that closes. Useful when fo navigation
     * drawers when they close as a result of selecting and item.
     *
     * @param withAnim true, will close the menu button with
     * the  Material animation
     */
    fun closeMenu(withAnim: Boolean) {
        menuOpen = false
        closeMenuDrawable(menuBtnDrawable, withAnim)
        onMenuStatusChangeListener?.onMenuClosed()
    }

    fun clearSuggestions() = swapSuggestions(emptyList())

    fun swapSuggestions(newSearchSuggestions: List<SearchSuggestion?>) = swapSuggestions(
        newSearchSuggestions,
        true
    )

    fun setLeftMenuOpen(isOpen: Boolean) {
        menuOpen = isOpen
        menuBtnDrawable.progress = if (isOpen) 1F else 0F
    }

    fun showProgress() {
        left_action.visibility = View.GONE

        ViewCompat.animate(search_bar_search_progress)
            .withStartAction {
                with(search_bar_search_progress) {
                    alpha = 0F
                    visibility = View.VISIBLE
                }
            }.alpha(1F)
            .start()
    }

    fun hideProgress() {
        search_bar_search_progress.visibility = View.GONE

        ViewCompat.animate(left_action)
            .withStartAction {
                with(left_action) {
                    alpha = 0F
                    visibility = View.VISIBLE
                }
            }.alpha(1F)
            .start()
    }

    fun clearQuery() { search_bar_text.setText("") }

    fun setSearchFocused(focused: Boolean): Boolean =
        (!focused && this.isSearchFocused).also {
            if (focused != this.isSearchFocused && suggestionSecHeightListener == null) {
                if (isSuggestionsSectionHeightSet)
                    isSearchFocused = focused
                else
                    suggestionSecHeightListener = {
                        isSearchFocused = focused
                        suggestionSecHeightListener = null
                    }
            }
        }
    fun clearSearchFocus() { isSearchFocused = false }

    private val drawerListener = object: DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            menuIconProgress = slideOffset
        }

        override fun onDrawerClosed(drawerView: View) {}
        override fun onDrawerOpened(drawerView: View) {}
        override fun onDrawerStateChanged(newState: Int) {}
    }

    fun attachNavigationDrawerToMenuButton(drawerLayout: DrawerLayout) {
        drawerLayout.addDrawerListener(drawerListener)
        onMenuStatusChangeListener = object: OnMenuStatusChangeListener {
            override fun onMenuOpened() {
                drawerLayout.openDrawer(GravityCompat.START)
            }
            override fun onMenuClosed() {}
        }
    }
    //endregion

    //region private methods
    private fun actionMenuAvailWidth(): Int {
        return if (isInEditMode)
            search_query_section.measuredWidth / 2
        else
            search_query_section.width / 2
    }

    private fun toggleLeftMenu() {
        if (menuOpen)
            closeMenu(true)
        else
            openMenu(true)
    }

    private fun refreshLeftIcon() {
        val leftActionWidthAndMarginLeft = dpToPx(LEFT_MENU_WIDTH_AND_MARGIN_START_DP)

        with(left_action) {
            visibility = View.VISIBLE

            when (leftActionMode) {
                LeftActionMode.HAMBURGER -> setImageDrawable(menuBtnDrawable.also {
                    it.progress = MENU_BUTTON_PROGRESS_HAMBURGER
                })
                LeftActionMode.SEARCH -> setImageDrawable(iconSearch)
                LeftActionMode.HOME -> setImageDrawable(menuBtnDrawable.also {
                    it.progress = MENU_BUTTON_PROGRESS_ARROW
                })
                LeftActionMode.NO_ACTION -> visibility = View.INVISIBLE
            }
        }

        search_input_parent.translationX = (
            if (leftActionMode == LeftActionMode.NO_ACTION)
                -leftActionWidthAndMarginLeft
            else
                0
        ).toFloat()
    }
    
    private fun refreshDimBackground() {
        backgroundDrawable.alpha =
            if (dimBackground && isSearchFocused)
                BACKGROUND_DRAWABLE_ALPHA_SEARCH_FOCUSED
            else
                BACKGROUND_DRAWABLE_ALPHA_SEARCH_NOT_FOCUSED
    }

    private fun moveSuggestionListToInitialPos() {
        suggestions_list_container.translationY = -suggestions_list_container.height.toFloat()
    }

    private enum class BackgroundAnimation {
        FADE_IN,
        FADE_OUT
    }
    private fun animateBackground(fadeIn: BackgroundAnimation) {
        ValueAnimator.ofInt(
            *when (fadeIn) {
                BackgroundAnimation.FADE_IN ->
                    intArrayOf(
                        BACKGROUND_DRAWABLE_ALPHA_SEARCH_NOT_FOCUSED,
                        BACKGROUND_DRAWABLE_ALPHA_SEARCH_FOCUSED
                    )
                BackgroundAnimation.FADE_OUT ->
                    intArrayOf(
                        BACKGROUND_DRAWABLE_ALPHA_SEARCH_FOCUSED,
                        BACKGROUND_DRAWABLE_ALPHA_SEARCH_NOT_FOCUSED
                    )
            }
        ).apply {
            addUpdateListener {
                background.alpha = it.animatedValue as Int
            }
            duration = BACKGROUND_FADE_ANIM_DURATION
            start()
        }
    }

    //returns the cumulative height that the current suggestion items take up or the given max if the
    //results is >= max. The max option allows us to avoid doing unnecessary and potentially long calculations.
    private fun calculateSuggestionItemsHeight(suggestions: List<SearchSuggestion?>, max: Int): Int {
        var visibleItemsHeight = 0

        return min((0 until min(suggestions.size, suggestions_list.childCount)).map {
            (visibleItemsHeight + suggestions_list.getChildAt(it).height).also { acc ->
                visibleItemsHeight = acc
            }
        }.maxOrNull() ?: 0, max)
    }

    // TODO("WTF IS THIS")
    private fun updateSuggestionSectionHeight(
        newSearchSuggestions: List<SearchSuggestion>,
        withAnim: Boolean
    ): Boolean {
        val cardTopBottomShadowPadding: Int = search_query_section.radius.toInt()
        val cardRadiusSize: Int = search_query_section.radius.toInt()

        val visibleSuggestionHeight: Int = calculateSuggestionItemsHeight(
            newSearchSuggestions,
            suggestions_list_container.height
        )
        val diff: Int = suggestions_list_container.height - visibleSuggestionHeight
        val addedTranslationYForShadowOffsets =
            if (diff <= cardTopBottomShadowPadding) -(cardTopBottomShadowPadding - diff) else if (diff < suggestions_list_container.height - cardTopBottomShadowPadding) cardRadiusSize else 0
        val newTranslationY: Float = (-suggestions_list_container.height +
                visibleSuggestionHeight + addedTranslationYForShadowOffsets).toFloat()

        //todo go over
        val fullyInvisibleTranslationY: Float =
            -suggestions_list_container.height + cardRadiusSize.toFloat()

        suggestions_list_container.animate().cancel()
        if (withAnim) {
            ViewCompat.animate(suggestions_list_container)
                .setInterpolator(SUGGEST_ITEM_ADD_ANIM_INTERPOLATOR)
                .setDuration(suggestionAnimDuration)
                .translationY(newTranslationY)
                .setUpdateListener {
                    onSuggestionsListHeightChanged?.invoke(abs(it.translationY - fullyInvisibleTranslationY))
                }
                .setListener(object : ViewPropertyAnimatorListenerAdapter() {
                    override fun onAnimationCancel(view: View?) {
                        suggestions_list_container.translationY = newTranslationY
                    }
                }).start()
        } else {
            suggestions_list_container.translationY = newTranslationY
            onSuggestionsListHeightChanged?.invoke(abs(suggestions_list_container.translationY - fullyInvisibleTranslationY))
        }

        return suggestions_list_container.height == visibleSuggestionHeight
    }

    private fun swapSuggestions(newSearchSuggestions: List<SearchSuggestion?>, withAnim: Boolean) {

        val suggestions = newSearchSuggestions.filterNotNull()

        with(suggestions_list) {
            viewTreeObserver.addOnGlobalLayoutListener(object: ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    suggestions_list.removeGlobalLayoutObserver(this)
                    val isSuggestionItemsFillRecyclerView = updateSuggestionSectionHeight(suggestions, withAnim)

                    (suggestions_list.layoutManager as LinearLayoutManager).let {
                        it.reverseLayout = !isSuggestionItemsFillRecyclerView
                        if (!isSuggestionItemsFillRecyclerView)
                            suggestionsAdapter?.reverseList()
                    }
                    suggestions_list.alpha = 1F
                }
            })
            alpha = 0F
            adapter = suggestionsAdapter?.also {
                it.swapData(suggestions)
            }
        }

        divider.visibility = if (newSearchSuggestions.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun openMenuDrawable(drawerArrowDrawable: DrawerArrowDrawable, withAnim: Boolean) {
        if (withAnim) {
            ValueAnimator.ofFloat(0F, 1F).apply {
                duration = MENU_ICON_ANIM_DURATION
                addUpdateListener {
                    drawerArrowDrawable.progress = it.animatedValue as Float
                }
                start()
            }
        } else
            drawerArrowDrawable.progress = 1F
    }

    private fun closeMenuDrawable(drawerArrowDrawable: DrawerArrowDrawable, withAnim: Boolean) {
        if (withAnim) {
            ValueAnimator.ofFloat(1F, 0F).apply {
                addUpdateListener {
                    drawerArrowDrawable.progress = it.animatedValue as Float
                }
                duration = MENU_ICON_ANIM_DURATION
                start()
            }
        } else
            drawerArrowDrawable.progress = 0F
    }

    private fun changeIcon(imageView: ImageView, newIcon: Drawable?, withAnim: Boolean) {
        imageView.setImageDrawable(newIcon)

        if (withAnim)
            ViewCompat.animate(imageView)
                .withStartAction {
                    alpha = 0F
                }.alpha(1F)
                .start()
        else
            imageView.alpha = 1F
    }

    private fun transitionInLeftSection(withAnim: Boolean) {
        left_action.visibility = if (search_bar_search_progress.visibility != View.VISIBLE)
            View.VISIBLE
        else
            View.INVISIBLE

        when (leftActionMode) {
            LeftActionMode.HAMBURGER -> {
                openMenuDrawable(menuBtnDrawable, withAnim)
            }
            LeftActionMode.SEARCH -> {
                left_action.setImageDrawable(iconBackArrow)
                if (withAnim) {
                    ViewCompat.animate(left_action)
                        .withStartAction {
                            left_action.rotation = 45F
                            left_action.alpha = 0F
                        }
                        .setDuration(500)
                        .rotation(0F)
                        .alpha(1F)
                }
            }
            LeftActionMode.NO_ACTION -> {
                left_action.setImageDrawable(iconBackArrow)

                if (withAnim) {
                    search_input_parent.animate()
                        .setDuration(500)
                        .translationX(0F)

                    left_action.apply {
                        scaleX = .5F
                        scaleY = .5F
                        alpha = 0F
                        translationX = dpToPx(8).toFloat()

                        animate()
                            .setStartDelay(150)
                            .setDuration(500)
                            .translationX(1F)
                            .scaleX(1F)
                            .scaleY(1F)
                            .alpha(1F)
                    }
                } else
                    search_input_parent.translationX = 0F
            }
            else -> {
                //do nothing
            }
        }
    }

    private fun transitionOutLeftSection(withAnim: Boolean) {
        when (leftActionMode) {
            LeftActionMode.HAMBURGER -> closeMenuDrawable(menuBtnDrawable, withAnim)
            LeftActionMode.SEARCH -> changeIcon(left_action, iconSearch, withAnim)
            LeftActionMode.NO_ACTION -> {
                left_action.setImageDrawable(iconBackArrow)

                if (withAnim) {
                    search_input_parent.animate()
                        .setDuration(350)
                        .translationX(-dpToPx(LEFT_MENU_WIDTH_AND_MARGIN_START_DP).toFloat())

                    ViewCompat.animate(left_action)
                        .setDuration(300)
                        .withStartAction {
                            with(left_action) {
                                scaleX = .5F
                                scaleY = .5F
                                alpha = 0F
                                translationX = dpToPx(8).toFloat()
                            }
                        }
                        .translationX(1F)
                        .scaleX(1F)
                        .scaleY(1F)
                        .alpha(1F)
                } else
                    left_action.visibility = View.INVISIBLE
            }
            else -> {
                //do nothing
            }
        }
    }
    //endregion
}