Floating Search View
=============
## Complete Kotlin/AndroidX port of [arimorty/FloatingSearchView](https://github.com/arimorty/floatingsearchview)

An implementation of a floating search box with search suggestions, also called persistent search bar.

![Alt text](https://github.com/arimorty/floatingsearchview/blob/master/images/150696.gif)
![Alt text](https://github.com/arimorty/floatingsearchview/blob/master/images/1506tq.gif)
![Alt text](https://github.com/arimorty/floatingsearchview/blob/master/images/1508kn.gif)

...


Usage
-----

1. In your dependencies, add
```
implementation 'xyz.quaver:floatingsearchview:1.0'
```
2. Add a FloatingSearchView to your view hierarchy, and make sure that it takes
   up the full width and height of the screen
3. Listen to query changes and provide suggestion items that implement SearchSuggestion

**Example:**

```xml
<xyz.quaver.floatingsearchview.FloatingSearchView
    android:id="@+id/floating_search_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:searchBarMarginLeft="@dimen/search_view_inset"
    app:searchBarMarginTop="@dimen/search_view_inset"
    app:searchBarMarginRight="@dimen/search_view_inset"
    app:searchHint="Search..."
    app:suggestionsAnimDuration="250"
    app:showSearchKey="false"
    app:leftActionMode="showHamburger"
    app:menu="@menu/menu_main"
    app:elevation="4dp"
    app:close_search_on_keyboard_dismiss="true"/>
```

### Kotlin
```kotlin
search_view.onQueryChangeListener = { oldQuery, newQuery -> {
    //get suggestions based on newQuery
  
    //pass them on to the search view
    search_view.swapSuggestions(newSuggestions);
}
```

### Java
```java
mSearchView.setOnQueryChangeListener((oldQuery, newQuery) -> {
    //get suggestions based on newQuery

    //pass them on to the search view
    mSearchView.swapSuggestions(newSuggestions);

    return Unit.INSTANCE;
});
```

**Left action mode:**

The left action can be configured as follows:

Add 
```xml
app:leftActionMode="[insert one of the options from table below]"
```

<table>
    <tr>
        <td>showHamburger</td>
        <td><img src="https://github.com/arimorty/floatingsearchview/blob/develop/images/vf2oi.gif"/></td>       
    </tr>    
    <tr>
       <td>showSearch</td>
       <td><img src="https://github.com/arimorty/floatingsearchview/blob/develop/images/vf91i.gif"/></td>        
    <tr>
        <td>showHome</td>
        <td><img src="https://github.com/arimorty/floatingsearchview/blob/develop/images/vf9cp.gif"/></td>       
    </tr>   
    <tr>
        <td>noLeftAction</td>
        <td><img src="https://github.com/arimorty/floatingsearchview/blob/develop/images/vf2ii.gif"/></td>       
    </tr>
</table>

Listen to *hamburger* button clicks:
```kotlin
 search_view.onMenuClickListener = object: FloatingSearchView.OnLeftMenuClickListener {
     ...
 }        
```

To quickly connect your **NavigationDrawer** to the *hamburger* button:
```kotlin
menu_view.attachNavigationDrawerToMenuButton(mDrawerLayout);
```

Listen to home (back arrow) button clicks:
```kotlin
menu_view.setOnHomeActionClickListener(
      new FloatingSearchView.OnHomeActionClickListener() { ... });       
```

<br/>

**Configure menu items:**

![Alt text](https://github.com/arimorty/floatingsearchview/blob/master/images/150sg9.gif)

Add a menu resource
```xml
app:=menu="@menu/menu_main"
```

In the menu resource, set items' ```app:showAsAction="[insert one of the options described in the table below]"```

<table>
    <tr>
        <td>never</td>
        <td>Puts the menu item in the overflow options popup</td>
    </tr>
    <tr>
       <td>ifRoom</td>
       <td>Shows an action icon for the menu if the following conditions are met:
       1. The search is not focused.
       2. There is enough room for it.
       </td>
    </tr>
    <tr>
        <td>always</td>
        <td>Shows an action icon for the menu if there is room, regardless of whether the search is focused or not.</td>
    </tr>   
</table>

Listen for item selections 
```kotlin
search_view.onMenuItemClickListener = { item ->

}
```

**Configure suggestion item:**

First, implement [SearchSuggestion](https://github.com/tom5079/floatingsearchview/blob/master/library/src/main/kotlin/xyz/quaver/floatingsearchview/suggestions/model/SearchSuggestion.java) 

*Optional*:

Set a callback for when a given suggestion is bound to the suggestion list.

For the history icons to show, you would need to implement this. Refer to the sample app for an [example implementation](https://github.com/tom5079/FloatingSearchView/blob/master/app/src/main/java/xyz/quaver/floatingsearchview/sample/fragment/ScrollingSearchExampleFragment.java#L222).
``` 
search_view.onBindSuggestionCallback = { suggestionView, leftIcon, textView, item, itemPosition ->
  //here you can set some attributes for the suggestion's left icon and text. For example,
  //you can choose your favorite image-loading library for setting the left icon's image.
});
``` 

**Styling:**  
Styling is currently not supported.

License
=======
    tom5079/FloatingSearchView was ported from arimorty/FloatingSearchView

    Copyright 2015 Ari C.
    Copyright 2020 tom5079

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
