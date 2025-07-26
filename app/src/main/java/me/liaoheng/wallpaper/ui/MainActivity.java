package me.liaoheng.wallpaper.ui;

import static android.Manifest.permission.POST_NOTIFICATIONS;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.github.clans.fab.FloatingActionButton;
import com.github.liaoheng.common.util.Callback;
import com.github.liaoheng.common.util.DisplayUtils;
import com.github.liaoheng.common.util.LanguageContextWrapper;
import com.github.liaoheng.common.util.ROM;
import com.github.liaoheng.common.util.SystemDataException;
import com.github.liaoheng.common.util.UIUtils;
import com.github.liaoheng.common.util.YNCallback;
import com.google.android.material.navigation.NavigationView;

import java.io.File;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.adapter.AppShortcutsAdapter;
import me.liaoheng.wallpaper.data.BingWallpaperNetworkClient;
import me.liaoheng.wallpaper.databinding.ActivityMainBinding;
import me.liaoheng.wallpaper.model.AppShortcut;
import me.liaoheng.wallpaper.model.BingWallpaperState;
import me.liaoheng.wallpaper.model.Config;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;
import me.liaoheng.wallpaper.util.BottomViewListener;
import me.liaoheng.wallpaper.util.Callback4;
import me.liaoheng.wallpaper.util.Constants;
import me.liaoheng.wallpaper.util.CrashReportHandle;
import me.liaoheng.wallpaper.util.DelayedHandler;
import me.liaoheng.wallpaper.util.DownloadHelper;
import me.liaoheng.wallpaper.util.GlideApp;
import me.liaoheng.wallpaper.util.SetWallpaperStateBroadcastReceiverHelper;
import me.liaoheng.wallpaper.util.Settings;
import me.liaoheng.wallpaper.util.TasksUtils;
import me.liaoheng.wallpaper.util.UIHelper;
import me.liaoheng.wallpaper.util.WallpaperUtils;
import me.liaoheng.wallpaper.widget.FeedbackDialog;

/**
 * 壁纸主界面
 *
 * @author liaoheng
 * @version 2017-2-15
 */
