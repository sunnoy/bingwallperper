package me.liaoheng.wallpaper.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.liaoheng.wallpaper.R;
import me.liaoheng.wallpaper.model.AppShortcut;

public class AppShortcutsAdapter extends RecyclerView.Adapter<AppShortcutsAdapter.AppShortcutViewHolder> {

    private final Context mContext;
    private final List<AppShortcut> mAppShortcuts = new ArrayList<>();
    private boolean mIsEditMode = false;
    private OnAppShortcutListener mListener;

    public interface OnAppShortcutListener {
        void onAppClicked(AppShortcut appShortcut);
        void onAppRemoved(AppShortcut appShortcut, int position);
        void onAppLongClicked(AppShortcut appShortcut);
    }

    public AppShortcutsAdapter(Context context) {
        mContext = context;
    }

    public void setOnAppShortcutListener(OnAppShortcutListener listener) {
        mListener = listener;
    }

    public void setAppShortcuts(List<AppShortcut> shortcuts) {
        mAppShortcuts.clear();
        mAppShortcuts.addAll(shortcuts);
        notifyDataSetChanged();
    }

    public void addAppShortcut(AppShortcut shortcut) {
        mAppShortcuts.add(shortcut);
        notifyItemInserted(mAppShortcuts.size() - 1);
    }

    public void removeAppShortcut(int position) {
        if (position >= 0 && position < mAppShortcuts.size()) {
            mAppShortcuts.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void setEditMode(boolean editMode) {
        mIsEditMode = editMode;
        notifyDataSetChanged();
    }

    public List<AppShortcut> getAppShortcuts() {
        return new ArrayList<>(mAppShortcuts);
    }

    @NonNull
    @Override
    public AppShortcutViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_shortcut, parent, false);
        return new AppShortcutViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppShortcutViewHolder holder, int position) {
        AppShortcut shortcut = mAppShortcuts.get(position);
        holder.bind(shortcut);
    }

    @Override
    public int getItemCount() {
        return mAppShortcuts.size();
    }

    class AppShortcutViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mAppIcon;
        private final TextView mAppName;
        private final ImageButton mRemoveButton;

        public AppShortcutViewHolder(@NonNull View itemView) {
            super(itemView);
            mAppIcon = itemView.findViewById(R.id.app_icon);
            mAppName = itemView.findViewById(R.id.app_name);
            mRemoveButton = itemView.findViewById(R.id.remove_app_button);
        }

        public void bind(AppShortcut shortcut) {
            try {
                PackageManager pm = mContext.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(shortcut.getPackageName(), 0);
                
                // Set app icon
                Drawable icon = pm.getApplicationIcon(appInfo);
                mAppIcon.setImageDrawable(icon);
                
                // Set app name
                String appName = pm.getApplicationLabel(appInfo).toString();
                mAppName.setText(appName);
                
            } catch (PackageManager.NameNotFoundException e) {
                // App not found, use default icon and package name
                mAppIcon.setImageResource(R.mipmap.ic_launcher);
                mAppName.setText(shortcut.getPackageName());
            }

            // Show/hide remove button based on edit mode
            mRemoveButton.setVisibility(mIsEditMode ? View.VISIBLE : View.GONE);

            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (!mIsEditMode && mListener != null) {
                    mListener.onAppClicked(shortcut);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (mListener != null) {
                    mListener.onAppLongClicked(shortcut);
                }
                return true;
            });

            mRemoveButton.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onAppRemoved(shortcut, getAdapterPosition());
                }
            });
        }
    }

    public void launchApp(AppShortcut shortcut) {
        try {
            PackageManager pm = mContext.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(shortcut.getPackageName());
            if (launchIntent != null) {
                mContext.startActivity(launchIntent);
            }
        } catch (Exception e) {
            // Handle launch failure
        }
    }
}