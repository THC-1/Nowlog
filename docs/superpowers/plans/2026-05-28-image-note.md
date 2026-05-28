# 图片记录功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Nowlog 新增图片记录功能，支持文字+图片笔记和独立图片笔记，最多9张，三级图片存储，相机/相册双来源。

**Architecture:** 新建 NoteImage 关联表通过 noteId 外键关联 Note 表。图片存入 getExternalFilesDir 私有目录，数据库存路径。编辑页通过 FAB+BottomSheet 选择图片来源，首页卡片显示封面缩略图，新建 ImageViewerActivity 支持全屏查看。

**Tech Stack:** Java, Room, Glide 4.16.0, PhotoView 2.3.0, ViewPager2, ActivityResultContracts, FileProvider

---

## 文件结构

### 新建文件
- `app/build.gradle` — 添加 Glide、PhotoView 依赖
- `app/src/main/java/com/example/nowlog/data/NoteImage.java` — 图片实体
- `app/src/main/java/com/example/nowlog/data/NoteImageDao.java` — 图片 DAO
- `app/src/main/java/com/example/nowlog/util/ImageProcessor.java` — 图片处理工具
- `app/src/main/java/com/example/nowlog/ImageViewerActivity.java` — 全屏查看器
- `app/src/main/res/layout/activity_image_viewer.xml` — 查看器布局
- `app/src/main/res/layout/item_edit_image.xml` — 编辑页图片项布局
- `app/src/main/res/layout/bottom_sheet_image_source.xml` — 图片来源选择弹窗
- `app/src/main/res/xml/file_paths.xml` — FileProvider 路径配置

### 修改文件
- `app/src/main/AndroidManifest.xml` — 添加相机权限、FileProvider、ImageViewerActivity
- `app/src/main/java/com/example/nowlog/data/AppDatabase.java` — 添加 NoteImage 实体、Migration、DAO
- `app/src/main/java/com/example/nowlog/NoteEditActivity.java` — 添加图片功能
- `app/src/main/res/layout/activity_note_edit.xml` — 添加 FAB、图片列表区域
- `app/src/main/java/com/example/nowlog/MainActivity.java` — 加载封面图
- `app/src/main/java/com/example/nowlog/adapter/NoteAdapter.java` — 支持封面图显示
- `app/src/main/res/layout/item_note.xml` — 添加封面图 ImageView

---

### Task 1: 添加依赖和配置

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 添加 Glide 和 PhotoView 依赖**

在 `app/build.gradle` 的 `dependencies` 块中添加：

```groovy
// 图片加载
implementation 'com.github.bumptech.glide:glide:4.16.0'
annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'

// 图片查看（双指缩放）
implementation 'com.github.chrisbanes:PhotoView:2.3.0'
```

- [ ] **Step 2: 创建 FileProvider 路径配置**

创建 `app/src/main/res/xml/file_paths.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="images" path="images/" />
</paths>
```

- [ ] **Step 3: 更新 AndroidManifest.xml**

在 `<application>` 标签内添加相机权限、FileProvider 和 ImageViewerActivity：

```xml
<!-- 在 <application> 标签前添加 -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```

```xml
<!-- 在 <application> 标签内，</application> 前添加 -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
<activity android:name=".ImageViewerActivity" />
```

- [ ] **Step 4: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/build.gradle app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: add Glide, PhotoView dependencies and FileProvider config"
```

---

### Task 2: 创建 NoteImage 实体

**Files:**
- Create: `app/src/main/java/com/example/nowlog/data/NoteImage.java`

- [ ] **Step 1: 创建 NoteImage 实体类**

创建 `app/src/main/java/com/example/nowlog/data/NoteImage.java`：

```java
package com.example.nowlog.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "note_images",
    foreignKeys = @ForeignKey(
        entity = Note.class,
        parentColumns = "id",
        childColumns = "noteId",
        onDelete = ForeignKey.CASCADE
    ),
    indices = @Index(value = "noteId"))
