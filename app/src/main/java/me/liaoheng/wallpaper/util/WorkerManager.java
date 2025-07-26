package me.liaoheng.wallpaper.util;

import android.content.Context;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.github.liaoheng.common.util.L;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.liaoheng.wallpaper.model.Config;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.service.BingWallpaperWorker;

/**
 * @author liaoheng
 * @version 2019-03-18 10:42
 */
public class WorkerManager {
    private static final String WORKER_TAG = "bing_wallpaper_worker_" + 0x484;

    public static void disabled(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG);
    }

    /**
     * @param time seconds
     */
    public static boolean enabled(Context context, long time) {
        try {
            PeriodicWorkRequest.Builder builder = new PeriodicWorkRequest.Builder(BingWallpaperWorker.class, time,
                    TimeUnit.SECONDS)
                    .addTag(WORKER_TAG);
            Constraints.Builder constraints = new Constraints.Builder();
            if (Settings.getOnlyWifi(context)) {
                constraints.setRequiredNetworkType(NetworkType.UNMETERED);
            }
            builder.setConstraints(constraints.build());

            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORKER_TAG, ExistingPeriodicWorkPolicy.UPDATE, builder.build());
            return true;
        } catch (Throwable e) {
            L.alog().w("WorkerManager", e, "enable work error");
        }
        return false;
    }

    public static void start(Context context, Wallpaper wallpaper, Config config) {
        OneTimeWorkRequest.Builder builder = new OneTimeWorkRequest.Builder(BingWallpaperWorker.class)
                .addTag(WORKER_TAG);
        Data.Builder dataBuilder = new Data.Builder();
        if (config != null) {
            dataBuilder.putAll(config.getMap());
        }
        if (wallpaper != null) {
            dataBuilder.putAll(wallpaper.getMap());
        }
        builder.setInputData(dataBuilder.build());
        WorkManager.getInstance(context).enqueue(builder.build());
    }

    public static Configuration getConfig(boolean debug) {
        return new Configuration.Builder().setMinimumLoggingLevel(debug ? Log.DEBUG : Log.ERROR)
                .setExecutor(Executors.newSingleThreadExecutor())
                .build();
    }

    public static boolean isScheduled(Context context) {
        ListenableFuture<List<WorkInfo>> statuses = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORKER_TAG);
        try {
            boolean running = false;
            List<WorkInfo> workInfoList = statuses.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED;
            }
            return running;
        } catch (ExecutionException | InterruptedException e) {
            return false;
        }
    }
}
