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

package xyz.quaver.floatingsearchview.sample.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import kotlin.Unit;
import xyz.quaver.floatingsearchview.FloatingSearchView;
import xyz.quaver.floatingsearchview.sample.R;
import xyz.quaver.floatingsearchview.sample.adapter.SearchResultsListAdapter;
import xyz.quaver.floatingsearchview.sample.data.ColorSuggestion;
import xyz.quaver.floatingsearchview.sample.data.ColorWrapper;
import xyz.quaver.floatingsearchview.sample.data.DataHelper;
import xyz.quaver.floatingsearchview.suggestions.model.SearchSuggestion;


public class SlidingSearchResultsExampleFragment extends BaseExampleFragment {
    private final String TAG = "BlankFragment";

    public static final long FIND_SUGGESTION_SIMULATED_DELAY = 250;

    private FloatingSearchView mSearchView;

    private RecyclerView mSearchResultsList;
    private SearchResultsListAdapter mSearchResultsAdapter;

    private boolean mIsDarkSearchTheme = false;

    private String mLastQuery = "";

    public SlidingSearchResultsExampleFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sliding_search_results_example_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSearchView = (FloatingSearchView) view.findViewById(R.id.floating_search_view);
        mSearchResultsList = (RecyclerView) view.findViewById(R.id.search_results_list);

