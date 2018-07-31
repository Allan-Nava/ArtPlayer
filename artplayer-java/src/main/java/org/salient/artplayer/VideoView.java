package org.salient.artplayer;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.Map;

/**
 * > Created by Mai on 2018/7/10
 * *
 * > Description: 视频播放视图
 * *
 */
public class VideoView extends FrameLayout {

    private final String TAG = VideoView.class.getSimpleName();
    private final int TEXTURE_VIEW_POSITION = 0;//视频播放视图层
    private final int CONTROL_PANEL_POSITION = 1;//控制面板层
    private FrameLayout textureViewContainer;
    private int mScreenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;

    // settable by the client
    private Object mData = null;//video data like id, title, cover picture...
    private Object dataSourceObject;// video dataSource (Http url or Android assets file) would be posted to MediaPlayer.
    protected Map<String, String> mHeaders;//当前视频地址的请求头

    private AbsControlPanel mControlPanel;
    private WindowType mWindowType = WindowType.NORMAL;
    private OnWindowDetachedListener mDetachedListener;
    private boolean mSmartMode = true;
    private VideoView mParentVideoView = null;

    //api attribute
    private boolean isMute = false;
    public int widthRatio = 0;
    public int heightRatio = 0;

    private Comparator mComparator = new Comparator() {
        @Override
        public boolean compare(VideoView videoView) {
            Object dataSource = MediaPlayerManager.instance().getDataSource();
            return dataSource != null && videoView != null && dataSource == videoView.getDataSourceObject();
        }
    };

