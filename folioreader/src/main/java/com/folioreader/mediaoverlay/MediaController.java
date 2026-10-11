package com.folioreader.mediaoverlay;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.folioreader.Constants;
import com.folioreader.model.event.MediaOverlayPlayPauseEvent;
import com.folioreader.model.event.MediaOverlaySpeedEvent;
import com.folioreader.model.media_overlay.OverlayItems;
import com.folioreader.util.UiUtil;
import org.readium.r2.shared.Clip;
import org.readium.r2.shared.MediaOverlays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author gautam chibde on 21/6/17.
 */

public class MediaController {

    private static final String TAG = MediaController.class.getSimpleName();

    public enum MediaType {
        TTS, SMIL,
    }

    private MediaType mediaType;
    private MediaControllerCallbacks callbacks;
    private Context context;

    //**********************************//
    //          MEDIA OVERLAY           //
    //**********************************//
    private MediaOverlays mediaOverlays;
    private List<OverlayItems> mediaItems = new ArrayList<>();
    private int mediaItemPosition = 0;
    private MediaPlayer mediaPlayer;

    private Clip currentClip;

    private boolean isMediaPlayerReady;
    private Handler mediaHandler;

    //*********************************//
    //              TTS                //
    //*********************************//
    private TextToSpeech mTextToSpeech;
    private boolean mIsSpeaking = false;
    private ExecutorService ttsExecutor = Executors.newSingleThreadExecutor();

    public MediaController(Context context, MediaType mediaType, MediaControllerCallbacks callbacks) {
        this.mediaType = mediaType;
        this.callbacks = callbacks;
        this.context = context;
    }

    private Runnable mHighlightTask = new Runnable() {
        @Override
        public void run() {
            int currentPosition = mediaPlayer.getCurrentPosition();
            if (mediaPlayer.getDuration() != currentPosition) {
                if (mediaItemPosition < mediaItems.size()) {
                    //int end = (int) currentClip.end * 1000;
                    int end = (int) (currentClip.getEnd() * 1000);
                    if (currentPosition > end) {
                        mediaItemPosition++;
                        currentClip = mediaOverlays.clip(mediaItems.get(mediaItemPosition).getId());
                        if (currentClip != null) {
                            callbacks.highLightText(mediaItems.get(mediaItemPosition).getId());
                        } else {
                            mediaItemPosition++;
                        }
                    }
                    mediaHandler.postDelayed(mHighlightTask, 10);
                } else {
                    mediaHandler.removeCallbacks(mHighlightTask);
                }
            }
        }
    };

    public void resetMediaPosition() {
        mediaItemPosition = 0;
    }

    public void setSMILItems(List<OverlayItems> overlayItems) {
        this.mediaItems = overlayItems;
    }

    public void next() {
        mediaItemPosition++;
    }

    public void setTextToSpeech(final Context context) {
        mTextToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(final int status) {
                if (status != TextToSpeech.ERROR) {
                    ttsExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mTextToSpeech != null) {
                                try {
                                    mTextToSpeech.setLanguage(Locale.UK);
                                    mTextToSpeech.setSpeechRate(0.70f);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error setting TTS language", e);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    public void setUpMediaPlayer(MediaOverlays mediaOverlays, String path, String mBookTitle) {
        this.mediaOverlays = mediaOverlays;
        mediaHandler = new Handler();
        try {
            mediaItemPosition = 0;
            String uri = Constants.DEFAULT_STREAMER_URL + mBookTitle + path;
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(uri);
            mediaPlayer.prepare();
            isMediaPlayerReady = true;
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    public void setSpeed(MediaOverlaySpeedEvent.Speed speed) {
        switch (speed) {
            case HALF:
                setPlaybackSpeed(0.5f);
                break;
            case ONE:
                setPlaybackSpeed(1.0f);
                break;
            case ONE_HALF:
                setPlaybackSpeed(1.5f);
                break;
            case TWO:
                setPlaybackSpeed(2.0f);
                break;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void setPlaybackSpeed(final float speed) {
        if (mediaType == MediaType.SMIL) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            }
        } else {
            ttsExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mTextToSpeech != null) {
                        try {
                            mTextToSpeech.setSpeechRate(speed);
                        } catch (Exception e) {
                            Log.e(TAG, "Error setting TTS speech rate", e);
                        }
                    }
                }
            });
        }
    }

    public void stateChanged(MediaOverlayPlayPauseEvent event) {
        if (event.isStateChanged()) {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
            if (mTextToSpeech != null) {
                ttsExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (mTextToSpeech != null && mTextToSpeech.isSpeaking()) {
                            mTextToSpeech.stop();
                        }
                    }
                });
            }
            mIsSpeaking = false;
        } else {
            if (event.isPlay()) {
                UiUtil.keepScreenAwake(true, context);
            } else {
                UiUtil.keepScreenAwake(false, context);
            }
            if (mediaType == MediaType.SMIL) {
                playSMIL();
            } else {
                if (mIsSpeaking) {
                    ttsExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mTextToSpeech != null) {
                                mTextToSpeech.stop();
                            }
                        }
                    });
                    mIsSpeaking = false;
                    callbacks.resetCurrentIndex();
                } else {
                    mIsSpeaking = true;
                    callbacks.highLightTTS();
                }
            }
        }
    }

    private void playSMIL() {
        if (mediaPlayer != null && isMediaPlayerReady) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else {
                currentClip = mediaOverlays.clip(mediaItems.get(mediaItemPosition).getId());
                if (currentClip != null) {
                    mediaPlayer.start();
                    mediaHandler.post(mHighlightTask);
                } else {
                    mediaItemPosition++;
                    mediaPlayer.start();
                    mediaHandler.post(mHighlightTask);
                }
            }
        }
    }

    public void speakAudio(final String sentence) {
        if (mediaType == MediaType.TTS) {
            ttsExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mTextToSpeech != null) {
                        try {
                            HashMap<String, String> params = new HashMap<>();
                            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "stringId");
                            mTextToSpeech.speak(sentence, TextToSpeech.QUEUE_FLUSH, params);
                        } catch (Exception e) {
                            Log.e(TAG, "Error speaking audio", e);
                        }
                    }
                }
            });
        }
    }

    public void stop() {
        mIsSpeaking = false;
        if (mTextToSpeech != null) {
            ttsExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (mTextToSpeech != null) {
                        try {
                            if (mTextToSpeech.isSpeaking()) {
                                mTextToSpeech.stop();
                            }
                            mTextToSpeech.shutdown();
                            mTextToSpeech = null;
                        } catch (Exception e) {
                            Log.e(TAG, "Error stopping TTS", e);
                        }
                    }
                }
            });
        }
        if (ttsExecutor != null && !ttsExecutor.isShutdown()) {
            ttsExecutor.shutdown();
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            mediaHandler.removeCallbacks(mHighlightTask);
        }
    }
}
