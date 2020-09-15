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

package xyz.quaver.floatingsearchview.util.view

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View.OnKeyListener
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText

class SearchInputView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = R.attr.editTextStyle) : AppCompatEditText(context, attrs, defStyle) {

    var onSearchKeyListener: (() -> Unit)? = null
    var onKeyboardDismissedListener: (() -> Unit)? = null

    private val onKeyListener = OnKeyListener { _, keyCode, _ ->
        (keyCode == KeyEvent.KEYCODE_ENTER && onSearchKeyListener != null).also {
            if (it) onSearchKeyListener?.invoke()
        }
    }

    init {
        setOnKeyListener(onKeyListener)
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK)
            onKeyboardDismissedListener?.invoke()

        return super.onKeyPreIme(keyCode, event)
    }

}