        setupFloatingSearch();
        setupResultsList();
        setupDrawer();
    }

    private void setupFloatingSearch() {
        mSearchView.setOnQueryChangeListener((oldQuery, newQuery) -> {
            if (!oldQuery.equals("") && newQuery.equals("")) {
                mSearchView.clearSuggestions();
            } else {

                //this shows the top left circular progress
                //you can call it where ever you want, but
                //it makes sense to do it when loading something in
                //the background.
                mSearchView.showProgress();

                //simulates a query call to a data source
                //with a new query.
                DataHelper.findSuggestions(getActivity(), newQuery, 5,
                        FIND_SUGGESTION_SIMULATED_DELAY, new DataHelper.OnFindSuggestionsListener() {

                            @Override
                            public void onResults(List<ColorSuggestion> results) {

                                //this will swap the data and
                                //render the collapse/expand animations as necessary
                                mSearchView.swapSuggestions(results);

                                //let the users know that the background
                                //process has completed
                                mSearchView.hideProgress();
                            }
                        });
            }

            Log.d(TAG, "onSearchTextChanged()");

            return Unit.INSTANCE;
        });

        mSearchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSuggestionClicked(final SearchSuggestion searchSuggestion) {

                ColorSuggestion colorSuggestion = (ColorSuggestion) searchSuggestion;
                DataHelper.findColors(getActivity(), colorSuggestion.getBody(),
                        new DataHelper.OnFindColorsListener() {

                            @Override
                            public void onResults(List<ColorWrapper> results) {
                                mSearchResultsAdapter.swapData(results);
                            }

                        });
                Log.d(TAG, "onSuggestionClicked()");

                mLastQuery = searchSuggestion.getBody();
            }

            @Override
            public void onSearchAction(String query) {
                mLastQuery = query;

                DataHelper.findColors(getActivity(), query,
                        new DataHelper.OnFindColorsListener() {

                            @Override
                            public void onResults(List<ColorWrapper> results) {
                                mSearchResultsAdapter.swapData(results);
                            }

                        });
                Log.d(TAG, "onSearchAction()");
            }
        });

        mSearchView.setOnFocusChangeListener(new FloatingSearchView.OnFocusChangeListener() {
            @Override
            public void onFocus() {

                //show suggestions when search bar gains focus (typically history suggestions)
                mSearchView.swapSuggestions(DataHelper.getHistory(getActivity(), 3));

                Log.d(TAG, "onFocus()");
            }

            @Override
            public void onFocusCleared() {

                //set the title of the bar so that when focus is returned a new query begins
                mSearchView.setSearchBarTitle(mLastQuery);

                //you can also set setSearchText(...) to make keep the query there when not focused and when focus returns
                //mSearchView.setSearchText(searchSuggestion.getBody());

                Log.d(TAG, "onFocusCleared()");
            }
        });


        //handle menu clicks the same way as you would
        //in a regular activity
        mSearchView.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_change_colors) {

                mIsDarkSearchTheme = true;

                /* TODO
                //demonstrate setting colors for items
                mSearchView.setBackgroundColor(Color.parseColor("#787878"));
                mSearchView.setViewTextColor(Color.parseColor("#e9e9e9"));
                mSearchView.setHintTextColor(Color.parseColor("#e9e9e9"));
                mSearchView.setActionMenuOverflowColor(Color.parseColor("#e9e9e9"));
                mSearchView.setMenuItemIconColor(Color.parseColor("#e9e9e9"));
                mSearchView.setLeftActionIconColor(Color.parseColor("#e9e9e9"));
                mSearchView.setClearBtnColor(Color.parseColor("#e9e9e9"));
                mSearchView.setDividerColor(Color.parseColor("#BEBEBE"));
                mSearchView.setLeftActionIconColor(Color.parseColor("#e9e9e9"));*/
            } else {

                //just print action
                Toast.makeText(getActivity().getApplicationContext(), item.getTitle(),
                        Toast.LENGTH_SHORT).show();
            }

            return Unit.INSTANCE;
        });

        //use this listener to listen to menu clicks when app:leftAction="showHome"
        mSearchView.setOnHomeActionClickListener(() -> {
            Log.d(TAG, "onHomeClicked()");
            return Unit.INSTANCE;
        });

        /*
         * Here you have access to the left icon and the text of a given suggestion
         * item after as it is bound to the suggestion list. You can utilize this
         * callback to change some properties of the left icon and the text. For example, you
         * can load the left icon images using your favorite image loading library, or change text color.
         *
         *
         * Important:
         * Keep in mind that the suggestion list is a RecyclerView, so views are reused for different
         * items in the list.
         */
        mSearchView.setOnBindSuggestionCallback((suggestionView, leftIcon, textView, item, itemPosition) -> {
            ColorSuggestion colorSuggestion = (ColorSuggestion) item;

            String textColor = mIsDarkSearchTheme ? "#ffffff" : "#000000";
            String textLight = mIsDarkSearchTheme ? "#bfbfbf" : "#787878";

            if (colorSuggestion.getIsHistory()) {
                leftIcon.setImageDrawable(ResourcesCompat.getDrawable(getResources(),
                        R.drawable.history, null));

                /*TODO Util.setIconColor(leftIcon, Color.parseColor(textColor));*/
                leftIcon.setAlpha(.36f);
            } else {
                leftIcon.setAlpha(0.0f);
                leftIcon.setImageDrawable(null);
            }

            textView.setTextColor(Color.parseColor(textColor));
            String text = colorSuggestion.getBody()
                    .replaceFirst(mSearchView.getQuery(),
                            "<font color=\"" + textLight + "\">" + mSearchView.getQuery() + "</font>");
            textView.setText(Html.fromHtml(text));

            return Unit.INSTANCE;
        });

        //listen for when suggestion list expands/shrinks in order to move down/up the
        //search results list
        mSearchView.setOnSuggestionsListHeightChanged((newHeight) -> {
            mSearchResultsList.setTranslationY(newHeight);

            return Unit.INSTANCE;
        });

        /*
         * When the user types some text into the search field, a clear button (and 'x' to the
         * right) of the search text is shown.
         *
         * This listener provides a callback for when this button is clicked.
         */
        mSearchView.setOnClearSearchActionListener(() -> {
            Log.d(TAG, "onClearSearchClicked()");

            return Unit.INSTANCE;
        });
    }

    private void setupResultsList() {
        mSearchResultsAdapter = new SearchResultsListAdapter();
        mSearchResultsList.setAdapter(mSearchResultsAdapter);
        mSearchResultsList.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    @Override
    public boolean onActivityBackPress() {
        //if mSearchView.setSearchFocused(false) causes the focused search
        //to close, then we don't want to close the activity. if mSearchView.setSearchFocused(false)
        //returns false, we know that the search was already closed so the call didn't change the focus
        //state and it makes sense to call supper onBackPressed() and close the activity
        if (!mSearchView.setSearchFocused(false)) {
            return false;
        }
        return true;
    }

    private void setupDrawer() {
        attachSearchViewActivityDrawer(mSearchView);
    }

}