public class NoteImage {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private long noteId;
    private String originalPath;
    private String displayPath;
    private String thumbPath;
    private int sortOrder;
    private boolean isCover;
    private long createdAt;

    public NoteImage(long noteId, String originalPath, String displayPath,
                     String thumbPath, int sortOrder, boolean isCover, long createdAt) {
        this.noteId = noteId;
        this.originalPath = originalPath;
        this.displayPath = displayPath;
        this.thumbPath = thumbPath;
        this.sortOrder = sortOrder;
        this.isCover = isCover;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getNoteId() { return noteId; }
    public void setNoteId(long noteId) { this.noteId = noteId; }
    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public String getDisplayPath() { return displayPath; }
    public void setDisplayPath(String displayPath) { this.displayPath = displayPath; }
    public String getThumbPath() { return thumbPath; }
    public void setThumbPath(String thumbPath) { this.thumbPath = thumbPath; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isCover() { return isCover; }
    public void setCover(boolean cover) { isCover = cover; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 2: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/nowlog/data/NoteImage.java
git commit -m "feat: add NoteImage entity with foreign key to Note"
```

---

### Task 3: 创建 NoteImageDao

**Files:**
- Create: `app/src/main/java/com/example/nowlog/data/NoteImageDao.java`

- [ ] **Step 1: 创建 NoteImageDao 接口**

创建 `app/src/main/java/com/example/nowlog/data/NoteImageDao.java`：

```java
package com.example.nowlog.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

@Dao
public abstract class NoteImageDao {
    @Insert
    public abstract long insert(NoteImage image);

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC")
    public abstract List<NoteImage> getByNoteId(long noteId);

    @Delete
    public abstract void delete(NoteImage image);

    @Query("DELETE FROM note_images WHERE noteId = :noteId")
    public abstract void deleteByNoteId(long noteId);

    @Transaction
    public void updateCover(long noteId, long imageId) {
        clearCover(noteId);
        setCover(noteId, imageId);
    }

    @Query("UPDATE note_images SET isCover = 0 WHERE noteId = :noteId AND isCover = 1")
    protected abstract void clearCover(long noteId);

    @Query("UPDATE note_images SET isCover = 1 WHERE id = :imageId AND noteId = :noteId")
    protected abstract void setCover(long noteId, long imageId);

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY isCover DESC, sortOrder ASC LIMIT 1")
    public abstract NoteImage getCoverByNoteId(long noteId);
}
```

- [ ] **Step 2: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/nowlog/data/NoteImageDao.java
git commit -m "feat: add NoteImageDao with CRUD and cover operations"
```

---

### Task 4: 更新 AppDatabase

**Files:**
- Modify: `app/src/main/java/com/example/nowlog/data/AppDatabase.java`

- [ ] **Step 1: 更新 AppDatabase**

更新 `app/src/main/java/com/example/nowlog/data/AppDatabase.java`，添加 NoteImage 实体、Migration 和 DAO：

```java
package com.example.nowlog.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Note.class, NoteImage.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract NoteImageDao noteImageDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "nowlog.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS note_images ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "noteId INTEGER NOT NULL,"
                    + "originalPath TEXT,"
                    + "displayPath TEXT,"
                    + "thumbPath TEXT,"
                    + "sortOrder INTEGER NOT NULL DEFAULT 0,"
                    + "isCover INTEGER NOT NULL DEFAULT 0,"
                    + "createdAt INTEGER NOT NULL,"
                    + "FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX index_note_images_noteId ON note_images(noteId)");
        }
    };
}
```

- [ ] **Step 2: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/nowlog/data/AppDatabase.java
git commit -m "feat: add NoteImage to AppDatabase with migration v1->v2"
```

---

### Task 5: 创建 ImageProcessor 工具类

**Files:**
- Create: `app/src/main/java/com/example/nowlog/util/ImageProcessor.java`

- [ ] **Step 1: 创建 ImageProcessor**

创建 `app/src/main/java/com/example/nowlog/util/ImageProcessor.java`：

```java
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
     * @return NoteImage 的三条路径数组 [originalPath, displayPath, thumbPath]
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
```

- [ ] **Step 2: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL（需要确认 exifinterface 依赖是否已包含，如果没有需要添加）

- [ ] **Step 3: 添加 exifinterface 依赖（如需要）**

在 `app/build.gradle` 的 dependencies 中添加：

```groovy
implementation 'androidx.exifinterface:exifinterface:1.3.7'
```

- [ ] **Step 4: 再次验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/nowlog/util/ImageProcessor.java app/build.gradle
git commit -m "feat: add ImageProcessor with EXIF-aware scaling"
```

---

### Task 6: 创建编辑页图片相关布局

**Files:**
- Create: `app/src/main/res/layout/item_edit_image.xml`
- Create: `app/src/main/res/layout/bottom_sheet_image_source.xml`
- Modify: `app/src/main/res/layout/activity_note_edit.xml`

- [ ] **Step 1: 创建编辑页图片项布局**

创建 `app/src/main/res/layout/item_edit_image.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp">

    <ImageView
        android:id="@+id/ivImage"
        android:layout_width="match_parent"
        android:layout_height="200dp"
        android:scaleType="centerCrop"
        android:contentDescription="笔记图片" />

    <!-- 删除按钮 -->
    <ImageView
        android:id="@+id/ivDelete"
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:layout_gravity="top|end"
        android:layout_margin="8dp"
        android:background="@drawable/bg_circle_delete"
        android:padding="6dp"
        android:src="@drawable/ic_delete"
        android:contentDescription="删除图片"
        android:scaleType="centerInside" />

    <!-- 拖拽排序手柄（可选区域） -->
    <View
        android:id="@+id/dragHandle"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

- [ ] **Step 2: 创建删除按钮圆形背景**

创建 `app/src/main/res/drawable/bg_circle_delete.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#99000000" />
</shape>
```

- [ ] **Step 3: 创建图片来源选择弹窗布局**

创建 `app/src/main/res/layout/bottom_sheet_image_source.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="选择图片来源"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="?attr/colorOnSurface"
        android:gravity="center"
        android:paddingBottom="16dp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnCamera"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:text="拍照"
        android:textSize="16sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnGallery"
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_marginTop="8dp"
        android:text="从相册选择"
        android:textSize="16sp" />

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnCancel"
        style="@style/Widget.Material3.Button.TextButton"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="8dp"
        android:text="取消" />
</LinearLayout>
```

注意：需要创建 `ic_camera.xml` 和 `ic_gallery.xml` 图标 drawable，或使用 Material Icons。如果不想创建新图标，可以去掉 `app:icon` 属性。

- [ ] **Step 4: 更新编辑页布局**

更新 `app/src/main/res/layout/activity_note_edit.xml`，在 EditText 和 btnSave 之间添加图片列表区域，在底部添加图片 FAB：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    android:orientation="vertical">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="?attr/colorPrimary"
        app:elevation="0dp">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            app:navigationIcon="@drawable/ic_arrow_back_white"
            app:title="新建笔记"
            app:titleTextAppearance="@style/Nowlog.ToolbarTitle"
            app:titleTextColor="@android:color/white" />
    </com.google.android.material.appbar.AppBarLayout>

    <EditText
        android:id="@+id/etContent"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="@android:color/transparent"
        android:gravity="top|start"
        android:hint="写下你的想法..."
        android:inputType="textMultiLine|textCapSentences"
        android:lineSpacingExtra="6dp"
        android:paddingHorizontal="24dp"
        android:paddingVertical="20dp"
        android:textColor="?attr/colorOnSurface"
        android:textColorHint="?attr/colorOnSurfaceVariant"
        android:textSize="16sp" />

    <!-- 图片列表区域 -->
    <LinearLayout
        android:id="@+id/imageSection"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:visibility="gone"
        android:paddingHorizontal="24dp">

        <TextView
            android:id="@+id/tvImageCount"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="图片 (0/9)"
            android:textSize="14sp"
            android:textColor="?attr/colorOnSurfaceVariant"
            android:paddingBottom="8dp" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/rvImages"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:nestedScrollingEnabled="false" />
    </LinearLayout>

    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnSave"
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:layout_marginHorizontal="24dp"
        android:layout_marginBottom="24dp"
        android:text="保存"
        android:textSize="16sp"
        app:backgroundTint="?attr/colorPrimary"
        app:cornerRadius="16dp"
        app:rippleColor="?attr/colorOnPrimary" />

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAddImage"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end|bottom"
        android:layout_marginEnd="24dp"
        android:layout_marginBottom="16dp"
        android:contentDescription="添加图片"
        android:src="@android:drawable/ic_menu_gallery"
        app:fabSize="mini" />

</LinearLayout>
```

- [ ] **Step 5: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/layout/item_edit_image.xml app/src/main/res/drawable/bg_circle_delete.xml app/src/main/res/layout/bottom_sheet_image_source.xml app/src/main/res/layout/activity_note_edit.xml
git commit -m "feat: add image edit layouts and bottom sheet"
```

---

### Task 7: 改造 NoteEditActivity 支持图片

**Files:**
- Modify: `app/src/main/java/com/example/nowlog/NoteEditActivity.java`

- [ ] **Step 1: 更新 NoteEditActivity**

重写 `app/src/main/java/com/example/nowlog/NoteEditActivity.java`，添加图片选择、相机拍照、保存逻辑：

```java
package com.example.nowlog;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nowlog.data.AppDatabase;
import com.example.nowlog.data.Note;
import com.example.nowlog.data.NoteImage;
import com.example.nowlog.util.ImageProcessor;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteEditActivity extends AppCompatActivity {
    private static final int MAX_IMAGES = 9;

    private EditText etContent;
    private View imageSection;
    private TextView tvImageCount;
    private RecyclerView rvImages;
    private FloatingActionButton fabAddImage;
    private AppDatabase db;
    private ExecutorService executor;
    private Handler mainHandler;
    private ImageProcessor imageProcessor;

    private final List<Uri> pendingImageUris = new ArrayList<>();
    private EditImageAdapter imageAdapter;
    private Uri cameraTempUri;

    // 相册多选
    private final ActivityResultLauncher<String> galleryLauncher =
        registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            int remaining = MAX_IMAGES - pendingImageUris.size();
            if (uris.size() > remaining) {
                Toast.makeText(this, "最多只能添加" + MAX_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
                uris = uris.subList(0, remaining);
            }
            pendingImageUris.addAll(uris);
            updateImageUI();
        });

    // 相机拍照
    private final ActivityResultLauncher<Uri> cameraLauncher =
        registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraTempUri != null) {
                pendingImageUris.add(cameraTempUri);
                updateImageUI();
            }
        });

