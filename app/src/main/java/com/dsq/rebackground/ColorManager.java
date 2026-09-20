package com.dsq.rebackground;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ColorManager {
    private static final String PREFS_NAME = "color_favorites";
    private static final String KEY_GROUPS = "groups";
    private static ColorManager instance;
    private SharedPreferences prefs;
    private Gson gson;

    private ColorManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    public static synchronized ColorManager getInstance(Context context) {
        if (instance == null) {
            instance = new ColorManager(context);
        }
        return instance;
    }

    public List<FavoriteGroup> getAllGroups() {
        String json = prefs.getString(KEY_GROUPS, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<FavoriteGroup>>(){}.getType();
        List<FavoriteGroup> groups = gson.fromJson(json, type);
        return groups != null ? groups : new ArrayList<>();
    }

    public void saveGroups(List<FavoriteGroup> groups) {
        String json = gson.toJson(groups);
        prefs.edit().putString(KEY_GROUPS, json).apply();
    }

    /**
     * 同步保存，使用 commit 确保数据落盘
     */
    public void saveGroupsSync(List<FavoriteGroup> groups) {
        String json = gson.toJson(groups);
        prefs.edit().putString(KEY_GROUPS, json).commit();
    }

    public void addColorToGroup(String groupName, String colorHex) {
        List<FavoriteGroup> groups = getAllGroups();
        FavoriteGroup target = null;
        for (FavoriteGroup g : groups) {
            if (g.groupName.equals(groupName)) {
                target = g;
                break;
            }
        }
        if (target == null) {
            target = new FavoriteGroup(groupName);
            groups.add(target);
        }
        if (!target.colors.contains(colorHex)) {
            target.colors.add(colorHex);
        }
        saveGroups(groups);
    }

    public List<String> getColorsInGroup(String groupName) {
        for (FavoriteGroup g : getAllGroups()) {
            if (g.groupName.equals(groupName)) {
                return g.colors;
            }
        }
        return new ArrayList<>();
    }
}