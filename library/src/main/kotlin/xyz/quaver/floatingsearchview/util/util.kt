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

package xyz.quaver.floatingsearchview.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.showSoftKeyboard(editText: EditText) = CoroutineScope(Dispatchers.Main).launch {
    delay(100)
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .showSoftInput(editText, InputMethodManager.SHOW_FORCED)
}

fun Activity.closeSoftKeyboard() {
    currentFocus?.let {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(it.windowToken, 0)
    }
}

fun View.removeGlobalLayoutObserver(layoutListener: ViewTreeObserver.OnGlobalLayoutListener) {
    @Suppress("DEPRECATION")
    if (Build.VERSION.SDK_INT < 16)
        viewTreeObserver.removeGlobalOnLayoutListener(layoutListener)
    else
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
}

fun dpToPx(dp: Int): Int =
    (dp * Resources.getSystem().displayMetrics.density).toInt()

fun spToPx(sp: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), Resources.getSystem().displayMetrics).toInt()

val Context.hostActivity: Activity?
    get() {
        var context: Context = this

        while (context is ContextWrapper) {
            if (context is Activity) return context

            context = context.baseContext
        }

        return null
    }

@Deprecated("")
val Activity.screenHeight: Int
        get() = DisplayMetrics().also {
            windowManager.defaultDisplay.getMetrics(it)
        }.heightPixels