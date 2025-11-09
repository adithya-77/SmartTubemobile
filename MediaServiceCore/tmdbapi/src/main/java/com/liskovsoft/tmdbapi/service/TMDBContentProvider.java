package com.liskovsoft.tmdbapi.service;

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.tmdbapi.models.TMDBMovie;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import java.util.ArrayList;
import java.util.List;

public class TMDBContentProvider {
    private static final int TMDB_POPULAR_SECTION = 1001;
    private static final int TMDB_TOP_RATED_SECTION = 1002;
    private static final int TMDB_NOW_PLAYING_SECTION = 1003;
    private static final int TMDB_UPCOMING_SECTION = 1004;
    
    private TMDBContentService mTMDBService;
    
    public TMDBContentProvider() {
        mTMDBService = new TMDBContentService();
    }
    
    public Observable<List<MediaGroup>> getTMDBHomeRows() {
        return Observable.create(new ObservableOnSubscribe<List<MediaGroup>>() {
            @Override
            public void subscribe(ObservableEmitter<List<MediaGroup>> emitter) {
                try {
                    List<MediaGroup> groups = new ArrayList<>();
                    
                    // Popular Movies
                    List<TMDBMovie> popularMovies = mTMDBService.getPopularMovies(1);
                    if (!popularMovies.isEmpty()) {
                        MediaGroup popularGroup = createMediaGroup("Popular Movies", popularMovies);
                        groups.add(popularGroup);
                    }
                    
                    // Top Rated Movies
                    List<TMDBMovie> topRatedMovies = mTMDBService.getTopRatedMovies(1);
                    if (!topRatedMovies.isEmpty()) {
                        MediaGroup topRatedGroup = createMediaGroup("Top Rated Movies", topRatedMovies);
                        groups.add(topRatedGroup);
                    }
                    
                    // Now Playing Movies
                    List<TMDBMovie> nowPlayingMovies = mTMDBService.getNowPlayingMovies(1);
                    if (!nowPlayingMovies.isEmpty()) {
                        MediaGroup nowPlayingGroup = createMediaGroup("Now Playing", nowPlayingMovies);
                        groups.add(nowPlayingGroup);
                    }
                    
                    // Upcoming Movies
                    List<TMDBMovie> upcomingMovies = mTMDBService.getUpcomingMovies(1);
                    if (!upcomingMovies.isEmpty()) {
                        MediaGroup upcomingGroup = createMediaGroup("Upcoming Movies", upcomingMovies);
                        groups.add(upcomingGroup);
                    }
                    
                    emitter.onNext(groups);
                    emitter.onComplete();
                    
                } catch (Exception e) {
                    emitter.onError(e);
                }
            }
        });
    }
    
    private MediaGroup createMediaGroup(String title, List<TMDBMovie> movies) {
        List<MediaItem> mediaItems = new ArrayList<>();
        
        for (TMDBMovie movie : movies) {
            Video video = TMDBToVideoConverter.convertToVideo(movie);
            if (video != null) {
                // Create a MediaItem from the Video
                MediaItem mediaItem = createMediaItemFromVideo(video);
                if (mediaItem != null) {
                    mediaItems.add(mediaItem);
                }
            }
        }
        
        return new MediaGroup() {
            @Override
            public String getTitle() {
                return title;
            }
            
            @Override
            public List<MediaItem> getMediaItems() {
                return mediaItems;
            }
            
            @Override
            public String getNextPageKey() {
                return null; // No pagination for now
            }
            
            @Override
            public boolean isEmpty() {
                return mediaItems.isEmpty();
            }
            
            @Override
            public int getType() {
                return 0; // Default type
            }
            
            @Override
            public String getChannelUrl() {
                return null;
            }
        };
    }
    
    private MediaItem createMediaItemFromVideo(Video video) {
        return new MediaItem() {
            @Override
            public int getId() {
                return video.id;
            }
            
            @Override
            public String getTitle() {
                return video.title;
            }
            
            @Override
            public String getSecondTitle() {
                return video.secondTitle != null ? video.secondTitle.toString() : null;
            }
            
            @Override
            public String getContentType() {
                return video.category;
            }
            
            @Override
            public int getType() {
                return video.itemType;
            }
            
            @Override
            public String getVideoId() {
                return video.videoId;
            }
            
            @Override
            public String getChannelId() {
                return video.channelId;
            }
            
            @Override
            public String getBackgroundImageUrl() {
                return video.bgImageUrl;
            }
            
            @Override
            public String getCardImageUrl() {
                return video.cardImageUrl;
            }
            
            @Override
            public String getAuthor() {
                return video.author;
            }
            
            @Override
            public int getPercentWatched() {
                return (int) video.percentWatched;
            }
            
            @Override
            public int getStartTimeSeconds() {
                return video.startTimeSeconds;
            }
            
            @Override
            public String getBadgeText() {
                return video.badge;
            }
            
            @Override
            public boolean hasNewContent() {
                return video.hasNewContent;
            }
            
            @Override
            public String getVideoPreviewUrl() {
                return video.previewUrl;
            }
            
            @Override
            public String getPlaylistId() {
                return video.playlistId;
            }
            
            @Override
            public int getPlaylistIndex() {
                return video.playlistIndex;
            }
            
            @Override
            public String getParams() {
                return video.playlistParams;
            }
            
            @Override
            public String getReloadPageKey() {
                return video.reloadPageKey;
            }
            
            @Override
            public boolean isLive() {
                return video.isLive;
            }
            
            @Override
            public boolean isUpcoming() {
                return video.isUpcoming;
            }
            
            @Override
            public boolean isShorts() {
                return video.isShorts;
            }
            
            @Override
            public boolean isMovie() {
                return video.isMovie;
            }
            
            @Override
            public String getClickTrackingParams() {
                return video.clickTrackingParams;
            }
            
            @Override
            public long getDurationMs() {
                return video.durationMs;
            }
            
            @Override
            public String getSearchQuery() {
                return video.searchQuery;
            }
            
            @Override
            public boolean hasUploads() {
                return false;
            }
        };
    }
}