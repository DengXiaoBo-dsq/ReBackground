package com.dsq.rebackground.utils;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class BitmapCache {
    private static BitmapCache instance;
    private Map<String, Bitmap> bitmapCache; // 缓存 Bitmap
    private Map<String, ImageInfo> imageInfoCache; // 缓存图片属性信息

    // 私有构造函数
    private BitmapCache() {
        bitmapCache = new HashMap<>();
        imageInfoCache = new HashMap<>();
    }

    // 获取单例实例
    public static BitmapCache getInstance() {
        if (instance == null) {
            synchronized (BitmapCache.class) {
                if (instance == null) {
                    instance = new BitmapCache();
                }
            }
        }
        return instance;
    }

    // 缓存图片和属性信息
    public void put(String key, Bitmap bitmap, ImageInfo imageInfo) {
        if (bitmap == null || imageInfo == null) {
            throw new IllegalArgumentException("Bitmap and ImageInfo cannot be null");
        }
        bitmapCache.put(key, bitmap);
        imageInfoCache.put(key, imageInfo);
    }

    // 获取缓存的图片
    public Bitmap getBitmap(String key) {
        return bitmapCache.get(key);
    }

    // 获取缓存的图片属性信息
    public ImageInfo getImageInfo(String key) {
        return imageInfoCache.get(key);
    }

    // 清理指定缓存
    public void remove(String key) {
        Bitmap bitmap = bitmapCache.get(key);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        bitmapCache.remove(key);
        imageInfoCache.remove(key);
    }

    // 清理所有缓存
    public void clear() {
        for (Bitmap bitmap : bitmapCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        bitmapCache.clear();
        imageInfoCache.clear();
    }

    // 图片属性信息类
    public static class ImageInfo {
        private String path;
        private int width;
        private int height;
        private String format;
        private long fileSize;
        private int orientation; // 新增字段

        public ImageInfo(String path, int width, int height, String format, long fileSize, int orientation) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.format = format;
            this.fileSize = fileSize;
            this.orientation = orientation; // 保存方向信息


        }

        // 新增 Getter
        public int getOrientation() {
            return orientation;
        }
        public String getPath() {
            return path;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public String getFormat() {
            return format;
        }

        public long getFileSize() {
            return fileSize;
        }
    }
}