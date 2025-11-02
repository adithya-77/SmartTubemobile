package com.liskovsoft.smartyoutubetv2.tv.ui.browse;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.app.HeadersSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.PageRow;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.PresenterSelector;
import androidx.leanback.widget.TitleHelper;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.service.VideoStateService.State;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SplashPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.presenter.IconHeaderItemPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.browse.dialog.ErrorDialogFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.headers.ExtendedHeadersSupportFragment;
import com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc.ProgressBarManager;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.browse.NavigateTitleView;

import java.util.HashMap;
import java.util.Map;

/*
 * Main class to show BrowseFragment with header and rows of videos
 */
public class BrowseFragment extends BrowseSupportFragment implements BrowseView {
    private static final String TAG = BrowseFragment.class.getSimpleName();
    private static final String SELECTED_HEADER_INDEX = "SelectedHeaderIndex";
    private static final String SELECTED_VIDEO = "SelectedVideo";
    private static final String IS_PLAYER_IN_FOREGROUND = "IsPlayerInForeground";
    private ArrayObjectAdapter mSectionRowAdapter;
    private BrowsePresenter mBrowsePresenter;
    private Map<Integer, BrowseSection> mSections;
    private BrowseSectionFragmentFactory mSectionFragmentFactory;
    private Handler mHandler;
    private ProgressBarManager mProgressBarManager;
    private NavigateTitleView mTitleView;
    private boolean mIsFragmentCreated;
    private int mSelectedHeaderIndex = -1;
    private Video mSelectedVideo;
    private boolean mIsPlayerInForeground;
    private boolean mFocusOnContent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);

        if (savedInstanceState != null) {
            mSelectedHeaderIndex = savedInstanceState.getInt(SELECTED_HEADER_INDEX, -1);
            mSelectedVideo = Video.fromString(savedInstanceState.getString(SELECTED_VIDEO));
            mIsPlayerInForeground = savedInstanceState.getBoolean(IS_PLAYER_IN_FOREGROUND, false);
        }
        mIsFragmentCreated = true;

        mSections = new HashMap<>();
        mHandler = new Handler();
        mBrowsePresenter = BrowsePresenter.instance(getContext());
        mBrowsePresenter.setView(this);
        mProgressBarManager = new ProgressBarManager();

        setupAdapter();
        setupFragmentFactory();
        setupUi();

        enableMainFragmentScaling(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Store position in case activity is crashed
        outState.putInt(SELECTED_HEADER_INDEX, getSelectedPosition());
        if (mBrowsePresenter.getCurrentVideo() != null) {
            outState.putString(SELECTED_VIDEO, mBrowsePresenter.getCurrentVideo().toString());
            outState.putBoolean(IS_PLAYER_IN_FOREGROUND, ViewManager.instance(getContext()).isPlayerInForeground());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = super.onCreateView(inflater, container, savedInstanceState);

        mProgressBarManager.setRootView((ViewGroup) root);
        mTitleView = root.findViewById(R.id.browse_title_group);

        return root;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupEventListeners();

        // Hide the sidebar headers - we're using custom navigation buttons instead
        View view = getView();
        if (view != null) {
            // Hide headers dock and root - we're using custom navigation buttons
            View headersDock = view.findViewById(androidx.leanback.R.id.browse_headers_dock);
            if (headersDock != null) {
                headersDock.setVisibility(View.GONE);
            }
            View headersRoot = view.findViewById(androidx.leanback.R.id.browse_headers_root);
            if (headersRoot != null) {
                headersRoot.setVisibility(View.GONE);
            }
            
            // Hide headers fragment
            HeadersSupportFragment headersFragment = getHeadersSupportFragment();
            if (headersFragment != null) {
                View headersFragmentView = headersFragment.getView();
                if (headersFragmentView != null) {
                    headersFragmentView.setVisibility(View.GONE);
                }
            }
            
            view.post(() -> {
                // Setup custom navigation buttons - inflate and add programmatically
                // Do this after view is laid out to ensure proper focus handling
                setupNavigationButtons(view);
                
                // Try to give initial focus to home button if no focus exists
                View navContainer = view.findViewById(R.id.navigation_buttons_container);
                if (navContainer != null) {
                    View homeBtn = navContainer.findViewById(R.id.btn_home);
                    if (homeBtn != null && view.findFocus() == null) {
                        homeBtn.post(() -> {
                            if (homeBtn.requestFocus()) {
                                // Focus successfully set
                            }
                        });
                    }
                }
            });
            
            view.post(() -> {
                // Setup focus handling for custom navigation buttons
                androidx.leanback.widget.BrowseFrameLayout browseFrame = 
                    view.findViewById(androidx.leanback.R.id.browse_frame);
                if (browseFrame != null) {
                    // Set up focus search listener to handle left arrow from content to buttons
                    browseFrame.setOnFocusSearchListener(
                        new androidx.leanback.widget.BrowseFrameLayout.OnFocusSearchListener() {
                            @Override
                            public android.view.View onFocusSearch(android.view.View focused, int direction) {
                                if (getChildFragmentManager().isDestroyed()) {
                                    return null;
                                }
                                
                                View navContainer = view.findViewById(R.id.navigation_buttons_container);
                                if (navContainer == null || navContainer.getVisibility() != View.VISIBLE) {
                                    return null;
                                }
                                
                                // If navigating left from content area, move focus to buttons
                                if (direction == android.view.View.FOCUS_LEFT) {
                                    if (focused != null && !isDescendantOf(focused, navContainer)) {
                                        // Focus is in content area, move to buttons
                                        View homeBtn = navContainer.findViewById(R.id.btn_home);
                                        if (homeBtn != null && homeBtn.isFocusable()) {
                                            return homeBtn;
                                        }
                                    }
                                }
                                
                                // If navigating right from buttons, move focus to content
                                if (direction == android.view.View.FOCUS_RIGHT) {
                                    if (focused != null && isDescendantOf(focused, navContainer)) {
                                        // Focus is on buttons, move to content
                                        View containerDock = view.findViewById(androidx.leanback.R.id.browse_container_dock);
                                        if (containerDock != null) {
                                            // Find first focusable view in the content area
                                            View firstFocusable = containerDock.findFocus();
                                            if (firstFocusable != null) {
                                                // Already has focus, find next focusable to the right
                                                View nextFocus = firstFocusable.focusSearch(android.view.View.FOCUS_RIGHT);
                                                if (nextFocus != null) {
                                                    return nextFocus;
                                                }
                                            }
                                            // Try to find first focusable view in the container
                                            firstFocusable = findFirstFocusable(containerDock);
                                            if (firstFocusable != null) {
                                                return firstFocusable;
                                            }
                                        }
                                    }
                                }
                                
                                return null; // Use default focus search
                            }
                            
                            private boolean isDescendantOf(View child, View parent) {
                                if (child == null || parent == null) return false;
                                View current = child;
                                while (current != null) {
                                    if (current == parent) return true;
                                    if (current.getParent() instanceof View) {
                                        current = (View) current.getParent();
                                    } else {
                                        break;
                                    }
                                }
                                return false;
                            }
                            
                            private View findFirstFocusable(View root) {
                                if (root == null) return null;
                                if (root.isFocusable() && root.isFocusableInTouchMode()) {
                                    return root;
                                }
                                if (root instanceof ViewGroup) {
                                    ViewGroup group = (ViewGroup) root;
                                    for (int i = 0; i < group.getChildCount(); i++) {
                                        View child = group.getChildAt(i);
                                        View focusable = findFirstFocusable(child);
                                        if (focusable != null) {
                                            return focusable;
                                        }
                                    }
                                }
                                return null;
                            }
                        });
                    
                    browseFrame.setOnChildFocusListener(
                        new androidx.leanback.widget.BrowseFrameLayout.OnChildFocusListener() {
                            @Override
                            public boolean onRequestFocusInDescendants(int direction, 
                                    android.graphics.Rect previouslyFocusedRect) {
                                if (getChildFragmentManager().isDestroyed()) {
                                    return true;
                                }
                                
                                // Check if navigation buttons container is currently focused
                                View navContainer = view.findViewById(R.id.navigation_buttons_container);
                                View currentlyFocused = view.findFocus();
                                
                                // If navigating left and we're in content area, focus on buttons
                                if (direction == android.view.View.FOCUS_LEFT && 
                                    navContainer != null && 
                                    navContainer.getVisibility() == View.VISIBLE &&
                                    (currentlyFocused == null || 
                                     (currentlyFocused.getParent() != navContainer && 
                                      !isDescendantOf(currentlyFocused, navContainer)))) {
                                    android.widget.ImageButton btn = navContainer.findViewById(R.id.btn_home);
                                    if (btn != null && btn.requestFocus(direction, previouslyFocusedRect)) {
                                        return true;
                                    }
                                }
                                
                                // If already on a button, allow normal focus navigation
                                if (currentlyFocused != null && 
                                    navContainer != null &&
                                    (currentlyFocused.getParent() == navContainer || isDescendantOf(currentlyFocused, navContainer))) {
                                    // Let the button handle its own focus navigation
                                    return false;
                                }
                                
                                // Allow main content to receive focus
                                androidx.fragment.app.Fragment mainFragment = getMainFragment();
                                if (mainFragment != null && mainFragment.getView() != null
                                        && mainFragment.getView().requestFocus(direction, previouslyFocusedRect)) {
                                    return true;
                                }
                                return false;
                            }
                            
                            private boolean isDescendantOf(View child, View parent) {
                                if (child == null || parent == null) return false;
                                View current = child;
                                while (current != null) {
                                    if (current == parent) return true;
                                    if (current.getParent() instanceof View) {
                                        current = (View) current.getParent();
                                    } else {
                                        break;
                                    }
                                }
                                return false;
                            }

                            @Override
                            public void onRequestChildFocus(android.view.View child, android.view.View focused) {
                                // Ensure navigation buttons stay visible
                                if (!getChildFragmentManager().isDestroyed()) {
                                    int childId = child.getId();
                                    // When navigation buttons get focus, hide backdrop if needed
                                    if (childId == R.id.navigation_buttons_container || 
                                        (focused != null && focused.getParent() != null && 
                                         focused.getParent().equals(view.findViewById(R.id.navigation_buttons_container)))) {
                                        updateBackdropVisibility();
                                    }
                                    // When content gets focus, ensure navigation buttons are visible
                                    if (childId == androidx.leanback.R.id.browse_container_dock) {
                                        View navContainer = view.findViewById(R.id.navigation_buttons_container);
                                        if (navContainer != null) {
                                            navContainer.setVisibility(View.VISIBLE);
                                            navContainer.setAlpha(1.0f);
                                        }
                                    }
                                }
                            }
                        });
                }
            });
        }

        prepareEntranceTransition();

        // Ensure navigation buttons are visible and headers are hidden
        View view2 = getView();
        if (view2 != null) {
            view2.post(() -> {
                ensureNavigationButtonsVisible();
            });
            view2.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 100);
            view2.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 300);
            view2.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 600);
        }

        mBrowsePresenter.onViewInitialized();

        if (mSelectedHeaderIndex != -1) {
            // Restore state after crash
            selectSection(mSelectedHeaderIndex, true);
            mSelectedHeaderIndex = -1;

            // Restore state after crash
            selectSectionItem(mSelectedVideo);
            if (PlaybackPresenter.instance(getContext()).getPlayer() == null && mIsPlayerInForeground) {
                VideoStateService stateService = VideoStateService.instance(getContext());
                boolean isVideoStateSynced = mSelectedVideo == null || stateService.getByVideoId(mSelectedVideo.videoId) != null;
                State lastState = stateService.getLastState();
                PlaybackPresenter.instance(getContext()).openVideo(lastState != null && isVideoStateSynced ? lastState.video : mSelectedVideo);
            }
            mSelectedVideo = null;
        }
    }

    @Override
    public HeadersSupportFragment onCreateHeadersSupportFragment() {
        HeadersSupportFragment headersFragment = new ExtendedHeadersSupportFragment();
        // Headers will be hidden in ensureNavigationButtonsVisible()
        // We need to return the fragment to keep structure intact
        return headersFragment;
    }

    /**
     * Setup custom navigation buttons (Home, Playlist, History, Settings)
     */
    private void setupNavigationButtons(View rootView) {
        // Find the browse_frame to add buttons to
        android.view.View browseFrame = rootView.findViewById(androidx.leanback.R.id.browse_frame);
        if (!(browseFrame instanceof ViewGroup)) {
            return;
        }
        ViewGroup parentContainer = (ViewGroup) browseFrame;
        
        // Check if container already exists
        View container = rootView.findViewById(R.id.navigation_buttons_container);
        if (container == null) {
            // Inflate and add the navigation buttons layout
            LayoutInflater inflater = LayoutInflater.from(getContext());
            container = inflater.inflate(R.layout.custom_navigation_buttons, parentContainer, false);
            if (container != null) {
                // Set layout params to position at left edge
                ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                    ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                    ViewGroup.MarginLayoutParams.MATCH_PARENT
                );
                if (container.getLayoutParams() instanceof android.widget.FrameLayout.LayoutParams) {
                    android.widget.FrameLayout.LayoutParams frameParams = 
                        (android.widget.FrameLayout.LayoutParams) container.getLayoutParams();
                    frameParams.gravity = android.view.Gravity.START | android.view.Gravity.TOP;
                    frameParams.width = (int) (80 * getResources().getDisplayMetrics().density);
                    frameParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    frameParams.leftMargin = (int) (-2 * getResources().getDisplayMetrics().density);
                    container.setLayoutParams(frameParams);
                }
                parentContainer.addView(container);
            }
        }
        
        if (container == null) {
            return;
        }
        
        // Ensure container is visible and on top
        container.setVisibility(View.VISIBLE);
        container.setAlpha(1.0f);
        container.bringToFront();
        parentContainer.bringChildToFront(container);
        
        // Make container focusable so it can handle focus events
        if (container instanceof ViewGroup) {
            ((ViewGroup) container).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        
        android.widget.ImageButton btnHome = container.findViewById(R.id.btn_home);
        android.widget.ImageButton btnPlaylist = container.findViewById(R.id.btn_playlist);
        android.widget.ImageButton btnHistory = container.findViewById(R.id.btn_history);
        android.widget.ImageButton btnSettings = container.findViewById(R.id.btn_settings);
        
        // Setup button focus order
        android.view.View.OnFocusChangeListener buttonFocusListener = (v, hasFocus) -> {
            if (v instanceof android.widget.ImageButton) {
                updateButtonHighlight((android.widget.ImageButton) v, hasFocus);
            }
        };
        
        // Load icons from common module and setup buttons
        if (btnHome != null) {
            btnHome.setVisibility(View.VISIBLE);
            btnHome.setAlpha(1.0f);
            Drawable homeIcon = ContextCompat.getDrawable(getContext(), com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_home);
            if (homeIcon != null) {
                btnHome.setImageDrawable(homeIcon);
            }
            btnHome.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            btnHome.setOnClickListener(v -> navigateToSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_HOME));
            btnHome.setOnFocusChangeListener(buttonFocusListener);
            btnHome.setFocusable(true);
            btnHome.setFocusableInTouchMode(true);
            btnHome.setClickable(true);
            // Set next focus
            btnHome.setNextFocusDownId(R.id.btn_playlist);
            btnHome.setNextFocusUpId(R.id.btn_settings);
            // Don't set NextFocusRightId - let OnFocusSearchListener handle it
        }
        
        if (btnPlaylist != null) {
            btnPlaylist.setVisibility(View.VISIBLE);
            btnPlaylist.setAlpha(1.0f);
            Drawable playlistIcon = ContextCompat.getDrawable(getContext(), com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_playlist);
            if (playlistIcon != null) {
                btnPlaylist.setImageDrawable(playlistIcon);
            }
            btnPlaylist.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            btnPlaylist.setOnClickListener(v -> navigateToSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_USER_PLAYLISTS));
            btnPlaylist.setOnFocusChangeListener(buttonFocusListener);
            btnPlaylist.setFocusable(true);
            btnPlaylist.setFocusableInTouchMode(true);
            btnPlaylist.setClickable(true);
            // Set next focus
            btnPlaylist.setNextFocusDownId(R.id.btn_history);
            btnPlaylist.setNextFocusUpId(R.id.btn_home);
            // Don't set NextFocusRightId - let OnFocusSearchListener handle it
        }
        
        if (btnHistory != null) {
            btnHistory.setVisibility(View.VISIBLE);
            btnHistory.setAlpha(1.0f);
            Drawable historyIcon = ContextCompat.getDrawable(getContext(), com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_history);
            if (historyIcon != null) {
                btnHistory.setImageDrawable(historyIcon);
            }
            btnHistory.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            btnHistory.setOnClickListener(v -> navigateToSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_HISTORY));
            btnHistory.setOnFocusChangeListener(buttonFocusListener);
            btnHistory.setFocusable(true);
            btnHistory.setFocusableInTouchMode(true);
            btnHistory.setClickable(true);
            // Set next focus
            btnHistory.setNextFocusDownId(R.id.btn_settings);
            btnHistory.setNextFocusUpId(R.id.btn_playlist);
            // Don't set NextFocusRightId - let OnFocusSearchListener handle it
        }
        
        if (btnSettings != null) {
            btnSettings.setVisibility(View.VISIBLE);
            btnSettings.setAlpha(1.0f);
            Drawable settingsIcon = ContextCompat.getDrawable(getContext(), com.liskovsoft.smartyoutubetv2.common.R.drawable.icon_settings);
            if (settingsIcon != null) {
                btnSettings.setImageDrawable(settingsIcon);
            }
            btnSettings.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            btnSettings.setOnClickListener(v -> navigateToSection(com.liskovsoft.mediaserviceinterfaces.data.MediaGroup.TYPE_SETTINGS));
            btnSettings.setOnFocusChangeListener(buttonFocusListener);
            btnSettings.setFocusable(true);
            btnSettings.setFocusableInTouchMode(true);
            btnSettings.setClickable(true);
            // Set next focus - wrap around
            btnSettings.setNextFocusDownId(R.id.btn_home);
            btnSettings.setNextFocusUpId(R.id.btn_history);
            // Don't set NextFocusRightId - let OnFocusSearchListener handle it
        }
        
        // Update button highlights based on current section
        updateNavigationButtonStates();
    }
    
    /**
     * Navigate to a specific section
     */
    private void navigateToSection(int sectionId) {
        com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter.instance(getContext()).selectSection(sectionId);
        updateBackdropVisibility();
        // Update button states after navigation
        View rootView = getView();
        if (rootView != null) {
            rootView.postDelayed(() -> updateNavigationButtonStates(), 100);
        }
    }
    
    /**
     * Update button highlight based on focus
     */
    private void updateButtonHighlight(android.widget.ImageButton button, boolean hasFocus) {
        if (button == null) return;
        
        float scale = hasFocus ? 1.1f : 1.0f;
        button.setScaleX(scale);
        button.setScaleY(scale);
        
        float alpha = hasFocus ? 1.0f : 0.7f;
        button.setAlpha(alpha);
    }
    
    /**
     * Update navigation button states based on current section
     */
    private void updateNavigationButtonStates() {
        View rootView = getView();
        if (rootView == null) return;
        
        View container = rootView.findViewById(R.id.navigation_buttons_container);
        if (container == null) return;
        
        com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter bp = 
            com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter.instance(getContext());
        
        android.widget.ImageButton btnHome = container.findViewById(R.id.btn_home);
        android.widget.ImageButton btnPlaylist = container.findViewById(R.id.btn_playlist);
        android.widget.ImageButton btnHistory = container.findViewById(R.id.btn_history);
        android.widget.ImageButton btnSettings = container.findViewById(R.id.btn_settings);
        
        // Update highlight based on current section
        if (btnHome != null) {
            boolean isActive = bp.isHomeSection();
            btnHome.setAlpha(isActive ? 1.0f : 0.7f);
        }
        if (btnPlaylist != null) {
            boolean isActive = bp.isPlaylistsSection();
            btnPlaylist.setAlpha(isActive ? 1.0f : 0.7f);
        }
        if (btnHistory != null) {
            boolean isActive = bp.isHistorySection();
            btnHistory.setAlpha(isActive ? 1.0f : 0.7f);
        }
        if (btnSettings != null) {
            boolean isActive = bp.isSettingsSection();
            btnSettings.setAlpha(isActive ? 1.0f : 0.7f);
        }
    }

    private void setupEventListeners() {
        // Disable header listeners - we're using custom navigation buttons
        // Listen to header selection changes to navigate to sections
        getHeadersSupportFragment().setOnHeaderViewSelectedListener(
                (viewHolder, row) -> {
                    if (row != null) {
                        long headerId = row.getHeaderItem().getId();
                        int position = getHeadersSupportFragment().getSelectedPosition();
                        if (position >= 0 && position < mSectionRowAdapter.size()) {
                            // Navigate to the selected section
                            selectSection(position, true);
                            mBrowsePresenter.onSectionFocused((int) headerId);
                            updateBackdropVisibility();
                        }
                    }
                }
        );
        
        getHeadersSupportFragment().setOnHeaderClickedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();
                    int newPosition = indexOf(headerId);

                    if (getSelectedPosition() != newPosition) {
                        // touch screen support - use parent's setSelectedPosition to ensure fragment replacement
                        setSelectedPosition(newPosition, true);
                    } else {
                        // update section when clicked or pressed on already selected section
                        mBrowsePresenter.onSectionFocused((int) headerId);
                        updateBackdropVisibility();
                        startHeadersTransitionSafe(false);
                    }
                }
        );

        ((ExtendedHeadersSupportFragment) getHeadersSupportFragment()).setOnHeaderLongPressedListener(
                (viewHolder, row) -> {
                    long headerId = row.getHeaderItem().getId();

                    mBrowsePresenter.onSectionLongPressed((int) headerId);
                }
        );

        setOnSearchClickedListener(view -> SearchPresenter.instance(getActivity()).startSearch(null));
        
        // Listen to headers transition to update backdrop visibility
        setBrowseTransitionListener(new BrowseSupportFragment.BrowseTransitionListener() {
            @Override
            public void onHeadersTransitionStart(boolean withHeaders) {
                // Update backdrop when transition starts
                updateBackdropVisibility();
            }

            @Override
            public void onHeadersTransitionStop(boolean withHeaders) {
                // Ensure backdrop is updated when transition completes
                updateBackdropVisibility();
            }
        });
    }

    private void setupFragmentFactory() {
        mSectionFragmentFactory = new BrowseSectionFragmentFactory(
                (row) -> {
                    focusOnContentIfNeeded();
                    mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
                    updateBackdropVisibility();
                    // Update navigation button states when section changes
                    updateNavigationButtonStates();
                }
        );

        getMainFragmentRegistry().registerFragment(PageRow.class, mSectionFragmentFactory);
    }

    private int indexOf(long headerId) {
        for (int i = 0; i < mSectionRowAdapter.size(); i++) {
            PageRow row = (PageRow) mSectionRowAdapter.get(i);
            HeaderItem header = row.getHeaderItem();
            if (header.getId() == headerId) {
                return i;
            }
        }

        return 0;
    }

    private void setupAdapter() {
        // Map category results from the database to ListRow objects.
        // This Adapter is used to render the MainFragment sidebar labels.
        mSectionRowAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        setAdapter(mSectionRowAdapter);
        
        // Register observer to ensure navigation buttons remain visible when adapter changes
        mSectionRowAdapter.registerObserver(new androidx.leanback.widget.ObjectAdapter.DataObserver() {
            @Override
            public void onChanged() {
                ensureNavigationButtonsVisible();
            }
            
            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                ensureNavigationButtonsVisible();
            }
            
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                ensureNavigationButtonsVisible();
            }
            
            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                ensureNavigationButtonsVisible();
            }
        });
    }

    private void setupUi() {
        // Keep headers enabled for fragment structure, but we'll hide them visually
        // HEADERS_DISABLED breaks the fragment, so we use HEADERS_ENABLED and hide visually
        setHeadersState(HEADERS_ENABLED);
        // Disable headers transition on back button - we're using custom navigation buttons
        setHeadersTransitionOnBackEnabled(false);

        int brandColorRes = Helpers.getThemeAttr(getActivity(), R.attr.brandColor);
        int brandAccentColorRes = Helpers.getThemeAttr(getActivity(), R.attr.brandAccentColor);
        int appLogoRes = Helpers.getThemeAttr(getActivity(), R.attr.appLogo);

        Drawable bridgeIcon = Utils.getDrawable(getActivity(), SplashPresenter.instance(getActivity()).getBridgePackageName(), "app_icon");

        // Top right corner logo
        setBadgeDrawable(bridgeIcon != null ? bridgeIcon : appLogoRes > 0 ? ContextCompat.getDrawable(getActivity(), appLogoRes) : null);

        // This title replaces badge in case one is null
        //setTitle(getString(R.string.browse_title));

        // Set fastLane (or headers) background color to transparent for overlay effect
        setBrandColor(ContextCompat.getColor(getActivity(), android.R.color.transparent));

        // Set search icon color.
        setSearchAffordanceColor(ContextCompat.getColor(getActivity(), brandAccentColorRes));

        setHeaderPresenterSelector(new PresenterSelector() {
            private final Map<Integer, Presenter> mPresenterMap = new HashMap<>();

            @Override
            public Presenter getPresenter(Object o) {
                Presenter presenter = mPresenterMap.get(o.hashCode());

                if (presenter == null) {
                    presenter = new IconHeaderItemPresenter(getHeaderResId(o), getIconUrl(o));
                    mPresenterMap.put(o.hashCode(), presenter);
                }

                return presenter;
            }

            private int getHeaderResId(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getResId();
                }

                return -1;
            }

            private String getIconUrl(Object o) {
                if (o instanceof PageRow) {
                    return ((SectionHeaderItem) ((PageRow) o).getHeaderItem()).getIconUrl();
                }

                return null;
            }
        });
    }

    private void updateBackdropVisibility() {
        View root = getView();
        if (root == null || getActivity() == null) return;
        View browseFrame = getActivity().findViewById(androidx.leanback.R.id.browse_frame);
        if (browseFrame == null) return;
        android.widget.ImageView backdrop = browseFrame.findViewById(androidx.leanback.R.id.backdrop_image);
        if (backdrop == null) return;

        // Hide backdrop if sidebar is showing
        if (isShowingHeaders()) {
            backdrop.setImageDrawable(null);
            backdrop.setVisibility(View.INVISIBLE);
            return;
        }

        boolean isHome = BrowsePresenter.instance(getContext()).isHomeSection();
        // Reset card/grid sizing caches so Home and non-Home recalc their exact spacing
        com.liskovsoft.smartyoutubetv2.tv.ui.browse.video.GridFragmentHelper.invalidateCaches();
        if (isHome) {
            backdrop.setVisibility(View.VISIBLE);
        } else {
            backdrop.setImageDrawable(null);
            backdrop.setVisibility(View.INVISIBLE); // Use INVISIBLE to maintain layout space
        }
    }

    private int getSelectedHeaderId() {
        if (getSelectedPosition() >= mSectionRowAdapter.size()) {
            return -1;
        }

        return (int) ((PageRow) mSectionRowAdapter.get(getSelectedPosition())).getHeaderItem().getId();
    }
    
    public void updateErrorIfEmpty(ErrorFragmentData data) {
        mHandler.postDelayed(() -> showErrorIfEmpty(data), 500); // need delay because header may be not updated
    }

    @Override
    public void showError(ErrorFragmentData data) {
        replaceMainFragment(new ErrorDialogFragment(data));
    }

    private void showErrorIfEmpty(ErrorFragmentData data) {
        if (isEmpty()) {
            replaceMainFragment(new ErrorDialogFragment(data));
        }
    }

    private void replaceMainFragment(Fragment fragment) {
        //Object mainFragment = Helpers.getField(this,"mMainFragment");
        Fragment mainFragment = getMainFragment();

        if (mainFragment != null && fragment != null && mainFragment != fragment) {
            Helpers.setField(this, "mMainFragment", fragment);

            FragmentTransaction ft = getChildFragmentManager().beginTransaction();
            ft.replace(R.id.scale_frame, fragment);
            //mFocusOnContent = !isShowingHeaders(); // Fix focus lost when error fragment shown and sidebar is hidden
            mFocusOnContent = hasFocus(); // Maintain focus
            ft.runOnCommit(() -> {
                focusOnContentIfNeeded();
                updateBackdropVisibility();
            });
            ft.commitAllowingStateLoss(); // FIX: "Can not perform this action after onSaveInstanceState"
        }
    }

    @Override
    public void addSection(int index, BrowseSection section) {
        if (section == null) {
            return;
        }

        if (mSections.get(section.getId()) != null && (index == -1 || indexOf(section.getId()) == index)) {
            return;
        }

        removeSection(section);

        mSections.put(section.getId(), section);
        createHeader(index, section);
        
        // Ensure headers remain visible after adding sections
        View view = getView();
        if (view != null) {
            view.post(() -> {
                ensureNavigationButtonsVisible();
            });
        }
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (section == null) {
            return;
        }

        mSections.remove(section.getId());
        removeHeader(section);
    }

    @Override
    public void removeAllSections() {
        mSections.clear();
        mSectionRowAdapter.clear();
        // Ensure headers remain visible when sections are cleared/refreshed
        View view = getView();
        if (view != null) {
            view.post(() -> {
                ensureNavigationButtonsVisible();
            });
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);

        fixInvisibleSearchOrb();
        
        // Ensure headers remain visible when section data is updated
        View view = getView();
        if (view != null) {
            view.post(() -> {
                ensureNavigationButtonsVisible();
            });
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        restoreMainFragment();

        mSectionFragmentFactory.updateCurrentFragment(group);
        
        // Ensure headers remain visible when section data is updated
        View view = getView();
        if (view != null) {
            view.post(() -> {
                ensureNavigationButtonsVisible();
            });
        }
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (index >= 0 && mSectionRowAdapter.size() > 0) {
            mFocusOnContent = focusOnContent; // focus after header transition

            // Fix refresh current section
            if (getSelectedPosition() == index) {
                // update section manually
                // headers transition event not fired on the same index
                focusOnContentIfNeeded();
                mBrowsePresenter.onSectionFocused(getSelectedHeaderId());
                // Update navigation button states
                View view = getView();
                if (view != null) {
                    view.post(() -> {
                        updateNavigationButtonStates();
                    });
                }
            }

            // Need select again if current header is removed previously (can't check for it right now)
            // Fallback to the last section if index above size
            setSelectedPosition(index < mSectionRowAdapter.size() ? index : mSectionRowAdapter.size() - 1, false);
        }
    }

    @Override
    public void focusOnContent() {
        startHeadersTransitionSafe(false);
        if (getMainFragment() != null && getMainFragment().getView() != null) {
            getMainFragment().getView().requestFocus();
        }
    }

    /**
     * Usually called after header transition or fragment transaction
     */
    private void focusOnContentIfNeeded() {
        if (mFocusOnContent) {
            focusOnContent();
            mFocusOnContent = false;
        }
    }

    private boolean hasFocus() {
        if (getMainFragment() == null || getMainFragment().getView() == null) {
            return false;
        }

        return getMainFragment().getView().hasFocus();
    }

    @Override
    public void selectSectionItem(int index) {
        if (index >= 0) {
            mSectionFragmentFactory.setCurrentFragmentItemIndex(index);
        }
    }

    @Override
    public void selectSectionItem(Video item) {
        if (item != null) {
            mSectionFragmentFactory.selectCurrentFragmentItem(item);
        }
    }

    /**
     * Fix: IllegalStateException: "Can not perform this action after onSaveInstanceState"
     */
    private void startHeadersTransitionSafe(boolean withHeaders) {
        // Ensure navigation buttons are visible and headers are hidden
        ensureNavigationButtonsVisible();
        // Update backdrop visibility
        updateBackdropVisibility();
    }

    /**
     * Restore after the error fragment
     */
    private void restoreMainFragment() {
        Fragment currentFragment = mSectionFragmentFactory.getCurrentFragment();

        if (currentFragment != null) {
            replaceMainFragment(currentFragment);
        }
    }

    private void createHeader(int index, BrowseSection header) {
        HeaderItem headerItem = new SectionHeaderItem(header);

        PageRow pageRow = new PageRow(headerItem);
        if (index == -1 || mSectionRowAdapter.size() < index) {
            mSectionRowAdapter.add(pageRow); // add to the end
        } else {
            mSectionRowAdapter.add(index, pageRow);
        }
    }

    private void removeHeader(BrowseSection header) {
        Object foundHeader = null;

        for (Object item : mSectionRowAdapter.unmodifiableList()) {
            if (((PageRow) item).getHeaderItem().getId() == header.getId()) {
                foundHeader = item;
                break;
            }
        }

        if (foundHeader != null) {
            mSectionRowAdapter.remove(foundHeader);
        }
    }

    @Override
    public void clearSection(BrowseSection section) {
        mSectionFragmentFactory.clearCurrentFragment();
    }

    @Override
    public void onDestroyView() {
        mSectionFragmentFactory.cleanup();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBrowsePresenter.onViewDestroyed();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewPaused();
        }
    }

    @Override
    protected void onEntranceTransitionEnd() {
        super.onEntranceTransitionEnd();
        // Ensure navigation buttons are visible after entrance transition completes
        ensureNavigationButtonsVisible();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        
        // Ensure navigation buttons are visible from the start
        View view = getView();
        if (view != null) {
            // Ensure immediately and multiple times to catch all transitions
            view.post(() -> {
                ensureNavigationButtonsVisible();
            });
            view.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 100);
            view.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 300);
            view.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 600);
            view.postDelayed(() -> {
                ensureNavigationButtonsVisible();
            }, 1000);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!mIsFragmentCreated) {
            mBrowsePresenter.onViewResumed();
        }

        mIsFragmentCreated = false;
        
        // Update navigation button states when resuming
        android.view.View view = getView();
        if (view != null) {
            view.post(() -> {
                updateNavigationButtonStates();
            });
        }
    }
    
    /**
     * Ensure navigation buttons are visible and headers are hidden
     */
    private void ensureNavigationButtonsVisible() {
        View view = getView();
        if (view == null || getActivity() == null) return;
        
        // Hide all header-related views visually (but keep structure intact)
        HeadersSupportFragment headersFragment = getHeadersSupportFragment();
        if (headersFragment != null) {
            View headersFragmentView = headersFragment.getView();
            if (headersFragmentView != null) {
                headersFragmentView.setVisibility(View.GONE);
                headersFragmentView.setAlpha(0f);
            }
            androidx.leanback.widget.VerticalGridView headersGridView = headersFragment.getVerticalGridView();
            if (headersGridView != null) {
                headersGridView.setVisibility(View.GONE);
                headersGridView.setAlpha(0f);
            }
        }
        
        View headersRoot = view.findViewById(androidx.leanback.R.id.browse_headers_root);
        if (headersRoot != null) {
            headersRoot.setVisibility(View.GONE);
            headersRoot.setAlpha(0f);
        }
        
        View headersDock = view.findViewById(androidx.leanback.R.id.browse_headers_dock);
        if (headersDock != null) {
            headersDock.setVisibility(View.GONE);
            headersDock.setAlpha(0f);
        }
        
        // Ensure navigation buttons are visible and positioned correctly
        View navContainer = view.findViewById(R.id.navigation_buttons_container);
        if (navContainer != null) {
            navContainer.setVisibility(View.VISIBLE);
            navContainer.setAlpha(1.0f);
            navContainer.bringToFront();
            // Ensure it's on top of everything
            ViewGroup parent = (ViewGroup) navContainer.getParent();
            if (parent != null) {
                parent.bringChildToFront(navContainer);
            }
        }
        
        // Also ensure main content is visible
        View browseContainer = view.findViewById(androidx.leanback.R.id.browse_container_dock);
        if (browseContainer != null) {
            browseContainer.setVisibility(View.VISIBLE);
            browseContainer.setAlpha(1.0f);
        }
        
        View scaleFrame = view.findViewById(androidx.leanback.R.id.scale_frame);
        if (scaleFrame != null) {
            scaleFrame.setVisibility(View.VISIBLE);
            scaleFrame.setAlpha(1.0f);
        }
    }

    /**
     * Fix suddenly invisible search orb<br/>
     * Could happen on topmost category when the page partially scrolled<br/>
     * More info: {@link TitleHelper}
     */
    private void fixInvisibleSearchOrb() {
        if (isShowingTitle() && getTitleView() != null && getTitleView().getVisibility() != View.VISIBLE) {
            getTitleView().setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showProgressBar(boolean show) {
        Runnable callback;

        if (show) {
            callback = mProgressBarManager::show;
        } else {
            callback = mProgressBarManager::hide;
        }

        // Essential. Need to run on the main thread.
        new Handler(Looper.getMainLooper()).post(callback);
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressBarManager.isShowing();
    }

    @Override
    public boolean isEmpty() {
        return mSectionFragmentFactory == null || mSectionFragmentFactory.isEmpty();
    }
}