    // 相机权限
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                launchCamera();
            } else {
                Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_SHORT).show();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_note_edit);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        imageProcessor = new ImageProcessor(this);

        etContent = findViewById(R.id.etContent);
        imageSection = findViewById(R.id.imageSection);
        tvImageCount = findViewById(R.id.tvImageCount);
        rvImages = findViewById(R.id.rvImages);
        fabAddImage = findViewById(R.id.fabAddImage);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        findViewById(R.id.btnSave).setOnClickListener(v -> saveNote());

        toolbar.setNavigationOnClickListener(v -> finish());
        fabAddImage.setOnClickListener(v -> showImageSourceSheet());

        setupImageList();
        etContent.requestFocus();
    }

    private void setupImageList() {
        imageAdapter = new EditImageAdapter();
        rvImages.setLayoutManager(new LinearLayoutManager(this));
        rvImages.setAdapter(imageAdapter);

        // 拖拽排序
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition();
                int to = target.getAdapterPosition();
                Collections.swap(pendingImageUris, from, to);
                imageAdapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        };
        new ItemTouchHelper(callback).attachToRecyclerView(rvImages);
    }

    private void showImageSourceSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this)
            .inflate(R.layout.bottom_sheet_image_source, null);
        dialog.setContentView(sheetView);

        sheetView.findViewById(R.id.btnCamera).setOnClickListener(v -> {
            dialog.dismiss();
            checkCameraPermissionAndLaunch();
        });

        sheetView.findViewById(R.id.btnGallery).setOnClickListener(v -> {
            dialog.dismiss();
            galleryLauncher.launch("image/*");
        });

        sheetView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        try {
            File tempFile = new File(imageProcessor.getNoteImageDir(0), "camera_temp.jpg");
            tempFile.getParentFile().mkdirs();
            cameraTempUri = FileProvider.getUriForFile(this,
                getApplicationContext().getPackageName() + ".fileprovider", tempFile);
            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "无法启动相机", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateImageUI() {
        int count = pendingImageUris.size();
        if (count == 0) {
            imageSection.setVisibility(View.GONE);
            fabAddImage.setVisibility(View.VISIBLE);
        } else {
            imageSection.setVisibility(View.VISIBLE);
            tvImageCount.setText("图片 (" + count + "/" + MAX_IMAGES + ")");
            imageAdapter.notifyDataSetChanged();
            fabAddImage.setVisibility(count >= MAX_IMAGES ? View.GONE : View.VISIBLE);
        }
    }

    private void saveNote() {
        String content = etContent.getText().toString().trim();
        boolean hasImages = !pendingImageUris.isEmpty();

        if (content.isEmpty() && !hasImages) {
            Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        findViewById(R.id.btnSave).setEnabled(false);

        executor.execute(() -> {
            try {
                // 1. 插入 Note，获取 noteId
                Note note = new Note(content.isEmpty() ? "" : content, System.currentTimeMillis());
                long noteId = db.noteDao().insert(note);

                // 2. 处理图片
                for (int i = 0; i < pendingImageUris.size(); i++) {
                    Uri uri = pendingImageUris.get(i);
                    String[] paths = imageProcessor.processImage(uri, noteId);
                    NoteImage noteImage = new NoteImage(
                        noteId,
                        paths[0], // original
                        paths[1], // display
                        paths[2], // thumb
                        i,        // sortOrder
                        i == 0,   // 第一张为封面
                        System.currentTimeMillis()
                    );
                    db.noteImageDao().insert(noteImage);
                }

                mainHandler.post(() -> {
                    Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, "保存失败: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                    findViewById(R.id.btnSave).setEnabled(true);
                });
            }
        });
    }

    // 编辑页图片列表适配器
    private class EditImageAdapter extends RecyclerView.Adapter<EditImageAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_edit_image, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Uri uri = pendingImageUris.get(position);
            holder.ivImage.setImageURI(uri);
            holder.ivDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                pendingImageUris.remove(pos);
                notifyItemRemoved(pos);
                updateImageUI();
            });
        }

        @Override
        public int getItemCount() {
            return pendingImageUris.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivImage;
            ImageView ivDelete;

            VH(View view) {
                super(view);
                ivImage = view.findViewById(R.id.ivImage);
                ivDelete = view.findViewById(R.id.ivDelete);
            }
        }
    }
}
```

- [ ] **Step 2: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/nowlog/NoteEditActivity.java
git commit -m "feat: add image picking, camera capture, and save with images"
```

