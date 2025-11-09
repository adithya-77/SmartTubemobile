package com.liskovsoft.youtubeapi.service;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.youtubeapi.actions.ActionsService;
import com.liskovsoft.youtubeapi.actions.ActionsServiceWrapper;
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2;
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaGroup;
import com.liskovsoft.youtubeapi.browse.v2.BrowseService2Wrapper;
import com.liskovsoft.youtubeapi.common.models.impl.mediagroup.SuggestionsGroup;
import com.liskovsoft.youtubeapi.next.v2.WatchNextService;
import com.liskovsoft.youtubeapi.next.v2.WatchNextServiceWrapper;
import com.liskovsoft.youtubeapi.rss.RssService;
import com.liskovsoft.youtubeapi.search.SearchServiceWrapper;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.liskovsoft.youtubeapi.utils.UtilsService;
import com.liskovsoft.youtubeapi.browse.v1.BrowseService;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.youtubeapi.common.models.impl.mediagroup.BaseMediaGroup;
import com.liskovsoft.googlecommon.common.helpers.YouTubeHelper;
import com.liskovsoft.youtubeapi.search.SearchService;
import com.liskovsoft.youtubeapi.search.models.SearchResult;
import com.liskovsoft.youtubeapi.service.data.YouTubeMediaGroup;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class YouTubeContentService implements ContentService {
    private static final String TAG = YouTubeContentService.class.getSimpleName();
    private static YouTubeContentService sInstance;

    private YouTubeContentService() {
        Log.d(TAG, "Starting...");
    }

    public static ContentService instance() {
        if (sInstance == null) {
            sInstance = new YouTubeContentService();
        }

        return sInstance;
    }

    @Override
    public List<MediaGroup> getSearch(String searchText) {
        checkSigned();

        SearchResult search = getSearchService().getSearch(searchText);
        return YouTubeMediaGroup.from(search, MediaGroup.TYPE_SEARCH);
    }

    @Override
    public List<MediaGroup> getSearch(String searchText, int options) {
        checkSigned();

        SearchResult search = getSearchService().getSearch(searchText, options);
        return YouTubeMediaGroup.from(search, MediaGroup.TYPE_SEARCH);
    }

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText) {
        return RxHelper.fromCallable(() -> getSearch(searchText));
    }

    @Override
    public Observable<List<MediaGroup>> getSearchObserve(String searchText, int options) {
        return RxHelper.fromCallable(() -> getSearch(searchText, options));
    }

    @Override
    public List<String> getSearchTags(String searchText) {
        checkSigned();

        return getSearchService().getSearchTags(searchText);
    }

    @Override
    public Observable<List<String>> getSearchTagsObserve(String searchText) {
        return RxHelper.fromCallable(() -> getSearchTags(searchText));
    }

    @Override
    public MediaGroup getSubscriptions() {
        Log.d(TAG, "Getting subscriptions...");

        checkSigned();

        MediaGroup subscriptions = getBrowseService2().getSubscriptions();

        // TEMP fix. Subs not fully populated.
        if (subscriptions != null && subscriptions.getMediaItems() != null && subscriptions.getMediaItems().size() <= 5) {
            MediaGroup continuation = continueGroup(subscriptions);
            if (continuation == null || continuation.getMediaItems() == null || continuation.getMediaItems().isEmpty()) {
                if (getMediaServiceData() != null && !getMediaServiceData().isLegacyUIEnabled()) {
                    getMediaServiceData().setLegacyUIEnabled(true);
                    return getBrowseService2().getSubscriptions();
                }
            }
        }

        return subscriptions;
    }

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve() {
        return RxHelper.fromCallable(this::getSubscriptions);
    }

    @Override
    public MediaGroup getSubscriptions(String... channelIds) {
        checkSigned();

        return RssService.getFeed(channelIds);
    }

    @Override
    public Observable<MediaGroup> getSubscriptionsObserve(String... channelIds) {
        return RxHelper.fromCallable(() -> getSubscriptions(channelIds));
    }

    @Override
    public MediaGroup getSubscribedChannels() {
        checkSigned();

        return getBrowseService2().getSubscribedChannels();
    }

    @Override
    public MediaGroup getSubscribedChannelsByNewContent() {
        checkSigned();

        //List<GridTab> subscribedChannels = getBrowseService().getSubscribedChannelsUpdate();
        //return YouTubeMediaGroup.fromTabs(subscribedChannels, MediaGroup.TYPE_CHANNEL_UPLOADS);

        return getBrowseService2().getSubscribedChannelsByNewContent();
    }

    @Override
    public MediaGroup getSubscribedChannelsByName() {
        checkSigned();

        return getBrowseService2().getSubscribedChannelsByName();
    }

    @Override
    public MediaGroup getSubscribedChannelsByLastViewed() {
        checkSigned();

        return getBrowseService2().getSubscribedChannels();
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsObserve() {
        return RxHelper.fromCallable(this::getSubscribedChannels);
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNewContentObserve() {
        return RxHelper.fromCallable(this::getSubscribedChannelsByNewContent);
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByNameObserve() {
        return RxHelper.fromCallable(this::getSubscribedChannelsByName);
    }

    @Override
    public Observable<MediaGroup> getSubscribedChannelsByLastViewedObserve() {
        return RxHelper.fromCallable(this::getSubscribedChannelsByLastViewed);
    }

    @Override
    public MediaGroup getRecommended() {
        Log.d(TAG, "Getting recommended...");

        checkSigned();

        kotlin.Pair<List<MediaGroup>, String> home = getBrowseService2().getHome();

        List<MediaGroup> groups = home != null ? home.getFirst() : null;

        return groups != null && !groups.isEmpty() ? groups.get(0) : null;
    }

    @Override
    public Observable<MediaGroup> getRecommendedObserve() {
        return RxHelper.fromCallable(this::getRecommended);
    }

    @Override
    public MediaGroup getHistory() {
        Log.d(TAG, "Getting history...");

        checkSigned();

        return getBrowseService2().getHistory();
    }

    @Override
    public Observable<MediaGroup> getHistoryObserve() {
        return RxHelper.fromCallable(this::getHistory);
    }

    @Override
    public MediaGroup getGroup(String reloadPageKey) {
        return getBrowseService2().getGroup(reloadPageKey, MediaGroup.TYPE_UNDEFINED, null);
    }

    @Override
    public MediaGroup getGroup(MediaItem mediaItem) {
        return mediaItem.getReloadPageKey() != null ?
                getBrowseService2().getGroup(mediaItem.getReloadPageKey(), mediaItem.getType(), mediaItem.getTitle()) :
                getBrowseService2().getChannelAsGrid(mediaItem.getChannelId());
    }

    @Override
    public Observable<MediaGroup> getGroupObserve(MediaItem mediaItem) {
        return RxHelper.fromCallable(() -> getGroup(mediaItem));
    }

    @Override
    public Observable<MediaGroup> getGroupObserve(String reloadPageKey) {
        return RxHelper.fromCallable(() -> getGroup(reloadPageKey));
    }

    @Override
    public List<MediaGroup> getHome() {
        checkSigned();

        List<MediaGroup> result = new ArrayList<>();
        kotlin.Pair<List<MediaGroup>, String> home = getBrowseService2().getHome();
        List<MediaGroup> groups = home != null ? home.getFirst() : null;

        if (groups == null) {
            Log.e(TAG, "Home group is empty");
            return null;
        }

        for (MediaGroup group : groups) {
            // Load chips
            if (group != null && group.isEmpty()) {
                List<MediaGroup> sections = getBrowseService2().continueEmptyGroup(group);

                if (sections != null) {
                    result.addAll(sections);
                }
            } else if (group != null) {
                result.add(group);
            }
        }

        return result;
    }

    @Override
    public Observable<List<MediaGroup>> getHomeObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            // Custom: Show Watch Later content instead of regular home content
            emitGroups(emitter, getWatchLaterContent());
        });
    }
    
    /**
     * Get combined content from multiple playlists for custom Home section (loads all pages)
     * Includes: Watch Later, IMDb7, Movies T, Movies T1, and Temp
     */
    private List<MediaGroup> getWatchLaterContent() {
        try {
            Log.d(TAG, "Attempting to get content from multiple playlists for Home section");
            
            // List of playlist names to include
            String[] playlistNames = {"Watch Later", "IMDb7", "Movies T", "Movies T1", "Temp"};
            
            // Set to track video IDs to avoid duplicates
            Set<String> seenVideoIds = new HashSet<>();
            List<MediaItem> allVideos = new ArrayList<>();
            
            // Get all playlists
            MediaGroup playlists = getPlaylists();
            if (playlists == null || playlists.getMediaItems() == null) {
                Log.w(TAG, "No playlists found, falling back to home");
                kotlin.Pair<List<MediaGroup>, String> homeContent = getBrowseService2().getHome();
                return homeContent != null ? homeContent.getFirst() : null;
            }
            
            // Find and load content from each target playlist
            for (MediaItem playlist : playlists.getMediaItems()) {
                String playlistTitle = playlist.getTitle();
                String playlistChannelId = playlist.getChannelId();
                
                // Check if this playlist is one we want to include
                boolean shouldInclude = false;
                for (String targetName : playlistNames) {
                    if (targetName.equals(playlistTitle) || 
                        ("Watch Later".equals(targetName) && ("WL".equals(playlistChannelId) || "VLWL".equals(playlistChannelId)))) {
                        shouldInclude = true;
                        break;
                    }
                }
                
                if (shouldInclude) {
                    try {
                        Log.d(TAG, "Loading content from playlist: " + playlistTitle);
                        
                        // Try to get playlist content
                        kotlin.Pair<List<MediaGroup>, String> content = null;
                        
                        // For Watch Later, try channel IDs first
                        if ("Watch Later".equals(playlistTitle) || "WL".equals(playlistChannelId) || "VLWL".equals(playlistChannelId)) {
                            try {
                                content = getBrowseService2().getChannel("WL", null);
                                if (content == null || content.getFirst() == null || content.getFirst().isEmpty()) {
                                    content = getBrowseService2().getChannel("VLWL", null);
                                }
                            } catch (Exception e) {
                                // Continue to try with playlist channel ID
                            }
                        }
                        
                        // If channel ID method didn't work, use playlist channel ID
                        if (content == null || content.getFirst() == null || content.getFirst().isEmpty()) {
                            content = getBrowseService2().getChannel(playlistChannelId, playlist.getParams());
                        }
                        
                        if (content != null && content.getFirst() != null && !content.getFirst().isEmpty()) {
                            // Load all pages for this playlist
                            List<MediaGroup> playlistGroups = loadAllPages(content.getFirst());
                            
                            // Track videos added from this playlist
                            int videosBefore = allVideos.size();
                            
                            // Collect videos from all groups, avoiding duplicates
                            for (MediaGroup group : playlistGroups) {
                                if (group != null && group.getMediaItems() != null) {
                                    for (MediaItem video : group.getMediaItems()) {
                                        String videoId = video.getVideoId();
                                        if (videoId != null && !videoId.isEmpty() && !seenVideoIds.contains(videoId)) {
                                            seenVideoIds.add(videoId);
                                            allVideos.add(video);
                                        }
                                    }
                                }
                            }
                            
                            int videosAdded = allVideos.size() - videosBefore;
                            Log.d(TAG, "Loaded " + videosAdded + " new videos from " + playlistTitle + " (total unique: " + allVideos.size() + ")");
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error loading content from playlist " + playlistTitle + ": " + e.getMessage());
                    }
                }
            }
            
            // Create a single MediaGroup with all combined videos
            if (!allVideos.isEmpty()) {
                YouTubeMediaGroup combinedGroup = new YouTubeMediaGroup(MediaGroup.TYPE_HOME);
                combinedGroup.setTitle("Home");
                combinedGroup.setMediaItems(allVideos);
                
                List<MediaGroup> result = new ArrayList<>();
                result.add(combinedGroup);
                
                Log.d(TAG, "Combined content loaded successfully with " + allVideos.size() + " unique videos from multiple playlists");
                return result;
            } else {
                Log.w(TAG, "No videos found in target playlists, falling back to home");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting combined playlist content, falling back to home", e);
        }
        
        // Fallback to regular home content if all methods fail
        Log.d(TAG, "Using fallback home content");
        kotlin.Pair<List<MediaGroup>, String> homeContent = getBrowseService2().getHome();
        return homeContent != null ? homeContent.getFirst() : null;
    }
    
    /**
     * Load all pages of content for a MediaGroup
     */
    private List<MediaGroup> loadAllPages(List<MediaGroup> initialGroups) {
        List<MediaGroup> allGroups = new ArrayList<>();
        
        for (MediaGroup group : initialGroups) {
            if (group != null) {
                // Create a new group with all loaded content
                YouTubeMediaGroup fullGroup = new YouTubeMediaGroup(group.getType());
                fullGroup.setTitle(group.getTitle());
                
                // Collect all videos from all pages
                List<MediaItem> allVideos = new ArrayList<>();
                MediaGroup currentGroup = group;
                int pageCount = 0;
                int maxPages = 20; // Safety limit to prevent infinite loops
                
                while (currentGroup != null && pageCount < maxPages) {
                    if (currentGroup.getMediaItems() != null) {
                        allVideos.addAll(currentGroup.getMediaItems());
                        Log.d(TAG, "Loaded page " + (pageCount + 1) + " with " + currentGroup.getMediaItems().size() + " videos");
                    }
                    
                    // Check if there's a next page
                    if (currentGroup.getNextPageKey() != null && !currentGroup.getNextPageKey().isEmpty()) {
                        try {
                            Log.d(TAG, "Loading next page: " + currentGroup.getNextPageKey());
                            currentGroup = getBrowseService2().getGroup(currentGroup.getNextPageKey(), group.getType(), group.getTitle());
                            pageCount++;
                        } catch (Exception e) {
                            Log.w(TAG, "Error loading next page: " + e.getMessage());
                            break;
                        }
                    } else {
                        break;
                    }
                }
                
                // Set all collected videos to the group
                fullGroup.setMediaItems(allVideos);
                allGroups.add(fullGroup);
                
                Log.d(TAG, "Total videos loaded: " + allVideos.size() + " across " + (pageCount + 1) + " pages");
            }
        }
        
        return allGroups;
    }

    @Override
    public Observable<List<MediaGroup>> getTrendingObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getTrending());
        });
    }

    @Override
    public Observable<MediaGroup> getShortsObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            MediaGroup shorts = getBrowseService2().getShorts();

            if (shorts != null && shorts.getNextPageKey() != null) {
                emitGroups(emitter, shorts);
            } else {
                emitGroupsPartial(emitter, shorts);
                emitGroups(emitter, getBrowseService2().getShorts2());
            }
        });
    }

    @Override
    public Observable<List<MediaGroup>> getKidsHomeObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getKidsHome());
        });
    }

    @Override
    public Observable<List<MediaGroup>> getSportsObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getSports());
        });
    }

    @Override
    public Observable<List<MediaGroup>> getLiveObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getLive());
        });
    }

    @Override
    public Observable<MediaGroup> getMyVideosObserve() {
        return RxHelper.fromCallable(getBrowseService2()::getMyVideos);
    }

    @Override
    public Observable<List<MediaGroup>> getMusicObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            MediaGroup firstRow = getBrowseService2().getLikedMusic();
            emitGroupsPartial(emitter, Collections.singletonList(firstRow));

            emitGroups(emitter, getBrowseService2().getMusic());
        });
    }

    @Override
    public Observable<List<MediaGroup>> getNewsObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getNews());
        });
    }

    @Override
    public Observable<List<MediaGroup>> getGamingObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            emitGroups(emitter, getBrowseService2().getGaming());
        });
    }

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(String channelId) {
        return getChannelObserve(channelId, null, null);
    }

    @Override
    public Observable<List<MediaGroup>> getChannelObserve(MediaItem item) {
        return getChannelObserve(item.getChannelId(), item.getTitle(), item.getParams());
    }

    private Observable<List<MediaGroup>> getChannelObserve(String channelId, String title, String params) {
        return RxHelper.create(emitter -> {
            checkSigned();

            String canonicalId = UtilsService.canonicalChannelId(channelId);

            // Special type of channel that could be found inside Music section (see Liked row More button)
            if (YouTubeHelper.isGridChannel(canonicalId)) {
                MediaGroup gridChannel = getBrowseService2().getGridChannel(canonicalId);

                if (gridChannel instanceof BaseMediaGroup && !gridChannel.isEmpty()) {
                    ((BaseMediaGroup) gridChannel).setTitle(title);
                    emitGroups(emitter, Collections.singletonList(gridChannel));
                } else {
                    kotlin.Pair<List<MediaGroup>, String> channel = getBrowseService2().getChannel(canonicalId, params);
                    emitGroups(emitter, channel);
                }
            } else {
                kotlin.Pair<List<MediaGroup>, String> channel = getBrowseService2().getChannel(canonicalId, params);
                emitGroups(emitter, channel);
            }
        });
    }

    @Nullable
    private List<MediaGroup> getChannelSorting(String channelId) {
        checkSigned();

        return getBrowseService2().getChannelSorting(channelId);
    }

    @Override
    public Observable<List<MediaGroup>> getChannelSortingObserve(String channelId) {
        return RxHelper.fromCallable(() -> getChannelSorting(channelId));
    }

    @Override
    public Observable<List<MediaGroup>> getChannelSortingObserve(MediaItem item) {
        return item != null && item.getChannelId() != null ? getChannelSortingObserve(item.getChannelId()) : null;
    }

    @Override
    public MediaGroup getChannelSearch(String channelId, String query) {
        checkSigned();

        return getBrowseService2().getChannelSearch(channelId, query);
    }

    @Override
    public Observable<MediaGroup> getChannelSearchObserve(String channelId, String query) {
        return RxHelper.fromCallable(() -> getChannelSearch(channelId, query));
    }

    private void emitGroups(ObservableEmitter<List<MediaGroup>> emitter, kotlin.Pair<List<MediaGroup>, String> result) {
        if (result == null) {
            String msg = "emitGroups2: groups are null or empty";
            Log.e(TAG, msg);
            RxHelper.onError(emitter, msg);
            return;
        }

        List<MediaGroup> groups = result.getFirst();
        String nextKey = result.getSecond();

        while (groups != null && !groups.isEmpty()) {
            emitGroupsPartial(emitter, groups);
            result = getBrowseService2().continueSectionList(nextKey, groups.get(0).getType());
            groups = result != null ? result.getFirst() : null;
            nextKey = result != null ? result.getSecond() : null;
        }

        emitter.onComplete();
    }

    private void emitGroups(ObservableEmitter<List<MediaGroup>> emitter, List<MediaGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            String msg = "emitGroups: groups are null or empty";
            Log.e(TAG, msg);
            RxHelper.onError(emitter, msg);
            return;
        }

        emitGroupsPartial(emitter, groups);

        emitter.onComplete();
    }

    private void emitGroupsPartial(ObservableEmitter<List<MediaGroup>> emitter, List<MediaGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        MediaGroup firstGroup = groups.get(0);
        Log.d(TAG, "emitGroups: begin emitting group of type %s...", firstGroup != null ? firstGroup.getType() : null);

        List<MediaGroup> collector = new ArrayList<>();

        for (MediaGroup group : groups) { // Preserve positions
            if (group == null) {
                continue;
            }

            if (group.isEmpty()) { // Contains Chips (nested sections)?
                if (!collector.isEmpty()) {
                    emitter.onNext(collector);
                    collector = new ArrayList<>();
                }

                List<MediaGroup> sections = getBrowseService2().continueEmptyGroup(group);

                if (sections != null) {
                    emitter.onNext(sections);
                }
            } else {
                collector.add(group);
            }
        }

        if (!collector.isEmpty()) {
            emitter.onNext(collector);
        }
    }

    private void emitGroups(ObservableEmitter<MediaGroup> emitter, MediaGroup groups) {
        if (groups == null) {
            String msg = "emitGroups: groups are null or empty";
            Log.e(TAG, msg);
            RxHelper.onError(emitter, msg);
            return;
        }

        emitGroupsPartial(emitter, groups);

        emitter.onComplete();
    }

    private void emitGroupsPartial(ObservableEmitter<MediaGroup> emitter, MediaGroup groups) {
        if (groups == null) {
            return;
        }

        Log.d(TAG, "emitGroups: begin emitting group of type %s...", groups.getType());

        emitter.onNext(groups);
    }

    @Override
    public MediaGroup continueGroup(MediaGroup mediaGroup) {
        MediaGroup result = continueGroupChecked(mediaGroup);

        if (result == null) {
            return null;
        }

        if (result.isEmpty()) {
            // All contents has been filtered (e.g. shorts)
            return continueGroupChecked(result);
        }

        return result;
    }

    private MediaGroup continueGroupChecked(MediaGroup mediaGroup) {
        MediaGroup result = continueGroupInt(mediaGroup);

        if (result == null) {
            return null;
        }

        if (Helpers.equals(mediaGroup.getMediaItems(), result.getMediaItems()) &&
                Helpers.equals(mediaGroup.getNextPageKey(), result.getNextPageKey())) {
            // Result group is duplicate of the original. Seems that we've reached the end before. Skipping...
            return null;
        }

        return result;
    }

    private MediaGroup continueGroupInt(MediaGroup mediaGroup) {
        if (mediaGroup == null) {
            return null;
        }

        checkSigned();

        Log.d(TAG, "Continue group " + mediaGroup.getTitle() + "...");

        if (mediaGroup instanceof SuggestionsGroup) {
            return getWatchNextService().continueGroup(mediaGroup);
        }

        if (mediaGroup instanceof BaseMediaGroup) {
            MediaGroup group = null;

            // Fix channels with multiple empty groups (e.g. https://www.youtube.com/@RuhiCenetMedya/videos)
            for (int i = 0; i < 3; i++) {
                group = getBrowseService2().continueGroup(group == null ? mediaGroup : group);

                if (group == null || !group.isEmpty()) {
                    break;
                }
            }

            return group;
        }

        String nextKey = YouTubeHelper.extractNextKey(mediaGroup);

        switch (mediaGroup.getType()) {
            case MediaGroup.TYPE_SEARCH:
                return YouTubeMediaGroup.from(
                        getSearchService().continueSearch(nextKey),
                        mediaGroup);
            case MediaGroup.TYPE_HISTORY:
            case MediaGroup.TYPE_SUBSCRIPTIONS:
            case MediaGroup.TYPE_USER_PLAYLISTS:
            case MediaGroup.TYPE_CHANNEL_UPLOADS:
            case MediaGroup.TYPE_UNDEFINED:
                return YouTubeMediaGroup.from(
                        getBrowseService().continueGridTab(nextKey),
                        mediaGroup
                );
            default:
                return YouTubeMediaGroup.from(
                        getBrowseService().continueSection(nextKey),
                        mediaGroup
                );
        }
    }

    @Override
    public Observable<MediaGroup> continueGroupObserve(MediaGroup mediaGroup) {
        return RxHelper.fromCallable(() -> continueGroup(mediaGroup));
    }

    private void checkSigned() {
        getSignInService().checkAuth();
    }

    @Override
    public Observable<List<MediaGroup>> getPlaylistRowsObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();

            MediaGroup playlists = getPlaylists();

            if (playlists != null && playlists.getMediaItems() != null) {
                for (MediaItem playlist : playlists.getMediaItems()) {
                    kotlin.Pair<List<MediaGroup>, String> content = getBrowseService2().getChannel(playlist.getChannelId(), playlist.getParams());
                    if (content != null && content.getFirst() != null) {
                        MediaGroup mediaGroup = content.getFirst().get(0);
                        if (mediaGroup instanceof BaseMediaGroup) {
                            ((BaseMediaGroup) mediaGroup).setTitle(playlist.getTitle());
                        }
                        emitter.onNext(content.getFirst());
                    }
                }
                emitter.onComplete();
            } else {
                RxHelper.onError(emitter, "getPlaylistsRowObserve: the content is null");
            }
        });
    }

    @Override
    public Observable<MediaGroup> getPlaylistsObserve() {
        return RxHelper.fromCallable(this::getPlaylists);
    }

    private MediaGroup getPlaylists() {
        checkSigned();

        return getBrowseService2().getMyPlaylists();
    }

    @Override
    public Observable<MediaGroup> getHomeGridObserve() {
        return RxHelper.fromCallable(this::getHomeGrid);
    }

    private MediaGroup getHomeGrid() {
        checkSigned();
        
        Log.d(TAG, "Getting Home content for grid layout");
        
        // Get Watch Later content and return as single MediaGroup
        List<MediaGroup> watchLaterGroups = getWatchLaterContent();
        
        if (watchLaterGroups != null && !watchLaterGroups.isEmpty()) {
            // Return the first group (Watch Later content)
            Log.d(TAG, "Returning Watch Later content for Home grid: " + watchLaterGroups.get(0).getTitle());
            return watchLaterGroups.get(0);
        } else {
            // Fallback to regular home content
            Log.w(TAG, "Watch Later content not available, falling back to regular home");
            List<MediaGroup> homeGroups = getBrowseService2().getHome().getFirst();
            return homeGroups != null && !homeGroups.isEmpty() ? homeGroups.get(0) : null;
        }
    }

    /**
     * Get Home content as multiple rows for row layout grouped by TMDB genres
     */
    public Observable<List<MediaGroup>> getHomeRowsObserve() {
        return RxHelper.create(emitter -> {
            checkSigned();
            
            Log.d(TAG, "Getting Home content for row layout with genre categorization");
            
            // Get Watch Later content and group by genres
            List<MediaGroup> watchLaterGroups = getWatchLaterContent();
            
            if (watchLaterGroups != null && !watchLaterGroups.isEmpty()) {
                // First, try to fetch TMDB data for all videos in the background
                fetchTMDBDataForAllVideos(watchLaterGroups);
                
                // Group videos by TMDB genres (with fallback to title-based detection)
                List<MediaGroup> genreGroups = groupVideosByGenre(watchLaterGroups);
                Log.d(TAG, "Returning genre-categorized content: " + genreGroups.size() + " genre rows");
                emitter.onNext(genreGroups);
            } else {
                // Fallback to regular home content
                Log.w(TAG, "Watch Later content not available, falling back to regular home");
                List<MediaGroup> homeGroups = getBrowseService2().getHome().getFirst();
                if (homeGroups != null) {
                    emitter.onNext(homeGroups);
                }
            }
            emitter.onComplete();
        });
    }

    /**
     * Split a MediaGroup into multiple rows with specified items per row
     */
    private List<MediaGroup> splitIntoRows(MediaGroup sourceGroup, int itemsPerRow) {
        List<MediaGroup> rows = new ArrayList<>();
        List<MediaItem> items = sourceGroup.getMediaItems();
        
        if (items == null || items.isEmpty()) {
            return rows;
        }
        
        for (int i = 0; i < items.size(); i += itemsPerRow) {
            int endIndex = Math.min(i + itemsPerRow, items.size());
            List<MediaItem> rowItems = items.subList(i, endIndex);
            
            // Create a new YouTubeMediaGroup for this row
            YouTubeMediaGroup rowGroup = new YouTubeMediaGroup(MediaGroup.TYPE_HOME);
            rowGroup.setTitle(sourceGroup.getTitle() + " (Part " + (rows.size() + 1) + ")");
            rowGroup.setMediaItems(rowItems);
            
            rows.add(rowGroup);
        }
        
        return rows;
    }

    @Override
    public void enableHistory(boolean enable) {
        if (enable) {
            getActionsService().resumeWatchHistory();
        } else {
            getActionsService().pauseWatchHistory();
        }
    }

    @Override
    public void clearHistory() {
        getActionsService().clearWatchHistory();
    }

    @Override
    public void clearSearchHistory() {
        getActionsService().clearSearchHistory();
        getSearchService().clearSearchHistory();
    }

    @NonNull
    private static YouTubeSignInService getSignInService() {
        return YouTubeSignInService.instance();
    }

    @NonNull
    private static ActionsService getActionsService() {
        return ActionsServiceWrapper.instance();
    }

    @NonNull
    private static SearchService getSearchService() {
        return SearchServiceWrapper.instance();
    }

    @NonNull
    private static BrowseService getBrowseService() {
        return BrowseService.instance();
    }

    @NonNull
    private static BrowseService2 getBrowseService2() {
        return BrowseService2Wrapper.INSTANCE;
    }

    @NonNull
    private static WatchNextService getWatchNextService() {
        return WatchNextServiceWrapper.INSTANCE;
    }

    @Nullable
    private static MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }
    
    /**
     * Group videos by their TMDB genres for better organization
     */
    private List<MediaGroup> groupVideosByGenre(List<MediaGroup> sourceGroups) {
        List<MediaGroup> genreGroups = new ArrayList<>();
        
        // Map to store videos by genre
        java.util.Map<String, List<MediaItem>> genreMap = new java.util.HashMap<>();
        List<MediaItem> uncategorizedVideos = new ArrayList<>();
        
        // Debug: Check how many videos have genre data
        int totalVideos = 0;
        int videosWithGenre = 0;
        
        // Process all videos from source groups
        for (MediaGroup group : sourceGroups) {
            if (group != null && group.getMediaItems() != null) {
                for (MediaItem video : group.getMediaItems()) {
                    if (video != null) {
                        totalVideos++;
                        // Try to get TMDB genre information from the stored detailed info
                        String primaryGenre = getPrimaryGenreForVideo(video);
                        
                        if (primaryGenre != null && !primaryGenre.isEmpty()) {
                            genreMap.computeIfAbsent(primaryGenre, k -> new ArrayList<>()).add(video);
                            videosWithGenre++;
                            Log.d(TAG, "Video '" + video.getTitle() + "' categorized as: " + primaryGenre);
                        } else {
                            // If no genre info available, add to uncategorized
                            uncategorizedVideos.add(video);
                            Log.d(TAG, "Video '" + video.getTitle() + "' has no genre data");
                        }
                    }
                }
            }
        }
        
        Log.d(TAG, "Genre categorization summary: " + videosWithGenre + "/" + totalVideos + " videos have genre data");
        Log.d(TAG, "Found " + genreMap.size() + " different genres: " + genreMap.keySet());
        
        // Create genre-based groups with descriptive headers (sort by genre name for consistency)
        List<String> sortedGenres = new ArrayList<>(genreMap.keySet());
        java.util.Collections.sort(sortedGenres);
        
        for (String genre : sortedGenres) {
            List<MediaItem> videos = genreMap.get(genre);
            
            if (!videos.isEmpty()) {
                YouTubeMediaGroup genreGroup = new YouTubeMediaGroup(MediaGroup.TYPE_HOME);
                // Add descriptive header for each genre
                String genreHeader = getGenreHeader(genre);
                genreGroup.setTitle(genreHeader);
                genreGroup.setMediaItems(videos);
                genreGroups.add(genreGroup);
                Log.d(TAG, "Created genre group: " + genreHeader + " with " + videos.size() + " videos");
            }
        }
        
        // Add Recent Movies group (first 7 videos from original Watch Later order)
        List<MediaItem> recentMovies = getRecentMoviesFromOriginalOrder(sourceGroups);
        if (!recentMovies.isEmpty()) {
            YouTubeMediaGroup recentGroup = new YouTubeMediaGroup(MediaGroup.TYPE_HOME);
            recentGroup.setTitle("Recent Movies - Latest releases");
            recentGroup.setMediaItems(recentMovies);
            genreGroups.add(0, recentGroup); // Add at the beginning
            Log.d(TAG, "Created recent movies group with " + recentMovies.size() + " videos");
        }
        
        // Add uncategorized videos as a separate group if any exist
        if (!uncategorizedVideos.isEmpty()) {
            YouTubeMediaGroup uncategorizedGroup = new YouTubeMediaGroup(MediaGroup.TYPE_HOME);
            uncategorizedGroup.setTitle("Other Movies - Mixed genres and favorites");
            uncategorizedGroup.setMediaItems(uncategorizedVideos);
            genreGroups.add(uncategorizedGroup);
            Log.d(TAG, "Created uncategorized group with " + uncategorizedVideos.size() + " videos");
        }
        
        // If no genre groups were created, return original groups
        if (genreGroups.isEmpty()) {
            Log.w(TAG, "No genre groups created, returning original groups");
            return sourceGroups;
        }
        
        return genreGroups;
    }
    
    /**
     * Get the primary genre for a video from stored TMDB data or fallback to title-based detection
     */
    private String getPrimaryGenreForVideo(MediaItem video) {
        try {
            String videoId = video.getVideoId();
            if (videoId != null) {
                // First try TMDB genre data
                String tmdbGenre = getTMDBGenreForVideo(videoId);
                if (tmdbGenre != null && !tmdbGenre.isEmpty()) {
                    Log.d(TAG, "Using TMDB genre for video: " + video.getTitle() + " -> " + tmdbGenre);
                    return tmdbGenre;
                } else {
                    // Fallback to title-based genre detection
                    String titleGenre = detectGenreFromTitle(video.getTitle());
                    if (titleGenre != null && !titleGenre.isEmpty()) {
                        Log.d(TAG, "Using title-based genre for video: " + video.getTitle() + " -> " + titleGenre);
                        return titleGenre;
                    } else {
                        Log.d(TAG, "No genre data available for video: " + video.getTitle());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting genre for video: " + video.getTitle(), e);
        }
        return null;
    }
    
    
    /**
     * Fetch TMDB data for all videos in the background
     */
    private void fetchTMDBDataForAllVideos(List<MediaGroup> groups) {
        Log.d(TAG, "Starting background TMDB data fetch for all videos");
        
        // Collect all video IDs
        List<String> videoIds = new ArrayList<>();
        for (MediaGroup group : groups) {
            if (group != null && group.getMediaItems() != null) {
                for (MediaItem video : group.getMediaItems()) {
                    if (video != null && video.getVideoId() != null) {
                        videoIds.add(video.getVideoId());
                    }
                }
            }
        }
        
        Log.d(TAG, "Found " + videoIds.size() + " videos to fetch TMDB data for");
        
        // Start background thread to fetch TMDB data
        new Thread(() -> {
            int processedCount = 0;
            for (String videoId : videoIds) {
                try {
                    // Check if we already have genre data for this video
                    if (GenreService.instance().hasGenreData(videoId)) {
                        continue; // Skip if we already have data
                    }
                    
                    // Fetch TMDB data for this video
                    fetchTMDBDataForVideo(videoId);
                    processedCount++;
                    
                    // Small delay to avoid overwhelming the API
                    Thread.sleep(200);
                    
                    // Log progress every 10 videos
                    if (processedCount % 10 == 0) {
                        Log.d(TAG, "Processed " + processedCount + "/" + videoIds.size() + " videos for TMDB data");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error fetching TMDB data for video " + videoId + ": " + e.getMessage());
                }
            }
            Log.d(TAG, "Completed background TMDB data fetch for " + processedCount + " videos");
        }).start();
    }
    
    /**
     * Fetch TMDB data for a single video
     */
    private void fetchTMDBDataForVideo(String videoId) {
        try {
            // Try to access TMDBImageService using reflection
            Class<?> tmdbServiceClass = Class.forName("com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageService");
            java.lang.reflect.Method getInstanceMethod = tmdbServiceClass.getMethod("instance");
            Object tmdbService = getInstanceMethod.invoke(null);
            
            // Get the fetchDetailedMovieInfo method
            java.lang.reflect.Method fetchMethod = tmdbServiceClass.getMethod("fetchDetailedMovieInfo", String.class, 
                Class.forName("com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageCallback"));
            
            // Create a callback to handle the response
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Class.forName("com.liskovsoft.smartyoutubetv2.tv.services.TMDBImageCallback")},
                (proxy, method, args) -> {
                    if ("onDetailedMovieInfoReceived".equals(method.getName())) {
                        Object detailedInfo = args[0];
                        if (detailedInfo != null) {
                            // Store the genre data
                            try {
                                java.lang.reflect.Method getGenresMethod = detailedInfo.getClass().getMethod("getGenres");
                                Object genres = getGenresMethod.invoke(detailedInfo);
                                if (genres instanceof List && !((List<?>) genres).isEmpty()) {
                                    String primaryGenre = (String) ((List<?>) genres).get(0);
                                    GenreService.instance().storeVideoGenre(videoId, primaryGenre);
                                    Log.d(TAG, "Background: Stored genre '" + primaryGenre + "' for video " + videoId);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Error processing TMDB response for video " + videoId + ": " + e.getMessage());
                            }
                        }
                    }
                    return null;
                }
            );
            
            // Call the fetch method
            fetchMethod.invoke(tmdbService, videoId, callback);
            
        } catch (Exception e) {
            Log.w(TAG, "Could not fetch TMDB data for video " + videoId + ": " + e.getMessage());
        }
    }
    
    /**
     * Get the first 7 videos from the original Watch Later order (before genre grouping)
     */
    private List<MediaItem> getRecentMoviesFromOriginalOrder(List<MediaGroup> sourceGroups) {
        List<MediaItem> allVideos = new ArrayList<>();
        
        // Collect all videos from source groups in their original Watch Later order
        for (MediaGroup group : sourceGroups) {
            if (group != null && group.getMediaItems() != null) {
                allVideos.addAll(group.getMediaItems());
            }
        }
        
        // Return first 7 videos (Watch Later is already correctly ordered)
        int maxRecent = Math.min(7, allVideos.size());
        List<MediaItem> recentMovies = allVideos.subList(0, maxRecent);
        
        Log.d(TAG, "Selected " + recentMovies.size() + " recent movies (first 7 from Watch Later)");
        for (int i = 0; i < recentMovies.size(); i++) {
            MediaItem video = recentMovies.get(i);
            Log.d(TAG, "Recent movie #" + (i+1) + ": " + video.getTitle());
        }
        
        return recentMovies;
    }
    
    /**
     * Get release date for a video from TMDB data or fallback to current date
     */
    private String getReleaseDateForVideo(MediaItem video) {
        try {
            String videoId = video.getVideoId();
            if (videoId != null) {
                // Try to get TMDB release date using reflection
                String tmdbReleaseDate = getTMDBReleaseDateForVideo(videoId);
                if (tmdbReleaseDate != null && !tmdbReleaseDate.isEmpty()) {
                    return tmdbReleaseDate;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting release date for video: " + video.getTitle(), e);
        }
        
        // Fallback: return a very old date to put videos without TMDB data at the end
        return "1900-01-01";
    }
    
    /**
     * Get TMDB release date for a video ID
     */
    private String getTMDBReleaseDateForVideo(String videoId) {
        try {
            // Try to access the stored detailed movie info using reflection
            Class<?> movieDetailsPresenterClass = Class.forName("com.liskovsoft.smartyoutubetv2.tv.presenters.MovieDetailsVideoActionPresenter");
            java.lang.reflect.Method getDetailedInfoMethod = movieDetailsPresenterClass.getMethod("getDetailedMovieInfo", String.class);
            Object detailedInfo = getDetailedInfoMethod.invoke(null, videoId);
            
            if (detailedInfo != null) {
                // Get release date from detailed info
                java.lang.reflect.Method getReleaseDateMethod = detailedInfo.getClass().getMethod("getReleaseDate");
                String releaseDate = (String) getReleaseDateMethod.invoke(detailedInfo);
                return releaseDate;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not access TMDB release date for video ID " + videoId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Detect genre from video title as fallback when TMDB data is not available
     */
    private String detectGenreFromTitle(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        
        String lowerTitle = title.toLowerCase();
        
        // Action keywords
        if (lowerTitle.contains("action") || lowerTitle.contains("fight") || lowerTitle.contains("battle") || 
            lowerTitle.contains("war") || lowerTitle.contains("gun") || lowerTitle.contains("shoot") ||
            lowerTitle.contains("explosion") || lowerTitle.contains("superhero") || lowerTitle.contains("spy")) {
            return "Action";
        }
        
        // Comedy keywords
        if (lowerTitle.contains("comedy") || lowerTitle.contains("funny") || lowerTitle.contains("laugh") ||
            lowerTitle.contains("joke") || lowerTitle.contains("humor") || lowerTitle.contains("comic")) {
            return "Comedy";
        }
        
        // Drama keywords
        if (lowerTitle.contains("drama") || lowerTitle.contains("story") || lowerTitle.contains("life") ||
            lowerTitle.contains("family") || lowerTitle.contains("love") || lowerTitle.contains("romance")) {
            return "Drama";
        }
        
        // Horror keywords
        if (lowerTitle.contains("horror") || lowerTitle.contains("scary") || lowerTitle.contains("fright") ||
            lowerTitle.contains("ghost") || lowerTitle.contains("monster") || lowerTitle.contains("zombie") ||
            lowerTitle.contains("vampire") || lowerTitle.contains("demon")) {
            return "Horror";
        }
        
        // Sci-Fi keywords
        if (lowerTitle.contains("sci-fi") || lowerTitle.contains("science fiction") || lowerTitle.contains("space") ||
            lowerTitle.contains("alien") || lowerTitle.contains("robot") || lowerTitle.contains("future") ||
            lowerTitle.contains("time travel") || lowerTitle.contains("cyber")) {
            return "Sci-Fi";
        }
        
        // Thriller keywords
        if (lowerTitle.contains("thriller") || lowerTitle.contains("suspense") || lowerTitle.contains("mystery") ||
            lowerTitle.contains("crime") || lowerTitle.contains("detective") || lowerTitle.contains("murder") ||
            lowerTitle.contains("police") || lowerTitle.contains("investigation")) {
            return "Thriller";
        }
        
        // Adventure keywords
        if (lowerTitle.contains("adventure") || lowerTitle.contains("journey") || lowerTitle.contains("quest") ||
            lowerTitle.contains("explore") || lowerTitle.contains("expedition") || lowerTitle.contains("treasure")) {
            return "Adventure";
        }
        
        // Animation keywords
        if (lowerTitle.contains("animation") || lowerTitle.contains("cartoon") || lowerTitle.contains("anime") ||
            lowerTitle.contains("animated") || lowerTitle.contains("pixar") || lowerTitle.contains("disney")) {
            return "Animation";
        }
        
        // Documentary keywords
        if (lowerTitle.contains("documentary") || lowerTitle.contains("doc") || lowerTitle.contains("real") ||
            lowerTitle.contains("true story") || lowerTitle.contains("biography") || lowerTitle.contains("history")) {
            return "Documentary";
        }
        
        return null; // No genre detected
    }
    
    /**
     * Get TMDB genre for a video ID from the shared GenreService
     */
    private String getTMDBGenreForVideo(String videoId) {
        try {
            // Use the shared GenreService to get the primary genre
            String primaryGenre = GenreService.instance().getVideoGenre(videoId);
            
            if (primaryGenre != null && !primaryGenre.isEmpty()) {
                // Map TMDB genre names to our display names
                String mappedGenre = mapTMDBGenreToDisplayGenre(primaryGenre);
                Log.d(TAG, "Found TMDB genre for video ID " + videoId + ": " + primaryGenre + " -> " + mappedGenre);
                return mappedGenre;
            } else {
                Log.d(TAG, "No TMDB genre data found for video ID: " + videoId);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not access TMDB data for video ID " + videoId + ": " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Map TMDB genre names to our display genre names
     */
    private String mapTMDBGenreToDisplayGenre(String tmdbGenre) {
        if (tmdbGenre == null) return null;
        
        String lowerGenre = tmdbGenre.toLowerCase().trim();
        
        // Map common TMDB genre variations to our standard names
        switch (lowerGenre) {
            case "action":
            case "action & adventure":
                return "Action";
            case "comedy":
                return "Comedy";
            case "drama":
                return "Drama";
            case "horror":
                return "Horror";
            case "science fiction":
            case "sci-fi":
            case "sci fi":
                return "Sci-Fi";
            case "thriller":
                return "Thriller";
            case "adventure":
                return "Adventure";
            case "fantasy":
                return "Fantasy";
            case "animation":
                return "Animation";
            case "documentary":
                return "Documentary";
            case "romance":
                return "Romance";
            case "crime":
                return "Crime";
            case "family":
                return "Family";
            case "mystery":
                return "Thriller"; // Map mystery to thriller
            case "war":
                return "Action"; // Map war to action
            case "western":
                return "Action"; // Map western to action
            case "music":
                return "Documentary"; // Map music to documentary
            case "history":
                return "Documentary"; // Map history to documentary
            case "tv movie":
                return "Drama"; // Map TV movie to drama
            default:
                Log.d(TAG, "Unknown TMDB genre: " + tmdbGenre + ", using as-is");
                return tmdbGenre; // Use the original genre name if not mapped
        }
    }
    
    
    /**
     * Get descriptive header for each genre category
     */
    private String getGenreHeader(String genre) {
        if (genre == null) {
            return "Movies";
        }
        
        switch (genre) {
            case "Action":
                return "Action Movies";
            case "Comedy":
                return "Comedy Movies";
            case "Drama":
                return "Drama Movies";
            case "Horror":
                return "Horror Movies";
            case "Sci-Fi":
                return "Sci-Fi Movies";
            case "Thriller":
                return "Thriller Movies";
            case "Adventure":
                return "Adventure Movies";
            case "Fantasy":
                return "Fantasy Movies";
            case "Animation":
                return "Animation Movies";
            case "Documentary":
                return "Documentary Movies";
            case "Romance":
                return "Romance Movies";
            case "Crime":
                return "Crime Movies";
            case "Family":
                return "Family Movies";
            default:
                return genre + " Movies";
        }
    }
}
