package com.example.nowlog.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class ImageProcessor {
    private static final int DISPLAY_MAX_EDGE = 1080;
    private static final int THUMB_MAX_EDGE = 300;
    private static final int DISPLAY_QUALITY = 85;
    private static final int THUMB_QUALITY = 80;

    private final Context context;

    public ImageProcessor(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 处理一张图片：复制原图 + 生成展示图 + 生成缩略图
     * @return 三条路径数组 [originalPath, displayPath, thumbPath]
     */
    public String[] processImage(Uri sourceUri, long noteId) throws IOException {
        File noteDir = getNoteImageDir(noteId);
        if (!noteDir.exists()) noteDir.mkdirs();

        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = timestamp + "_" + System.nanoTime();

        // 1. 原样复制
        File originalFile = new File(noteDir, "original_" + fileName + ".jpg");
        copyFile(sourceUri, originalFile);

        // 2. 生成展示图（修正EXIF方向）
        File displayFile = new File(noteDir, "display_" + fileName + ".jpg");
        scaleAndSave(originalFile, displayFile, DISPLAY_MAX_EDGE, DISPLAY_QUALITY);

        // 3. 生成缩略图（修正EXIF方向）
        File thumbFile = new File(noteDir, "thumb_" + fileName + ".jpg");
        scaleAndSave(originalFile, thumbFile, THUMB_MAX_EDGE, THUMB_QUALITY);

        return new String[]{
            originalFile.getAbsolutePath(),
            displayFile.getAbsolutePath(),
            thumbFile.getAbsolutePath()
        };
    }

    public File getNoteImageDir(long noteId) {
        return new File(context.getExternalFilesDir(null), "images/" + noteId);
    }

    private void copyFile(Uri sourceUri, File destFile) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = Files.newOutputStream(destFile.toPath())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }

    private void scaleAndSave(File source, File dest, int maxLongEdge, int quality) throws IOException {
        // 读取 EXIF 方向
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            ExifInterface exif = new ExifInterface(source.getAbsolutePath());
            orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {}

        // 只解码边界
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), opts);

        // 计算 inSampleSize
        opts.inSampleSize = calculateInSampleSize(opts, maxLongEdge);
        opts.inJustDecodeBounds = false;

        Bitmap bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), opts);
        if (bitmap == null) throw new IOException("Failed to decode bitmap");

        // 按 EXIF 旋转
        bitmap = rotateBitmapByExif(bitmap, orientation);

        // 保存
        try (FileOutputStream out = new FileOutputStream(dest)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        }
        bitmap.recycle();
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int maxLongEdge) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;

        int longEdge = Math.max(width, height);
        while (longEdge / inSampleSize > maxLongEdge) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private Bitmap rotateBitmapByExif(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bitmap;
        }
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    /**
     * 删除笔记的所有图片文件
     */
    public void deleteNoteImageFiles(long noteId) {
        File dir = getNoteImageDir(noteId);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            dir.delete();
        }
    }
}