---

### Task 8: 更新首页卡片支持封面图

**Files:**
- Modify: `app/src/main/res/layout/item_note.xml`
- Modify: `app/src/main/java/com/example/nowlog/adapter/NoteAdapter.java`
- Modify: `app/src/main/java/com/example/nowlog/MainActivity.java`

- [ ] **Step 1: 更新卡片布局**

更新 `app/src/main/res/layout/item_note.xml`，在文字上方添加封面图 ImageView：

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="16dp"
    android:layout_marginVertical="5dp"
    android:clickable="true"
    android:focusable="true"
    app:cardBackgroundColor="?attr/colorSurfaceContainerLow"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeColor="?attr/colorOutlineVariant"
    app:strokeWidth="0.5dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingHorizontal="20dp"
        android:paddingTop="16dp"
        android:paddingBottom="14dp">

        <!-- 封面图（有图片时显示） -->
        <com.google.android.material.imageview.ShapeableImageView
            android:id="@+id/ivCover"
            android:layout_width="match_parent"
            android:layout_height="160dp"
            android:scaleType="centerCrop"
            android:visibility="gone"
            android:contentDescription="封面图"
            app:shapeAppearanceOverlay="@style/Nowlog.CardImage" />

        <TextView
            android:id="@+id/tvContent"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="0dp"
            android:ellipsize="end"
            android:lineSpacingExtra="4dp"
            android:maxLines="3"
            android:textColor="?attr/colorOnSurface"
            android:textSize="15sp" />

        <View
            android:layout_width="match_parent"
            android:layout_height="0.5dp"
            android:layout_marginTop="12dp"
            android:layout_marginBottom="10dp"
            android:background="?attr/colorOutlineVariant" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <ImageView
                android:layout_width="14dp"
                android:layout_height="14dp"
                android:src="@drawable/ic_time"
                android:contentDescription="@null"
                app:tint="?attr/colorOnSurfaceVariant" />

            <TextView
                android:id="@+id/tvTime"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="6dp"
                android:textColor="?attr/colorOnSurfaceVariant"
                android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: 添加封面图圆角样式**

