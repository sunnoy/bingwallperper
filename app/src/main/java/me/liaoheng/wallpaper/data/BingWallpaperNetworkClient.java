package me.liaoheng.wallpaper.data;

import android.content.Context;

import com.github.liaoheng.common.util.ValidateUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import me.liaoheng.wallpaper.model.BingWallpaper;
import me.liaoheng.wallpaper.model.BingWallpaperImage;
import me.liaoheng.wallpaper.model.Wallpaper;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;
import me.liaoheng.wallpaper.util.Constants;
import me.liaoheng.wallpaper.util.NetUtils;
import retrofit2.Response;

/**
 * @author liaoheng
 * @version 2016-09-20 11:27
 */
public class BingWallpaperNetworkClient {

    public static Observable<Wallpaper> getBingWallpaper(Context context) {
        return getBingWallpaper(context, 0, 1).map(bingWallpaperImages -> bingWallpaperImages.get(0));
    }

    public static Observable<List<Wallpaper>> getBingWallpaper(Context context, int index,
            int count) {
        String locale = BingWallpaperUtils.getAutoLocale(context);
        String url = BingWallpaperUtils.getUrl(context, index, count, locale);
        return getBingWallpaper(url, locale).flatMap(bingWallpaper -> {
            if (ValidateUtils.isItemEmpty(bingWallpaper.getImages())) {
                return Observable.error(new IOException("bing wallpaper is not data"));
            }
            List<Wallpaper> wallpapers = new ArrayList<>();
            for (BingWallpaperImage image : bingWallpaper.getImages()) {
                wallpapers.add(image.to(bingWallpaper.getTooltips()));
            }
            return Observable.just(wallpapers);
        });
    }

    public static Observable<BingWallpaper> getBingWallpaper(String url, String locale) {
        return NetUtils.get().getBingWallpaperNetworkService()
                .getBingWallpaper(url, getMkt(locale)).subscribeOn(Schedulers.io());
    }

    public static Wallpaper getWallpaper(Context context, boolean cache) throws IOException {
        return getBingWallpaperSingleCall(context, cache);
    }

    public static Wallpaper getBingWallpaperSingleCall(Context context, boolean cache) throws IOException {
        String locale = BingWallpaperUtils.getAutoLocale(context);
        String url = BingWallpaperUtils.getUrl(context);
        String c = "public, ";
        if (cache) {
            c += "max-age=" + 60 * 60 * 12;//12 hour
        } else {
            c += "no-cache";
        }
        return getBingWallpaperSingleCall(url, locale, c);
    }

    public static Wallpaper getBingWallpaperSingleCall(String url, String locale, String cache)
            throws IOException {
        Response<BingWallpaper> execute = NetUtils.get().getBingWallpaperNetworkService()
                .getBingWallpaperCall(url, getMkt(locale), cache).execute();
        if (execute.isSuccessful()) {
            BingWallpaper bingWallpaper = execute.body();
            if (bingWallpaper == null || ValidateUtils.isItemEmpty(bingWallpaper.getImages())) {
                throw new IOException("bing wallpaper is not data");
            }
            return bingWallpaper.getImages().get(0).to(bingWallpaper.getTooltips());
        } else {
            throw new IOException("bing server response failure");
        }
    }

    private static String getMkt(String locale) {
        return String.format(Constants.MKT_HEADER, locale);
    }
    
    /**
     * Get wallpapers for slideshow (past 14 days, or as many as available)
     * @param context Context
     * @return Observable list of wallpapers
     */
    public static Observable<List<Wallpaper>> getSlideshowWallpapers(Context context) {
        return getBingWallpaper(context, 0, 14); // Try to get 14 days of wallpapers, API may return less
    }
}