    public VideoView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {

        textureViewContainer = new FrameLayout(getContext());
        textureViewContainer.setBackgroundColor(Color.BLACK);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        addView(textureViewContainer, TEXTURE_VIEW_POSITION, params);

        try {
            mScreenOrientation = ((AppCompatActivity) context).getRequestedOrientation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setUp(String url) {
        setUp(url, WindowType.NORMAL, null);
    }

    public void setUp(String url, Object data) {
        setUp(url, WindowType.NORMAL, data);
    }

    public void setUp(String url, WindowType windowType) {
        setUp(url, windowType, null);
    }

    public void setUp(Object dataSourceObjects, WindowType windowType, Object data) {
        this.dataSourceObject = dataSourceObjects;
        this.mWindowType = windowType;
        this.mData = data;
        VideoView currentVideoView = MediaPlayerManager.instance().getCurrentVideoView();
        if (mSmartMode
                && mWindowType == WindowType.LIST
                && (isCurrentPlaying() || currentVideoView == null || currentVideoView.getWindowType() == WindowType.TINY)) {
            autoMatch();
        }
    }

    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    public void setHeaders(Map<String, String> mHeaders) {
        this.mHeaders = mHeaders;
    }

    public boolean isSmartMode() {
        return mSmartMode;
    }

    public void setSmartMode(boolean mSmartMode) {
        this.mSmartMode = mSmartMode;
    }

    /**
     * 列表匹配模式
     */
    private void autoMatch() {
        if (!mSmartMode || mWindowType != WindowType.LIST) {
            return;
        }
        VideoView currentVideoView = MediaPlayerManager.instance().getCurrentVideoView();
        if (isCurrentPlaying()) {
            //quit tiny window
            if (currentVideoView != null && currentVideoView.getWindowType() == WindowType.TINY) {
                //play at this video view
                MediaPlayerManager.instance().playAt(this);
                //自动退出小窗
                MediaPlayerManager.instance().clearTinyLayout(getContext());
                if (mControlPanel != null) {
                    mControlPanel.onExitSecondScreen();
                    mControlPanel.notifyStateChange();
                }
            } else if (currentVideoView != null && currentVideoView.getWindowType() == WindowType.FULLSCREEN) {
                if (mControlPanel != null) {
                    //mControlPanel.onStateIdle();
                }
            } else {
                //play at this video view
                MediaPlayerManager.instance().playAt(this);
                if (mControlPanel != null) {
                    mControlPanel.notifyStateChange();
                }
            }
        } else if (currentVideoView == this) {
            MediaPlayerManager.instance().removeTextureView();
            if (mControlPanel != null) {
                mControlPanel.onStateIdle();
            }
        } else {
            if (mControlPanel != null) {
                mControlPanel.onStateIdle();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSmartMode) {
            autoMatch();
        }
    }

    public Object getData() {
        return mData;
    }

    public void setData(Object data) {
        mData = data;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mWindowType == WindowType.FULLSCREEN || mWindowType == WindowType.TINY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (widthRatio != 0 && heightRatio != 0) {
            int specWidth = MeasureSpec.getSize(widthMeasureSpec);
            int specHeight = (int) ((specWidth * (float) heightRatio) / widthRatio);
            setMeasuredDimension(specWidth, specHeight);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(specHeight, MeasureSpec.EXACTLY);
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public int getScreenOrientation() {
        return mScreenOrientation;
    }

    /**
     * 开始播放
     */
    public void start() {
        Log.d(TAG, "start [" + this.hashCode() + "] ");

        if (dataSourceObject == null) {
            Log.w(TAG, "No Url");
            return;
        }

        if (isCurrentPlaying()) {
            switch (MediaPlayerManager.instance().getPlayerState()) {
                case IDLE://从初始状态开始播放
                case ERROR:
                    MediaPlayerManager.instance().play(this);
                    break;
                case PREPARED:
                case PLAYBACK_COMPLETED: // 重播
                case PAUSED://从暂停状态恢复播放
                    MediaPlayerManager.instance().start();
                    break;
            }
        } else {
            MediaPlayerManager.instance().play(this);
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        if (isCurrentPlaying()) {
            if (MediaPlayerManager.instance().getPlayerState() == MediaPlayerManager.PlayerState.PLAYING) {
                Log.d(TAG, "pause [" + this.hashCode() + "] ");
                MediaPlayerManager.instance().pause();
            }
        }
    }

    public Object getDataSourceObject() {
        return dataSourceObject;
    }

    public void setDataSourceObject(Object dataSourceObject) {
        this.dataSourceObject = dataSourceObject;
    }

    public AbsControlPanel getControlPanel() {
        return mControlPanel;
    }

    /**
     * 设置控制面板
     *
     * @param mControlPanel AbsControlPanel
     */
    public void setControlPanel(AbsControlPanel mControlPanel) {
        if (mControlPanel != null) {
            mControlPanel.setTarget(this);
            ViewParent parent = mControlPanel.getParent();
            if (parent != null) {
                ((ViewGroup) parent).removeView(mControlPanel);
            }
        }
        this.mControlPanel = mControlPanel;
        View child = getChildAt(CONTROL_PANEL_POSITION);
        if (child != null) {
            removeViewAt(CONTROL_PANEL_POSITION);
        }
        addView(this.mControlPanel, CONTROL_PANEL_POSITION);
        if (this.mControlPanel != null) {
            this.mControlPanel.onStateIdle();
        }
    }

    public FrameLayout getTextureViewContainer() {
        return textureViewContainer;
    }

    public WindowType getWindowType() {
        return mWindowType;
    }

    public void setWindowType(WindowType mWindowType) {
        this.mWindowType = mWindowType;
    }

    public OnWindowDetachedListener getDetachedListener() {
        return mDetachedListener;
    }

    public void setOnWindowDetachedListener(OnWindowDetachedListener mDetachedListener) {
        this.mDetachedListener = mDetachedListener;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDetachedListener != null) {
            mDetachedListener.onDetached(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof VideoView && mComparator.compare(this);
    }

    public Comparator getComparator() {
        return mComparator;
    }

    public void setComparator(@NonNull Comparator mComparator) {
        this.mComparator = mComparator;
    }

    /**
     * 判断VideoView 与 正在播放的多媒体资源是否匹配;
     * 匹配规则可以通过{@link VideoView#setComparator(Comparator)} 设置;
     * 默认比较{@link VideoView#dataSourceObject} 和 {@link AbsMediaPlayer#dataSource}
     * See{@link VideoView#mComparator }
     *
     * @return VideoView
     */
    public boolean isCurrentPlaying() {
        return mComparator.compare(this);
    }

    public VideoView getParentVideoView() {
        return mParentVideoView;
    }

    public void setParentVideoView(VideoView mParentVideoView) {
        this.mParentVideoView = mParentVideoView;
    }

    public boolean isMute() {
        return isMute;
    }

    /**
     * 设置静音
     * @param mute
     */
    public void setMute(boolean mute) {
        isMute = mute;
        MediaPlayerManager.instance().mute(mute);
    }

    /**
     * 进入全屏模式
     * <p>
     * 注意：这里把一个VideoView动态添加到{@link Window#ID_ANDROID_CONTENT }所指的View中
     */
    public void startFullscreen(int screenOrientation) {
        if (getParent() != null) {
            throw new IllegalStateException("The specified VideoView already has a parent. " +
                    "You must call removeView() on the VideoView's parent first.");
        }
        Context context = getContext();
        setWindowType(VideoView.WindowType.FULLSCREEN);
        Utils.hideSupportActionBar(context);
        // add to window
        ViewGroup vp = (Utils.scanForActivity(context)).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.salient_video_fullscreen_id);
        if (old != null) {
            vp.removeView(old);
        }
        setId(R.id.salient_video_fullscreen_id);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        vp.addView(this, lp);
        //add TextureView
        MediaPlayerManager.instance().removeTextureView();
        MediaPlayerManager.instance().addTextureView(this);
        //update ControlPanel State
        AbsControlPanel controlPanel = getControlPanel();
        if (controlPanel != null) {
            controlPanel.onEnterSecondScreen();
        }
        //update Parent ControlPanel State
        VideoView parentVideoView = getParentVideoView();
        if (parentVideoView != null) {
            AbsControlPanel parentControlPanel = parentVideoView.getControlPanel();
            if (parentControlPanel != null) {
                parentControlPanel.onEnterSecondScreen();
            }
        }
        //Rotate window an enter fullscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        Utils.setRequestedOrientation(context, screenOrientation);

        MediaPlayerManager.instance().updateState(MediaPlayerManager.instance().getPlayerState());

    }

    /**
     * 进入小屏模式
     * <p>
     * 注意：这里把一个VideoView动态添加到{@link Window#ID_ANDROID_CONTENT }所指的View中
     */
    public void startTinyWindow() {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(16 * 40, 9 * 40);
        layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        layoutParams.setMargins(0, 0, 30, 100);
        startTinyWindow(layoutParams);
    }

    /**
     * 进入小屏模式
     * <p>
     * 注意：这里把一个VideoView动态添加到{@link Window#ID_ANDROID_CONTENT }所指的View中
     */
    public void startTinyWindow(FrameLayout.LayoutParams lp) {
        Log.i(TAG, "startWindowTiny " + " [" + this.hashCode() + "] ");
        if (getParent() != null) {
            throw new IllegalStateException("The specified VideoView already has a parent. " +
                    "You must call removeView() on the VideoView's parent first.");
        }
        Context context = getContext();
        setWindowType(VideoView.WindowType.TINY);

        // add to window
        ViewGroup vp = (Utils.scanForActivity(context)).findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(R.id.salient_video_tiny_id);
        if (old != null) {
            vp.removeView(old);
        }
        setId(R.id.salient_video_tiny_id);
        if (lp != null) {
            vp.addView(this, lp);
        } else {
            vp.addView(this);
        }
        //add TextureView
        MediaPlayerManager.instance().removeTextureView();
        MediaPlayerManager.instance().addTextureView(this);
        //update ControlPanel State
        AbsControlPanel controlPanel = getControlPanel();
        if (controlPanel != null) {
            controlPanel.onEnterSecondScreen();
        }
        //update Parent ControlPanel State
        VideoView parentVideoView = getParentVideoView();
        if (parentVideoView != null) {
            AbsControlPanel parentControlPanel = parentVideoView.getControlPanel();
            if (parentControlPanel != null) {
                parentControlPanel.onEnterSecondScreen();
            }
        }

        MediaPlayerManager.instance().updateState(MediaPlayerManager.instance().getPlayerState());
    }

    public enum WindowType {
        NORMAL,
        LIST,
        FULLSCREEN,
        TINY
    }
}