在 `app/src/main/res/values/themes.xml` 中添加（如果已有 Nowlog 主题样式，在其中追加）：

```xml
<style name="Nowlog.CardImage" parent="">
    <item name="cornerSizeTopLeft">12dp</item>
    <item name="cornerSizeTopRight">12dp</item>
    <item name="cornerSizeBottomLeft">0dp</item>
    <item name="cornerSizeBottomRight">0dp</item>
</style>
```

- [ ] **Step 3: 更新 NoteAdapter**

重写 `app/src/main/java/com/example/nowlog/adapter/NoteAdapter.java`：

```java
package com.example.nowlog.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nowlog.R;
import com.example.nowlog.data.Note;
import com.example.nowlog.data.NoteImage;
import com.example.nowlog.util.TimeFormatter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {
    private List<Note> notes = new ArrayList<>();
    private Map<Long, NoteImage> coverMap = new HashMap<>();
    private OnNoteLongClickListener longClickListener;
    private OnNoteClickListener clickListener;

    public interface OnNoteLongClickListener {
        void onNoteLongClick(Note note);
    }

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public NoteAdapter(OnNoteLongClickListener longClickListener, OnNoteClickListener clickListener) {
        this.longClickListener = longClickListener;
        this.clickListener = clickListener;
    }

    public void setData(List<Note> notes, Map<Long, NoteImage> coverMap) {
        this.notes = notes;
        this.coverMap = coverMap;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.tvContent.setText(note.getContent());
        holder.tvTime.setText(TimeFormatter.format(note.getCreatedAt()));

        // 封面图
        NoteImage cover = coverMap.get(note.getId());
        if (cover != null) {
            holder.ivCover.setVisibility(View.VISIBLE);
            Glide.with(holder.ivCover)
                .load(new File(cover.getThumbPath()))
                .centerCrop()
                .into(holder.ivCover);
        } else {
            holder.ivCover.setVisibility(View.GONE);
            holder.ivCover.setImageDrawable(null);
        }

        // 纯图片笔记（文字为空）时隐藏文字区域
        if (note.getContent() == null || note.getContent().isEmpty()) {
            holder.tvContent.setVisibility(View.GONE);
        } else {
            holder.tvContent.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onNoteLongClick(note);
            return true;
        });

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onNoteClick(note);
        });
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivCover;
        TextView tvContent;
        TextView tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.ivCover);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
```

