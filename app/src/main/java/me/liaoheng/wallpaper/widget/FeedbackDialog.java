package me.liaoheng.wallpaper.widget;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import com.github.liaoheng.common.util.UIUtils;

import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.util.BingWallpaperUtils;

/**
 * @author liaoheng
 * @version 2018-11-01 11:30
 */
public class FeedbackDialog {

    public static Dialog create(final Context context) {
        return new AlertDialog.Builder(context).setMessage(R.string.menu_main_feedback)
                .setPositiveButton(
                        "E-Mail", (dialog, which) -> BingWallpaperUtils.sendFeedback(context))
                .setNegativeButton("Github", (dialog, which) -> BingWallpaperUtils.openBrowser(context,
                        "https://github.com/liaoheng/BingWallpaper/issues"))
                .setNeutralButton("info", (dialog, which) -> {
                    ClipboardManager cmb = (ClipboardManager) context.getSystemService(
                            Context.CLIPBOARD_SERVICE);
                    if (cmb != null) {
                        String info = BingWallpaperUtils.getSystemInfo(context);
                        cmb.setPrimaryClip(ClipData.newPlainText("feedback info", info));
                        UIUtils.showToast(context, R.string.feedback_copied);
                    }
                })
                .create();
    }

}
