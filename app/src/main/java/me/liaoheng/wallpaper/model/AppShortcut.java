package me.liaoheng.wallpaper.model;

import java.io.Serializable;

public class AppShortcut implements Serializable {
    private String packageName;
    private int position;

    public AppShortcut() {
    }

    public AppShortcut(String packageName, int position) {
        this.packageName = packageName;
        this.position = position;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AppShortcut that = (AppShortcut) obj;
        return packageName != null ? packageName.equals(that.packageName) : that.packageName == null;
    }

    @Override
    public int hashCode() {
        return packageName != null ? packageName.hashCode() : 0;
    }
}