- [ ] **Step 4: 更新 MainActivity**

更新 `app/src/main/java/com/example/nowlog/MainActivity.java`，加载封面图并传给 Adapter：

```java
package com.example.nowlog;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nowlog.adapter.NoteAdapter;
import com.example.nowlog.data.AppDatabase;
import com.example.nowlog.data.Note;
import com.example.nowlog.data.NoteImage;
import com.example.nowlog.util.ImageProcessor;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements NoteAdapter.OnNoteLongClickListener, NoteAdapter.OnNoteClickListener {
    private RecyclerView recyclerView;
    private View tvEmpty;
    private NoteAdapter adapter;
    private AppDatabase db;
    private ExecutorService executor;
    private Handler mainHandler;
    private ImageProcessor imageProcessor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db = AppDatabase.getInstance(this);
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        imageProcessor = new ImageProcessor(this);

        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        FloatingActionButton fab = findViewById(R.id.fab);

        adapter = new NoteAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        fab.setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteEditActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }

    private void loadNotes() {
        executor.execute(() -> {
            List<Note> notes = db.noteDao().getAll();
            Map<Long, NoteImage> coverMap = new HashMap<>();
            for (Note note : notes) {
                NoteImage cover = db.noteImageDao().getCoverByNoteId(note.getId());
                if (cover != null) coverMap.put(note.getId(), cover);
            }
            mainHandler.post(() -> {
                adapter.setData(notes, coverMap);
                tvEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(notes.isEmpty() ? View.GONE : View.VISIBLE);
            });
        });
    }

    @Override
    public void onNoteLongClick(Note note) {
        new AlertDialog.Builder(this)
                .setTitle("删除笔记")
                .setMessage("确定要删除这条笔记吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executor.execute(() -> {
                        // 先删文件
                        imageProcessor.deleteNoteImageFiles(note.getId());
                        // 再删数据库（NoteImage 通过外键级联删除）
                        db.noteDao().delete(note);
                        mainHandler.post(this::loadNotes);
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onNoteClick(Note note) {
        // 点击笔记：如果有图片，打开图片查看器；否则不做操作（后续可扩展编辑）
        executor.execute(() -> {
            List<NoteImage> images = db.noteImageDao().getByNoteId(note.getId());
            if (!images.isEmpty()) {
                ArrayList<String> displayPaths = new ArrayList<>();
                ArrayList<String> originalPaths = new ArrayList<>();
                for (NoteImage img : images) {
                    displayPaths.add(img.getDisplayPath());
                    originalPaths.add(img.getOriginalPath());
                }
                mainHandler.post(() -> {
                    Intent intent = new Intent(this, ImageViewerActivity.class);
                    intent.putStringArrayListExtra("displayPaths", displayPaths);
                    intent.putStringArrayListExtra("originalPaths", originalPaths);
                    intent.putExtra("currentPosition", 0);
                    startActivity(intent);
                });
            }
        });
    }
}
```