public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener, BottomViewListener {

    private boolean mIsHomeLauncher = false;

    @Override
    protected void attachBaseContext(Context context) {
        super.attachBaseContext(LanguageContextWrapper.wrap(context, BingWallpaperUtils.getLanguage(context)));
    }

    private ImageView mNavigationHeaderImage;
    private TextView mNavigationHeaderCoverStoryTitleView;

    private ActivityMainBinding mViewBinding;

    private Dialog mFeedbackDialog;

    private SetWallpaperStateBroadcastReceiverHelper mSetWallpaperStateBroadcastReceiverHelper;
    @Nullable
    private Wallpaper mCurWallpaper;
    private boolean isRun;
    private int mActionMenuBottomMargin;
    private UIHelper mUiHelper;
    private DownloadHelper mDownloadHelper;
    private Config.Builder mConfig;
    private final MutableLiveData<Configuration> mConfigurationChangedHandler = new MutableLiveData<>();

    // Clock widget fields
    private TextView mClockTime;
    private TextView mClockDate;
    private TextView mClockDayOfWeek;
    private Handler mClockHandler = new Handler(Looper.getMainLooper());
    private Runnable mClockUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockDisplay();
            mClockHandler.postDelayed(this, 1000); // Update every second
        }
    };

    // App shortcuts fields
    private RecyclerView mAppShortcutsRecycler;
    private AppShortcutsAdapter mAppShortcutsAdapter;
    private List<AppShortcut> mAppShortcuts = new ArrayList<>();
    private android.widget.ImageButton mAddAppButton;
    private boolean mIsEditMode = false;
    
    // UI visibility fields
    private boolean mIsUIVisible = false;
    private static final int UI_HIDE_DELAY = 3000; // 3 seconds
    private Handler mUIHideHandler = new Handler(Looper.getMainLooper());
    private Runnable mUIHideRunnable = new Runnable() {
        @Override
        public void run() {
            hideUI();
        }
    };
    
    // Slideshow fields
    private List<Wallpaper> mSlideshowWallpapers = new ArrayList<>();
    private int mCurrentSlideshowIndex = 0;
    private Handler mSlideshowHandler = new Handler(Looper.getMainLooper());
    private Runnable mSlideshowRunnable = new Runnable() {
        @Override
        public void run() {
            switchToNextWallpaper();
        }
    };

    final int MSG_GET_BING_WALLPAPER = 1;
    final DelayedHandler mHandler = new DelayedHandler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == MSG_GET_BING_WALLPAPER) {
                getBingWallpaper();
                return true;
            }
            return false;
        }
    });

    @Override
    public void showBottomView() {
        int navigationBarHeight = BingWallpaperUtils.getNavigationBarHeight(this);
        if (navigationBarHeight > 0) {
            showBottomView(navigationBarHeight);
        }
    }

    public void showBottomView(int navigationBarHeight) {
        UIUtils.viewVisible(mViewBinding.bingWallpaperBottom);
        ViewGroup.LayoutParams layoutParams = mViewBinding.bingWallpaperBottom.getLayoutParams();
        layoutParams.height = navigationBarHeight;
        mViewBinding.bingWallpaperBottom.setLayoutParams(layoutParams);

        ViewGroup.MarginLayoutParams menuLayoutParams = (ViewGroup.MarginLayoutParams) mViewBinding.bingWallpaperSetMenu
                .getLayoutParams();
        menuLayoutParams.bottomMargin = mActionMenuBottomMargin + navigationBarHeight;
        mViewBinding.bingWallpaperSetMenu.setLayoutParams(menuLayoutParams);
    }

    @Override
    public void hideBottomView() {
        UIUtils.viewGone(mViewBinding.bingWallpaperBottom);

        ViewGroup.MarginLayoutParams menuLayoutParams = (ViewGroup.MarginLayoutParams) mViewBinding.bingWallpaperSetMenu
                .getLayoutParams();
        menuLayoutParams.bottomMargin = mActionMenuBottomMargin;
        mViewBinding.bingWallpaperSetMenu.setLayoutParams(menuLayoutParams);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TasksUtils.isOne()) {
            UIUtils.startActivity(this, IntroActivity.class);
            finishAfterTransition();
            return;
        }
        
        // Check if launched as home launcher
        handleHomeLauncherIntent();
        
        initTranslucent();
        mViewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mViewBinding.getRoot());
        initStatusBarAddToolbar();
        mConfigurationChangedHandler.observe(this, configuration -> {
            if (mCurWallpaper == null) {
                mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 200);
                return;
            }
            loadImage(new Callback.EmptyCallback<File>() {
                @Override
                public void onSuccess(File file) {
                    mViewBinding.bingWallpaperView.setImageBitmap(
                            BitmapFactory.decodeFile(file.getAbsolutePath()));
                }
            });
        });

        mActionMenuBottomMargin = DisplayUtils.dp2px(this, 10);
        mConfig = new Config.Builder();
        mUiHelper = new UIHelper();
        mUiHelper.register(this, this);

        mFeedbackDialog = FeedbackDialog.create(this);

        mViewBinding.navigationDrawer.setNavigationItemSelectedListener(this);

        ((View) mViewBinding.bingWallpaperCoverStoryToggle.getParent()).setOnClickListener(
                v -> mViewBinding.bingWallpaperCoverStoryToggle.toggle());
        mViewBinding.bingWallpaperCoverStoryToggle.setOnCheckedChangeListener((view, isChecked) -> {
            if (mCurWallpaper != null) {
                UIUtils.toggleVisibility(mViewBinding.bingWallpaperCoverStoryText);
            }
        });
        mSetWallpaperStateBroadcastReceiverHelper = new SetWallpaperStateBroadcastReceiverHelper(
                new Callback4.EmptyCallback<BingWallpaperState>() {
                    @Override
                    public void onYes(BingWallpaperState bingWallpaperState) {
                        Toast.makeText(getApplicationContext(), R.string.set_wallpaper_success, Toast.LENGTH_LONG)
                                .show();
                    }

                    @Override
                    public void onNo(BingWallpaperState bingWallpaperState) {
                        Toast.makeText(getApplicationContext(), R.string.set_wallpaper_failure, Toast.LENGTH_LONG)
                                .show();
                    }

                    @Override
                    public void onFinish(BingWallpaperState bingWallpaperState) {
                        dismissProgressDialog();
                    }
                });

        mViewBinding.bingWallpaperSwipeRefresh.setOnRefreshListener(() -> {
            if (isRun) {
                UIUtils.showToast(getApplicationContext(), R.string.set_wallpaper_running);
            } else {
                mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 200);
            }
        });
        mNavigationHeaderImage = mViewBinding.navigationDrawer.getHeaderView(0)
                .findViewById(R.id.navigation_header_image);
        mNavigationHeaderCoverStoryTitleView = mViewBinding.navigationDrawer.getHeaderView(0)
                .findViewById(R.id.navigation_header_cover_story_title);
        mDownloadHelper = new DownloadHelper(this, TAG);

        // Initialize clock widget
        initClockWidget();
        
        // Initialize app shortcuts
        initAppShortcuts();
        
        // Setup screen tap listener
        setupScreenTapListener();

        if (BingWallpaperUtils.isConnected(getApplicationContext())) {
            showSwipeRefreshLayout();
            if (Settings.isSlideshowEnabled(this)) {
                initializeSlideshow();
            } else {
                mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 1000);
            }
        } else {
            mViewBinding.bingWallpaperError.setText(getString(R.string.network_unavailable));
        }

        BingWallpaperUtils.showMiuiDialog(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { POST_NOTIFICATIONS }, 123);
        }
    }

    @SuppressLint({ "SetTextI18n", "CheckResult" })
    private void getBingWallpaper() {
        if (!BingWallpaperUtils.isConnected(getApplicationContext())) {
            mViewBinding.bingWallpaperError.setText(getString(R.string.network_unavailable));
            return;
        }
        showSwipeRefreshLayout();

        BingWallpaperNetworkClient.getBingWallpaper(this)
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bingWallpaperImage -> {
                    if (bingWallpaperImage == null) {
                        setBingWallpaperError(new SystemDataException("Bing not data"));
                        return;
                    }
                    mCurWallpaper = bingWallpaperImage;
                    if (TextUtils.isEmpty(bingWallpaperImage.getDesc())) {
                        UIUtils.viewParentGone(mViewBinding.bingWallpaperCoverStoryText.getParent());
                    } else {
                        UIUtils.viewParentVisible(mViewBinding.bingWallpaperCoverStoryText.getParent());
                        mViewBinding.bingWallpaperCoverStoryText.setText(bingWallpaperImage.getDesc());
                    }

                    // Update last refresh time on successful wallpaper load
                    updateLastRefreshTime();
                    setImage(bingWallpaperImage);
                }, this::setBingWallpaperError);
    }

    @SuppressLint("SetTextI18n")
    private void setBingWallpaperError(Throwable throwable) {
        dismissProgressDialog();
        String error = CrashReportHandle.loadFailed(this, TAG, throwable);
        mViewBinding.bingWallpaperError.setText(getString(R.string.pull_refresh) + error);
    }

    /**
     * @param type 0. both , 1. home , 2. lock
     */
    private void setWallpaper(int type) {
        if (isRun) {
            UIUtils.showToast(getApplicationContext(), R.string.set_wallpaper_running);
            return;
        }
        if (mCurWallpaper == null) {
            return;
        }
        String url = getUrl();
        BingWallpaperUtils.setWallpaperDialog(this, mCurWallpaper.copy(url),
                mConfig.setWallpaperMode(type).loadConfig(this).build(),
                new YNCallback.EmptyCallback() {
                    @Override
                    public void onAllow() {
                        isRun = true;
                        mViewBinding.bingWallpaperSwipeRefresh.post(
                                () -> mViewBinding.bingWallpaperSwipeRefresh.setRefreshing(true));
                    }
                });
    }

    private String getUrl() {
        return getUrl(BingWallpaperUtils.getResolution(this, true));
    }

    private String getSaveUrl() {
        return getUrl(Settings.getSaveResolution(this));
    }

    private String getUrl(String resolution) {
        if (mCurWallpaper == null) {
            throw new IllegalArgumentException("image is null");
        }
        return BingWallpaperUtils.getImageUrl(this, resolution,
                mCurWallpaper.getBaseUrl());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId_ = item.getItemId();
        if (itemId_ == android.R.id.home) {
            mViewBinding.drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void dismissSwipeRefreshLayout() {
        mViewBinding.bingWallpaperSetMenu.post(() -> mViewBinding.bingWallpaperSetMenu.showMenu(true));
        dismissProgressDialog();
    }

    private void dismissProgressDialog() {
        isRun = false;
        mViewBinding.bingWallpaperSwipeRefresh.post(() -> mViewBinding.bingWallpaperSwipeRefresh.setRefreshing(false));
    }

    private void showSwipeRefreshLayout() {
        mViewBinding.bingWallpaperError.setText("");
        showProgressDialog();
    }

    private void showProgressDialog() {
        isRun = true;
        mViewBinding.bingWallpaperSetMenu.hideMenu(true);
        mViewBinding.bingWallpaperSwipeRefresh.post(() -> mViewBinding.bingWallpaperSwipeRefresh.setRefreshing(true));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 123) {
                recreate();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_main_drawer_settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), 123);
        } else if (item.getItemId() == R.id.menu_main_drawer_wallpaper_history_list) {
            UIUtils.startActivity(this, WallpaperHistoryListActivity.class);
        } else if (item.getItemId() == R.id.menu_main_drawer_wallpaper_info) {
            if (mCurWallpaper != null) {
                BingWallpaperUtils.openBrowser(this, mCurWallpaper);
            }
        } else if (item.getItemId() == R.id.menu_main_drawer_help) {
            BingWallpaperUtils.openBrowser(this, "https://github.com/liaoheng/BingWallpaper/wiki");
        } else if (item.getItemId() == R.id.menu_main_drawer_feedback) {
            UIUtils.showDialog(mFeedbackDialog);
        }
        mViewBinding.drawerLayout.closeDrawers();
        return true;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mConfigurationChangedHandler.postValue(newConfig);
    }

    private void loadImage(Callback<File> callback) {
        WallpaperUtils.loadImage(GlideApp.with(this).asFile()
                .load(getUrl())
                .dontAnimate()
                .thumbnail(0.5f)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL), mViewBinding.bingWallpaperView, callback);
    }

    private void loadMenuImage() {
        if (isDestroyed()) {
            return;
        }
        GlideApp.with(this)
                .asBitmap()
                .load(getUrl(Constants.WallpaperConfig.MAIN_WALLPAPER_RESOLUTION))
                .dontAnimate()
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .addListener(new RequestListener<Bitmap>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e,
                            Object model, Target<Bitmap> target, boolean isFirstResource) {
                        setBingWallpaperError(e);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target,
                            DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(new BitmapImageViewTarget(mNavigationHeaderImage) {

                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                            @Nullable Transition<? super Bitmap> transition) {
                        super.onResourceReady(resource, transition);
                        parseWallpaper(resource, mCurWallpaper);
                        loadImage(new Callback.EmptyCallback<File>() {
                            @Override
                            public void onPreExecute() {
                                showSwipeRefreshLayout();
                            }

                            @Override
                            public void onPostExecute() {
                                dismissSwipeRefreshLayout();
                            }

                            @Override
                            public void onSuccess(File file) {
                                mViewBinding.bingWallpaperView.setImageBitmap(
                                        BitmapFactory.decodeFile(file.getAbsolutePath()));
                            }

                            @Override
                            public void onError(Throwable e) {
                                setBingWallpaperError(e);
                            }
                        });
                    }
                });
    }

    private void setImage(Wallpaper image) {
        if (isDestroyed()) {
            return;
        }
        setTitle(image.getTitle());
        mNavigationHeaderCoverStoryTitleView.setText(image.getTitle());
        loadMenuImage();
    }

    private void parseWallpaper(@NonNull Bitmap bitmap, Wallpaper image) {
        int defMuted = ContextCompat.getColor(getActivity(), R.color.colorPrimaryDark);
        int defVibrant = ContextCompat.getColor(getActivity(), R.color.colorAccent);
        try {
            Palette.from(bitmap)
                    .generate(palette -> {
                        int lightMutedSwatch = defMuted;
                        int lightVibrantSwatch = defVibrant;

                        if (palette != null) {
                            lightMutedSwatch = palette.getMutedColor(defMuted);
                            lightVibrantSwatch = palette.getVibrantColor(defVibrant);
                            if (lightMutedSwatch == defMuted) {
                                if (lightVibrantSwatch != defVibrant) {
                                    lightMutedSwatch = lightVibrantSwatch;
                                }
                            }
                        }

                        initSetWallpaperActionMenu(lightMutedSwatch, lightVibrantSwatch, image);
                    });
        } catch (OutOfMemoryError e) {
            initSetWallpaperActionMenu(defMuted, defVibrant, image);
        }
    }

    private void initSetWallpaperActionMenu(@ColorInt int lightMutedSwatch, int lightVibrantSwatch, Wallpaper image) {
        mViewBinding.bingWallpaperSetMenu.removeAllMenuButtons();
        mViewBinding.bingWallpaperSetMenu.setMenuButtonColorNormal(lightMutedSwatch);
        mViewBinding.bingWallpaperSetMenu.setMenuButtonColorPressed(lightMutedSwatch);
        mViewBinding.bingWallpaperSetMenu.setMenuButtonColorRipple(lightVibrantSwatch);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            AddBothActionButton(image, lightMutedSwatch, lightVibrantSwatch, false);
        } else {
            AddBothActionButton(image, lightMutedSwatch, lightVibrantSwatch,
                    !ROM.getROM().isMiui());
        }

        mViewBinding.bingWallpaperSetMenu.showMenu(true);
    }

    private void AddBothActionButton(Wallpaper image, @ColorInt int lightMutedSwatch,
            @ColorInt int lightVibrantSwatch, boolean mini) {
        addActionButton(lightMutedSwatch, lightVibrantSwatch,
                getString(R.string.share),
                R.drawable.ic_share_24dp, v -> {
                    WallpaperUtils.shareImage(this, mConfig.loadConfig(this).build(),
                            getUrl(), image.getTitle());
                    mViewBinding.bingWallpaperSetMenu.close(true);
                });

        addActionButton(lightMutedSwatch, lightVibrantSwatch,
                getString(R.string.save),
                R.drawable.ic_save_white_24dp, v -> {
                    if (mCurWallpaper == null) {
                        return;
                    }
                    BingWallpaperUtils.showSaveWallpaperDialog(this, new YNCallback() {
                        @Override
                        public void onAllow() {
                            mViewBinding.bingWallpaperSetMenu.close(true);
                            mDownloadHelper.saveWallpaper(getActivity(), getSaveUrl());
                        }

                        @Override
                        public void onDeny() {

                        }
                    });
                });

        if (!mini) {
            addActionButton(lightMutedSwatch, lightVibrantSwatch,
                    getString(R.string.pref_set_wallpaper_auto_mode_home),
                    R.drawable.ic_home_white_24dp, v -> {
                        setWallpaper(1);
                        mViewBinding.bingWallpaperSetMenu.close(true);
                    });

            addActionButton(lightMutedSwatch, lightVibrantSwatch,
                    getString(R.string.pref_set_wallpaper_auto_mode_lock),
                    R.drawable.ic_lock_white_24dp, v -> {
                        setWallpaper(2);
                        mViewBinding.bingWallpaperSetMenu.close(true);
                    });
        }

        addActionButton(lightMutedSwatch, lightVibrantSwatch,
                mini ? getString(R.string.set_wallpaper) : getString(R.string.pref_set_wallpaper_auto_mode_both),
                R.drawable.ic_smartphone_white_24dp, v -> {
                    setWallpaper(0);
                    mViewBinding.bingWallpaperSetMenu.close(true);
                });
    }

    private void addActionButton(@ColorInt int lightMutedSwatch, @ColorInt int lightVibrantSwatch, String text,
            @DrawableRes int resId, View.OnClickListener listener) {
        FloatingActionButton actionButton = new FloatingActionButton(getActivity());
        actionButton.setLabelText(text);
        actionButton.setColorNormal(lightMutedSwatch);
        actionButton.setColorPressed(lightMutedSwatch);
        actionButton.setColorRipple(lightVibrantSwatch);
        actionButton.setImageResource(resId);
        actionButton.setButtonSize(FloatingActionButton.SIZE_MINI);
        mViewBinding.bingWallpaperSetMenu.addMenuButton(actionButton);
        actionButton.setLabelColors(lightMutedSwatch, lightMutedSwatch, lightVibrantSwatch);
        actionButton.setOnClickListener(listener);
    }

    @Override
    protected void onStart() {
        if (mSetWallpaperStateBroadcastReceiverHelper != null) {
            mSetWallpaperStateBroadcastReceiverHelper.register(this);
        }
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-refresh wallpaper data when app is resumed
        autoRefreshWallpaper();
    }

    private void autoRefreshWallpaper() {
        // Only auto-refresh if network is available and not currently loading
        if (!BingWallpaperUtils.isConnected(getApplicationContext()) || isRun) {
            return;
        }
        
        // Check if we need to refresh (e.g., if it's been more than 6 hours since last update)
        if (shouldAutoRefresh()) {
            if (Settings.isSlideshowEnabled(this)) {
                // In slideshow mode, reinitialize slideshow to get latest wallpapers
                initializeSlideshow();
            } else {
                // In normal mode, get today's wallpaper
                mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 500);
            }
        }
    }

    private boolean shouldAutoRefresh() {
        // Always refresh if no current wallpaper is loaded
        if (mCurWallpaper == null) {
            return true;
        }
        
        // Get the last refresh time from preferences
        long lastRefreshTime = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                .getLong("last_refresh_time", 0);
        long currentTime = System.currentTimeMillis();
        
        // Auto-refresh if it's been more than 6 hours since last refresh
        long sixHoursInMillis = 6 * 60 * 60 * 1000;
        return (currentTime - lastRefreshTime) > sixHoursInMillis;
    }

    private void updateLastRefreshTime() {
        getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                .edit()
                .putLong("last_refresh_time", System.currentTimeMillis())
                .apply();
    }

    @Override
    protected void onStop() {
        if (mSetWallpaperStateBroadcastReceiverHelper != null) {
            mSetWallpaperStateBroadcastReceiverHelper.unregister(this);
        }
        super.onStop();
    }

    private void initClockWidget() {
        mClockTime = findViewById(R.id.clock_time);
        mClockDate = findViewById(R.id.clock_date);
        mClockDayOfWeek = findViewById(R.id.clock_day_of_week);
        
        // Apply customization settings
        applyClockCustomization();
        
        // Start clock updates
        mClockHandler.post(mClockUpdateRunnable);
    }
    
    private void applyClockCustomization() {
        // Apply font sizes
        if (mClockTime != null) {
            mClockTime.setTextSize(Settings.getClockTimeSize(this));
        }
        if (mClockDate != null) {
            mClockDate.setTextSize(Settings.getClockDateSize(this));
        }
        if (mClockDayOfWeek != null) {
            mClockDayOfWeek.setTextSize(Settings.getClockDaySize(this));
        }
        
        // Apply background alpha for clock container
        View clockContainer = findViewById(R.id.clock_widget_container);
        if (clockContainer != null) {
            int alpha = Settings.getClockBackgroundAlpha(this);
            // Set the alpha on the background drawable instead of the whole view
            android.graphics.drawable.Drawable background = clockContainer.getBackground();
            if (background != null) {
                background.setAlpha((int)(alpha * 2.55f)); // Convert 0-100 to 0-255
            }
        }
        
        // Apply app shortcuts alpha
        View appShortcutsContainer = findViewById(R.id.app_shortcuts_container);
        if (appShortcutsContainer != null) {
            int alpha = Settings.getAppShortcutsAlpha(this);
            android.graphics.drawable.Drawable background = appShortcutsContainer.getBackground();
            if (background != null) {
                background.setAlpha((int)(alpha * 2.55f)); // Convert 0-100 to 0-255
            }
        }
    }

    private void updateClockDisplay() {
        Calendar calendar = Calendar.getInstance();
        
        // Format time
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String timeText = timeFormat.format(calendar.getTime());
        
        // Format date
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        String dateText = dateFormat.format(calendar.getTime());
        
        // Format day of week
        String[] dayNames = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        String dayOfWeekText = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1];
        
        // Update UI
        if (mClockTime != null) mClockTime.setText(timeText);
        if (mClockDate != null) mClockDate.setText(dateText);
        if (mClockDayOfWeek != null) mClockDayOfWeek.setText(dayOfWeekText);
    }

    private void initAppShortcuts() {
        mAppShortcutsRecycler = findViewById(R.id.app_shortcuts_recycler);
        
        // Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        mAppShortcutsRecycler.setLayoutManager(layoutManager);
        
        // Initialize adapter
        mAppShortcutsAdapter = new AppShortcutsAdapter(this);
        mAppShortcutsAdapter.setOnAppShortcutListener(new AppShortcutsAdapter.OnAppShortcutListener() {
            @Override
            public void onAppClicked(AppShortcut appShortcut) {
                if (!mIsEditMode) {
                    mAppShortcutsAdapter.launchApp(appShortcut);
                }
            }

            @Override
            public void onAppRemoved(AppShortcut appShortcut, int position) {
                mAppShortcuts.remove(appShortcut);
                mAppShortcutsAdapter.removeAppShortcut(position);
                saveAppShortcuts();
                
                // Exit edit mode if no apps left
                if (mAppShortcuts.isEmpty()) {
                    setEditMode(false);
                }
            }

            @Override
            public void onAppLongClicked(AppShortcut appShortcut) {
                setEditMode(true);
            }
        });
        
        mAppShortcutsRecycler.setAdapter(mAppShortcutsAdapter);
        
        // Load saved shortcuts
        loadAppShortcuts();
        
        // Setup add button
        mAddAppButton = findViewById(R.id.add_app_shortcut_button);
        mAddAppButton.setOnClickListener(v -> {
            if (mIsEditMode) {
                setEditMode(false);
            } else {
                showAppSelectionDialog();
            }
        });
        
        // Add long click listener to enter edit mode
        mAppShortcutsRecycler.setOnLongClickListener(v -> {
            if (!mAppShortcuts.isEmpty()) {
                setEditMode(true);
                return true;
            }
            return false;
        });
        
        // App shortcuts container will be shown/hidden via screen tap
    }

    private void loadAppShortcuts() {
        // Load from SharedPreferences
        String shortcutsJson = Settings.getAppShortcuts(this);
        mAppShortcuts.clear();
        
        if (!TextUtils.isEmpty(shortcutsJson)) {
            try {
                // Parse JSON string
                String[] packages = shortcutsJson.split(";");
                for (int i = 0; i < packages.length; i++) {
                    if (!packages[i].trim().isEmpty()) {
                        AppShortcut shortcut = new AppShortcut(packages[i].trim(), i);
                        mAppShortcuts.add(shortcut);
                    }
                }
            } catch (Exception e) {
                // If parsing fails, start with empty list
                mAppShortcuts.clear();
            }
        }
        
        mAppShortcutsAdapter.setAppShortcuts(mAppShortcuts);
    }

    private void saveAppShortcuts() {
        // Save to SharedPreferences
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mAppShortcuts.size(); i++) {
            if (i > 0) sb.append(";");
            sb.append(mAppShortcuts.get(i).getPackageName());
        }
        Settings.setAppShortcuts(this, sb.toString());
    }

    private void showAppSelectionDialog() {
        // Limit to 10 shortcuts for better UI
        if (mAppShortcuts.size() >= 10) {
            Toast.makeText(this, "最多只能添加10个应用快捷方式", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            // Get all launchable apps using Intent.ACTION_MAIN with CATEGORY_LAUNCHER
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.Intent mainIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN, null);
            mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
            
            java.util.List<android.content.pm.ResolveInfo> launchableApps = pm.queryIntentActivities(mainIntent, 0);
            
            // Filter out already added apps and create simple list
            java.util.List<String> appNames = new java.util.ArrayList<>();
            java.util.List<String> packageNames = new java.util.ArrayList<>();
            
            for (android.content.pm.ResolveInfo app : launchableApps) {
                String packageName = app.activityInfo.packageName;
                boolean alreadyExists = false;
                for (AppShortcut existing : mAppShortcuts) {
                    if (existing.getPackageName().equals(packageName)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    try {
                        String appName = app.loadLabel(pm).toString();
                        appNames.add(appName);
                        packageNames.add(packageName);
                    } catch (Exception e) {
                        // Skip apps that can't be loaded
                        continue;
                    }
                }
            }
            
            if (appNames.isEmpty()) {
                Toast.makeText(this, "所有应用都已添加", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Create simple dialog with array
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("选择应用 (" + mAppShortcuts.size() + "/10)");
            
            String[] appNamesArray = appNames.toArray(new String[0]);
            
            builder.setItems(appNamesArray, (dialog, which) -> {
                try {
                    String selectedPackage = packageNames.get(which);
                    String selectedName = appNames.get(which);
                    
                    AppShortcut newShortcut = new AppShortcut(selectedPackage, mAppShortcuts.size());
                    mAppShortcuts.add(newShortcut);
                    mAppShortcutsAdapter.addAppShortcut(newShortcut);
                    saveAppShortcuts();
                    
                    Toast.makeText(MainActivity.this, "已添加 " + selectedName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "添加应用失败", Toast.LENGTH_SHORT).show();
                }
            });
            
            builder.setNegativeButton("取消", null);
            builder.show();
            
        } catch (Exception e) {
            Toast.makeText(this, "获取应用列表失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setEditMode(boolean editMode) {
        mIsEditMode = editMode;
        
        // Update adapter edit mode
        if (mAppShortcutsAdapter != null) {
            mAppShortcutsAdapter.setEditMode(editMode);
        }
        
        // Update add button icon and text
        if (mAddAppButton != null) {
            if (editMode) {
                mAddAppButton.setImageResource(R.drawable.ic_check_white_24dp);
                mAddAppButton.setContentDescription("完成编辑");
            } else {
                mAddAppButton.setImageResource(R.drawable.ic_add_white_24dp);
                mAddAppButton.setContentDescription("添加应用快捷方式");
            }
        }
        
        // Show toast to inform user
        if (editMode) {
            Toast.makeText(this, "编辑模式：点击 X 删除应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupScreenTapListener() {
        mViewBinding.bingWallpaperView.setOnClickListener(v -> toggleUIVisibility());
        
        // Add long press listener for clock customization
        View clockContainer = findViewById(R.id.clock_widget_container);
        if (clockContainer != null) {
            clockContainer.setOnLongClickListener(v -> {
                showClockCustomizationDialog();
                return true;
            });
        }
    }
    
    private void toggleUIVisibility() {
        if (mIsUIVisible) {
            hideUI();
        } else {
            showUI();
        }
    }
    
    private void showUI() {
        mIsUIVisible = true;
        
        // Show main UI components with animation (except clock which is always visible)
        View appShortcutsContainer = findViewById(R.id.app_shortcuts_container);
        View mainUIContainer = findViewById(R.id.main_ui_container);
        
        // Always show app shortcuts container when UI is shown (regardless of whether there are apps)
        if (appShortcutsContainer != null) {
            appShortcutsContainer.setVisibility(View.VISIBLE);
            appShortcutsContainer.setAlpha(0f);
            appShortcutsContainer.animate().alpha(1f).setDuration(300).start();
        }
        
        if (mainUIContainer != null) {
            mainUIContainer.setVisibility(View.VISIBLE);
            mainUIContainer.setAlpha(0f);
            mainUIContainer.animate().alpha(1f).setDuration(300).start();
        }
        
        // Show floating action menu if wallpaper is loaded
        if (mCurWallpaper != null) {
            mViewBinding.bingWallpaperSetMenu.setVisibility(View.VISIBLE);
            mViewBinding.bingWallpaperSetMenu.showMenu(true);
        }
        
        // Show slideshow indicator if in slideshow mode
        if (Settings.isSlideshowEnabled(this) && !mSlideshowWallpapers.isEmpty()) {
            View indicator = findViewById(R.id.slideshow_indicator);
            if (indicator != null) {
                indicator.setVisibility(View.VISIBLE);
                indicator.setAlpha(0f);
                indicator.animate().alpha(1f).setDuration(300).start();
            }
        }
        
        // Schedule auto-hide
        mUIHideHandler.removeCallbacks(mUIHideRunnable);
        mUIHideHandler.postDelayed(mUIHideRunnable, UI_HIDE_DELAY);
    }
    
    private void hideUI() {
        mIsUIVisible = false;
        
        // Hide UI components with animation (except clock which stays visible)
        View appShortcutsContainer = findViewById(R.id.app_shortcuts_container);
        View mainUIContainer = findViewById(R.id.main_ui_container);
        
        if (appShortcutsContainer != null) {
            appShortcutsContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> 
                appShortcutsContainer.setVisibility(View.GONE)).start();
        }
        
        if (mainUIContainer != null) {
            mainUIContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> 
                mainUIContainer.setVisibility(View.GONE)).start();
        }
        
        // Hide floating action menu
        mViewBinding.bingWallpaperSetMenu.hideMenu(true);
        mViewBinding.bingWallpaperSetMenu.setVisibility(View.GONE);
        
        // Hide slideshow indicator
        View indicator = findViewById(R.id.slideshow_indicator);
        if (indicator != null) {
            indicator.animate().alpha(0f).setDuration(300).withEndAction(() -> 
                indicator.setVisibility(View.GONE)).start();
        }
        
        // Cancel auto-hide timer
        mUIHideHandler.removeCallbacks(mUIHideRunnable);
    }
    
    private void showClockCustomizationDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("时钟自定义设置");
        
        // Get current settings
        int currentTimeSize = Settings.getClockTimeSize(this);
        int currentDateSize = Settings.getClockDateSize(this);
        int currentDaySize = Settings.getClockDaySize(this);
        int currentClockAlpha = Settings.getClockBackgroundAlpha(this);
        int currentAppAlpha = Settings.getAppShortcutsAlpha(this);
        
        // Setup sliders - we'll create them programmatically for now
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(50, 30, 50, 30);
        
        // Time size slider
        android.widget.TextView timeSizeLabel = new android.widget.TextView(this);
        timeSizeLabel.setText("时间字体大小: " + currentTimeSize + "sp");
        android.widget.SeekBar timeSizeSeeker = new android.widget.SeekBar(this);
        timeSizeSeeker.setMax(200);
        timeSizeSeeker.setProgress(currentTimeSize);
        timeSizeSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 20) progress = 20; // Minimum size
                timeSizeLabel.setText("时间字体大小: " + progress + "sp");
                if (mClockTime != null) mClockTime.setTextSize(progress);
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // Date size slider
        android.widget.TextView dateSizeLabel = new android.widget.TextView(this);
        dateSizeLabel.setText("日期字体大小: " + currentDateSize + "sp");
        android.widget.SeekBar dateSizeSeeker = new android.widget.SeekBar(this);
        dateSizeSeeker.setMax(80);
        dateSizeSeeker.setProgress(currentDateSize);
        dateSizeSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 10) progress = 10; // Minimum size
                dateSizeLabel.setText("日期字体大小: " + progress + "sp");
                if (mClockDate != null) mClockDate.setTextSize(progress);
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // Day size slider
        android.widget.TextView daySizeLabel = new android.widget.TextView(this);
        daySizeLabel.setText("星期字体大小: " + currentDaySize + "sp");
        android.widget.SeekBar daySizeSeeker = new android.widget.SeekBar(this);
        daySizeSeeker.setMax(50);
        daySizeSeeker.setProgress(currentDaySize);
        daySizeSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 8) progress = 8; // Minimum size
                daySizeLabel.setText("星期字体大小: " + progress + "sp");
                if (mClockDayOfWeek != null) mClockDayOfWeek.setTextSize(progress);
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // Clock background alpha
        android.widget.TextView clockAlphaLabel = new android.widget.TextView(this);
        clockAlphaLabel.setText("时钟背景透明度: " + currentClockAlpha + "%");
        android.widget.SeekBar clockAlphaSeeker = new android.widget.SeekBar(this);
        clockAlphaSeeker.setMax(100);
        clockAlphaSeeker.setProgress(currentClockAlpha);
        clockAlphaSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                clockAlphaLabel.setText("时钟背景透明度: " + progress + "%");
                View clockContainer = findViewById(R.id.clock_widget_container);
                if (clockContainer != null) {
                    android.graphics.drawable.Drawable background = clockContainer.getBackground();
                    if (background != null) {
                        background.setAlpha((int)(progress * 2.55f));
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // App shortcuts alpha
        android.widget.TextView appAlphaLabel = new android.widget.TextView(this);
        appAlphaLabel.setText("应用快捷方式透明度: " + currentAppAlpha + "%");
        android.widget.SeekBar appAlphaSeeker = new android.widget.SeekBar(this);
        appAlphaSeeker.setMax(100);
        appAlphaSeeker.setProgress(currentAppAlpha);
        appAlphaSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                appAlphaLabel.setText("应用快捷方式透明度: " + progress + "%");
                View appContainer = findViewById(R.id.app_shortcuts_container);
                if (appContainer != null) {
                    android.graphics.drawable.Drawable background = appContainer.getBackground();
                    if (background != null) {
                        background.setAlpha((int)(progress * 2.55f));
                    }
                }
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // Slideshow settings
        android.widget.TextView slideshowTitle = new android.widget.TextView(this);
        slideshowTitle.setText("轮播设置");
        slideshowTitle.setTextSize(16);
        slideshowTitle.setTextColor(android.graphics.Color.BLACK);
        slideshowTitle.setPadding(0, 30, 0, 10);
        
        // Slideshow enable/disable
        android.widget.CheckBox slideshowEnabled = new android.widget.CheckBox(this);
        slideshowEnabled.setText("启用轮播模式");
        slideshowEnabled.setChecked(Settings.isSlideshowEnabled(this));
        
        // Slideshow interval
        android.widget.TextView intervalLabel = new android.widget.TextView(this);
        int currentInterval = Settings.getSlideshowInterval(this);
        intervalLabel.setText("轮播间隔: " + currentInterval + "小时");
        android.widget.SeekBar intervalSeeker = new android.widget.SeekBar(this);
        intervalSeeker.setMax(24); // Max 24 hours
        intervalSeeker.setProgress(currentInterval);
        intervalSeeker.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (progress < 1) progress = 1; // Minimum 1 hour
                intervalLabel.setText("轮播间隔: " + progress + "小时");
            }
            
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        
        // Add all views to container
        container.addView(timeSizeLabel);
        container.addView(timeSizeSeeker);
        container.addView(dateSizeLabel);
        container.addView(dateSizeSeeker);
        container.addView(daySizeLabel);
        container.addView(daySizeSeeker);
        container.addView(clockAlphaLabel);
        container.addView(clockAlphaSeeker);
        container.addView(appAlphaLabel);
        container.addView(appAlphaSeeker);
        container.addView(slideshowTitle);
        container.addView(slideshowEnabled);
        container.addView(intervalLabel);
        container.addView(intervalSeeker);
        
        builder.setView(container);
        
        builder.setPositiveButton("保存", (dialog, which) -> {
            // Save settings
            Settings.setClockTimeSize(this, Math.max(20, timeSizeSeeker.getProgress()));
            Settings.setClockDateSize(this, Math.max(10, dateSizeSeeker.getProgress()));
            Settings.setClockDaySize(this, Math.max(8, daySizeSeeker.getProgress()));
            Settings.setClockBackgroundAlpha(this, clockAlphaSeeker.getProgress());
            Settings.setAppShortcutsAlpha(this, appAlphaSeeker.getProgress());
            
            // Save slideshow settings
            boolean wasEnabled = Settings.isSlideshowEnabled(this);
            boolean newEnabled = slideshowEnabled.isChecked();
            Settings.setSlideshowEnabled(this, newEnabled);
            Settings.setSlideshowInterval(this, Math.max(1, intervalSeeker.getProgress()));
            
            // Handle slideshow mode change
            if (wasEnabled != newEnabled) {
                if (newEnabled) {
                    // Enable slideshow
                    initializeSlideshow();
                } else {
                    // Disable slideshow
                    stopSlideshowTimer();
                    mSlideshowWallpapers.clear();
                    mCurrentSlideshowIndex = 0;
                    
                    // Hide slideshow indicator
                    updateSlideshowIndicator(0, 0);
                    
                    // Load today's wallpaper
                    mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 1000);
                }
            } else if (newEnabled) {
                // Slideshow is still enabled, restart timer with new interval
                startSlideshowTimer();
            }
            
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton("取消", (dialog, which) -> {
            // Restore original settings
            applyClockCustomization();
        });
        
        builder.setNeutralButton("重置", (dialog, which) -> {
            // Reset to defaults
            Settings.setClockTimeSize(this, 48);
            Settings.setClockDateSize(this, 16);
            Settings.setClockDaySize(this, 14);
            Settings.setClockBackgroundAlpha(this, 80);
            Settings.setAppShortcutsAlpha(this, 80);
            applyClockCustomization();
            Toast.makeText(this, "已重置为默认设置", Toast.LENGTH_SHORT).show();
        });
        
        builder.show();
    }
    
    private void initializeSlideshow() {
        BingWallpaperNetworkClient.getSlideshowWallpapers(this)
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(wallpapers -> {
                    if (wallpapers != null && !wallpapers.isEmpty()) {
                        mSlideshowWallpapers.clear();
                        mSlideshowWallpapers.addAll(wallpapers);
                        mCurrentSlideshowIndex = 0;
                        
                        // Update last refresh time on successful slideshow load
                        updateLastRefreshTime();
                        
                        // Load first wallpaper
                        loadSlideshowWallpaper(mCurrentSlideshowIndex);
                        
                        // Start slideshow timer
                        startSlideshowTimer();
                    } else {
                        // Fallback to normal mode
                        mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 1000);
                    }
                }, throwable -> {
                    // Error loading slideshow, fallback to normal mode
                    setBingWallpaperError(throwable);
                    mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 1000);
                });
    }
    
    private void loadSlideshowWallpaper(int index) {
        if (index >= 0 && index < mSlideshowWallpapers.size()) {
            Wallpaper wallpaper = mSlideshowWallpapers.get(index);
            mCurWallpaper = wallpaper;
            
            // Update UI
            if (TextUtils.isEmpty(wallpaper.getDesc())) {
                UIUtils.viewParentGone(mViewBinding.bingWallpaperCoverStoryText.getParent());
            } else {
                UIUtils.viewParentVisible(mViewBinding.bingWallpaperCoverStoryText.getParent());
                mViewBinding.bingWallpaperCoverStoryText.setText(wallpaper.getDesc());
            }
            
            // Update slideshow indicator
            updateSlideshowIndicator(index + 1, mSlideshowWallpapers.size());
            
            setImage(wallpaper);
        }
    }
    
    private void updateSlideshowIndicator(int current, int total) {
        View indicator = findViewById(R.id.slideshow_indicator);
        TextView counter = findViewById(R.id.slideshow_counter);
        
        if (Settings.isSlideshowEnabled(this) && indicator != null && counter != null) {
            counter.setText(current + "/" + total);
            // Only show indicator when UI is visible
            if (mIsUIVisible) {
                indicator.setVisibility(View.VISIBLE);
            } else {
                indicator.setVisibility(View.GONE);
            }
        } else if (indicator != null) {
            indicator.setVisibility(View.GONE);
        }
    }
    
    private void switchToNextWallpaper() {
        if (!mSlideshowWallpapers.isEmpty()) {
            mCurrentSlideshowIndex = (mCurrentSlideshowIndex + 1) % mSlideshowWallpapers.size();
            loadSlideshowWallpaper(mCurrentSlideshowIndex);
            
            // Schedule next switch
            if (Settings.isSlideshowEnabled(this)) {
                startSlideshowTimer();
            }
        }
    }
    
    private void startSlideshowTimer() {
        stopSlideshowTimer();
        
        if (Settings.isSlideshowEnabled(this)) {
            int intervalHours = Settings.getSlideshowInterval(this);
            
            // Calculate delay to next interval boundary
            long currentTime = System.currentTimeMillis();
            long hourInMillis = 60 * 60 * 1000;
            long intervalInMillis = intervalHours * hourInMillis;
            
            // Find the next scheduled time (based on interval from midnight)
            long midnightToday = (currentTime / (24 * hourInMillis)) * (24 * hourInMillis);
            long nextScheduledTime = midnightToday;
            
            // Find the next interval boundary
            while (nextScheduledTime <= currentTime) {
                nextScheduledTime += intervalInMillis;
            }
            
            long delay = nextScheduledTime - currentTime;
            
            mSlideshowHandler.postDelayed(mSlideshowRunnable, delay);
        }
    }
    
    private void stopSlideshowTimer() {
        mSlideshowHandler.removeCallbacks(mSlideshowRunnable);
    }
    
    public void toggleSlideshowMode() {
        boolean isEnabled = Settings.isSlideshowEnabled(this);
        Settings.setSlideshowEnabled(this, !isEnabled);
        
        if (!isEnabled) {
            // Enable slideshow
            initializeSlideshow();
        } else {
            // Disable slideshow
            stopSlideshowTimer();
            mSlideshowWallpapers.clear();
            mCurrentSlideshowIndex = 0;
            
            // Hide slideshow indicator
            updateSlideshowIndicator(0, 0);
            
            // Load today's wallpaper
            mHandler.sendDelayed(MSG_GET_BING_WALLPAPER, 1000);
        }
    }

    @Override
    protected void onDestroy() {
        // Stop clock updates
        if (mClockHandler != null) {
            mClockHandler.removeCallbacks(mClockUpdateRunnable);
        }
        
        // Stop UI hide handler
        if (mUIHideHandler != null) {
            mUIHideHandler.removeCallbacks(mUIHideRunnable);
        }
        
        // Stop slideshow timer
        if (mSlideshowHandler != null) {
            mSlideshowHandler.removeCallbacks(mSlideshowRunnable);
        }
        
        if (mUiHelper != null) {
            mUiHelper.unregister(this);
        }
        if (mDownloadHelper != null) {
            mDownloadHelper.destroy();
        }
        UIUtils.cancelToast();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (mDownloadHelper != null && mCurWallpaper != null) {
            mDownloadHelper.onRequestPermissionsResult(requestCode, grantResults, getSaveUrl());
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void handleHomeLauncherIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (Intent.ACTION_MAIN.equals(action)) {
                // Check if this was launched from HOME button
                if (intent.hasCategory(Intent.CATEGORY_HOME)) {
                    mIsHomeLauncher = true;
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If we're running as home launcher, don't exit on back press
        // Instead, just hide the UI to show the wallpaper
        if (mIsHomeLauncher) {
            if (mIsUIVisible) {
                hideUI();
            } else {
                // If UI is already hidden, minimize to show other apps
                moveTaskToBack(true);
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleHomeLauncherIntent();
    }
}