- [ ] **Step 5: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/layout/item_note.xml app/src/main/java/com/example/nowlog/adapter/NoteAdapter.java app/src/main/java/com/example/nowlog/MainActivity.java
git commit -m "feat: show cover image on note cards and click to view"
```

---

### Task 9: 创建图片查看器

**Files:**
- Create: `app/src/main/res/layout/activity_image_viewer.xml`
- Create: `app/src/main/java/com/example/nowlog/ImageViewerActivity.java`

- [ ] **Step 1: 创建图片查看器布局**

创建 `app/src/main/res/layout/activity_image_viewer.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPager"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 底部操作栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:background="#66000000"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/tvIndex"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="14sp" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnViewOriginal"
            style="@style/Widget.Material3.Button.TextButton"
            android:layout_width="wrap_content"
            android:layout_height="40dp"
            android:text="查看原图"
            android:textColor="#FFFFFF" />

        <ProgressBar
            android:id="@+id/progressBar"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:visibility="gone" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 2: 创建 ImageViewerActivity**

创建 `app/src/main/java/com/example/nowlog/ImageViewerActivity.java`：

```java
package com.example.nowlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private TextView tvIndex;
    private MaterialButton btnViewOriginal;
    private ProgressBar progressBar;

    private ArrayList<String> displayPaths;
    private ArrayList<String> originalPaths;
    private int currentPosition;
    private boolean[] originalLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_viewer);

        // 沉浸式全屏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        displayPaths = getIntent().getStringArrayListExtra("displayPaths");
        originalPaths = getIntent().getStringArrayListExtra("originalPaths");
        currentPosition = getIntent().getIntExtra("currentPosition", 0);

        if (displayPaths == null || displayPaths.isEmpty()) {
            finish();
            return;
        }

        originalLoaded = new boolean[displayPaths.size()];

        viewPager = findViewById(R.id.viewPager);
        tvIndex = findViewById(R.id.tvIndex);
        btnViewOriginal = findViewById(R.id.btnViewOriginal);
        progressBar = findViewById(R.id.progressBar);

        ImagePagerAdapter adapter = new ImagePagerAdapter();
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentPosition, false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                updateUI();
            }
        });

        btnViewOriginal.setOnClickListener(v -> loadOriginal());

        updateUI();
    }

    private void updateUI() {
        tvIndex.setText((currentPosition + 1) + " / " + displayPaths.size());
        if (originalLoaded[currentPosition]) {
            btnViewOriginal.setText("已加载原图");
            btnViewOriginal.setEnabled(false);
        } else {
            btnViewOriginal.setText("查看原图");
            btnViewOriginal.setEnabled(true);
        }
    }

    private void loadOriginal() {
        if (originalPaths == null || currentPosition >= originalPaths.size()) return;

        btnViewOriginal.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);

        String originalPath = originalPaths.get(currentPosition);
        PhotoView photoView = getCurrentPhotoView();
        if (photoView != null) {
            Glide.with(this)
                .load(new File(originalPath))
                .into(photoView);

            originalLoaded[currentPosition] = true;
            progressBar.setVisibility(View.GONE);
            btnViewOriginal.setVisibility(View.VISIBLE);
            updateUI();
        }
    }

    private PhotoView getCurrentPhotoView() {
        // 获取当前 ViewPager2 中的 PhotoView
        RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(currentPosition);
        if (vh instanceof ImagePagerAdapter.ImageViewHolder) {
            return ((ImagePagerAdapter.ImageViewHolder) vh).photoView;
        }
        return null;
    }

    private class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {
        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PhotoView photoView = new PhotoView(parent.getContext());
            photoView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            return new ImageViewHolder(photoView);
        }

        @Override
        public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
            Glide.with(holder.photoView)
                .load(new File(displayPaths.get(position)))
                .into(holder.photoView);
        }

        @Override
        public int getItemCount() {
            return displayPaths.size();
        }

        class ImageViewHolder extends RecyclerView.ViewHolder {
            PhotoView photoView;
            ImageViewHolder(View view) {
                super(view);
                photoView = (PhotoView) view;
            }
        }
    }
}
```

- [ ] **Step 3: 验证构建**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/activity_image_viewer.xml app/src/main/java/com/example/nowlog/ImageViewerActivity.java
git commit -m "feat: add ImageViewerActivity with zoom and swipe"
```

---

### Task 10: 集成测试与验证

**Files:** 无新文件，手动验证

- [ ] **Step 1: 构建完整 APK**

运行：`./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 验证数据库迁移**

确认 `AppDatabase` 版本号为 2，Migration 包含 FOREIGN KEY 和 INDEX。

- [ ] **Step 3: 验证图片目录结构**

确认 `ImageProcessor` 使用 `getExternalFilesDir(null)` + `"images/" + noteId`，与 `file_paths.xml` 中的 `external-files-path` 一致。

- [ ] **Step 4: 验证保存校验**

确认 `saveNote()` 中校验逻辑：文字为空且图片为空时提示，否则允许保存。

- [ ] **Step 5: 验证删除逻辑**

确认 `onNoteLongClick` 中先调用 `imageProcessor.deleteNoteImageFiles()` 再删除 Note。

- [ ] **Step 6: 提交最终状态**

```bash
git status
git log --oneline -10
```

确认所有文件已提交，工作区干净。
