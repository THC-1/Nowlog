# Nowlog 图片记录功能设计文档

日期：2026-05-28

## 概述

为 Nowlog 笔记应用新增图片记录功能，支持在文字笔记中附加图片（最多9张），也支持创建独立的图片笔记。图片来源支持相册选取和相机拍照。采用三级图片存储策略（原图/展示图/缩略图），首页卡片默认显示首张缩略图作为封面，用户可自定义封面。

## 架构决策

- **方案选择**：新建 `NoteImage` 关联表（方案A），通过 `noteId` 外键关联 `Note` 表
- **图片存储**：拷贝到应用私有目录（`getExternalFilesDir`），数据库存文件路径
- **图片加载**：统一使用 Glide 4.16.0，自动处理内存缓存和 OOM 防护
- **图片处理**：后台线程执行，避免阻塞主线程

## 数据模型

### NoteImage 实体

```java
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
    private long noteId;        // 外键，关联 Note.id
    private String originalPath; // 原图路径（原样复制，不压缩）
    private String displayPath;  // 展示图路径（长边 1080px，JPEG 85%）
    private String thumbPath;    // 缩略图路径（长边 300px，JPEG 80%）
    private int sortOrder;       // 排序序号（0-8）
    private boolean isCover;     // 是否为封面图（INTEGER 存储，0/1）
    private long createdAt;      // 创建时间戳

    // getter / setter 全部补齐
}
```

### NoteImageDao

```java
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
        setCover(imageId);
    }

    @Query("UPDATE note_images SET isCover = 0 WHERE noteId = :noteId AND isCover = 1")
    protected abstract void clearCover(long noteId);

    @Query("UPDATE note_images SET isCover = 1 WHERE id = :imageId")
    protected abstract void setCover(long imageId);

    // 兜底：优先返回封面图，没有则返回第一张（sortOrder 最小）
    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY isCover DESC, sortOrder ASC LIMIT 1")
    public abstract NoteImage getCoverByNoteId(long noteId);
}
```

### AppDatabase 变更

- 版本号升级：1 → 2
- 添加 `NoteImage` 到 `@entities`
- 新增 `noteImageDao()` 抽象方法
- 编写 Migration(1, 2)

```java
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
            + "createdAt INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX index_note_images_noteId ON note_images(noteId)");
    }
};
```

### 文件删除逻辑

删除图片时先删文件再删数据库记录，避免"幽灵图片"堆积：

```java
public void deleteImageWithFiles(NoteImage image) {
    deleteFile(image.getOriginalPath());
    deleteFile(image.getDisplayPath());
    deleteFile(image.getThumbPath());
    noteImageDao.delete(image);
}

private void deleteFile(String path) {
    if (path != null) {
        File file = new File(path);
        if (file.exists()) file.delete();
    }
}
```

批量删除笔记时同理：先查出所有图片 → 删文件 → 再删数据库记录。

## 图片处理管线

### 应用私有目录结构

```
files/
  images/
    {noteId}/
      original_{timestamp}.jpg    // 原图（原样复制）
      display_{timestamp}.jpg     // 展示图（长边 1080px，JPEG 85%）
      thumb_{timestamp}.jpg       // 缩略图（长边 300px，JPEG 80%）
```

### 压缩策略

| 级别 | 处理方式 | 质量 | 用途 |
|------|---------|------|------|
| original | 原样复制，不压缩 | 原始 | 全屏查看、导出 |
| display | 长边 1080px，修正EXIF方向 | JPEG 85% | 详情页、Canvas |
| thumb | 长边 300px，修正EXIF方向 | JPEG 80% | 首页封面、网格 |

### 保存流程

```
用户在编辑页选择图片 URI / 相机拍照 URI
        ↓
暂存 URI 列表（内存中），不立刻入库
        ↓
点击保存
        ↓
ExecutorService 后台线程
  → 先 insert Note，拿到 noteId（@Insert 返回 long）
  → 为该 noteId 创建目录 files/images/{noteId}/
  → 遍历暂存 URI 列表：
      → 原样复制到 originalPath
      → 读取原始尺寸和 EXIF Orientation
      → 生成 displayPath（长边 1080px，修正方向，JPEG 85%）
      → 生成 thumbPath（长边 300px，修正方向，JPEG 80%）
      → 创建 NoteImage（sortOrder 按列表顺序 0-8，第一张 isCover = true）
      → 插入 note_images 表
→ runOnUiThread
  → Toast "保存成功"
  → finish()
```

### EXIF 方向处理

```java
private Bitmap decodeAndRotate(File file, int targetLongEdge) {
    // 1. 读取 EXIF Orientation
    ExifInterface exif = new ExifInterface(file.getAbsolutePath());
    int orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL);

    // 2. 只解码边界获取尺寸（不加载整张图到内存）
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

    // 3. 计算 inSampleSize，解码缩放后的 Bitmap
    opts.inSampleSize = calculateInSampleSize(opts, targetLongEdge);
    opts.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

    // 4. 按 EXIF 旋转 Bitmap
    return rotateBitmapByExif(bitmap, orientation);
}
```

### 相册选择

- 使用 `ActivityResultContracts.GetMultipleContents()` 支持多选
- 类型为 `"image/*"`
- 最多选择 9 张（减去已有图片数量）
- 相册选择不需要存储权限（通过 SAF 访问）

### 相机拍照

- 通过 FileProvider 创建临时文件 URI
- 调用 `MediaStore.ACTION_IMAGE_CAPTURE`
- 拍照完成后获取该 URI，加入暂存列表
- 需要 `CAMERA` 权限，运行时申请

## UI 设计

### 编辑页（NoteEditActivity）

#### 布局结构

```
┌─────────────────────────────┐
│ ← 新建笔记          Toolbar │
├─────────────────────────────┤
│                             │
│  EditText (文字输入)         │
│                             │
├─────────────────────────────┤
│  ┌─────┐ ┌─────┐ ┌─────┐   │  ← 图片列表区域（可滚动）
│  │ img │ │ img │ │ img │   │     单列铺开，默认显示
│  └─────┘ └─────┘ └─────┘   │
│  ┌─────┐                    │
│  │ img │                    │
│  └─────┘                    │
├─────────────────────────────┤
│         [ 保存 ]            │
├─────────────────────────────┤
│                      (+) FAB│  ← 图片添加 FAB
└─────────────────────────────┘
```

#### 图片列表区域

- 默认放在文字输入框下方、保存按钮上方
- 每张图片用缩略图展示，宽度 match_parent，高度自适应（保持比例）
- 图片之间有小间距
- 长按可拖拽排序（`ItemTouchHelper`）
- 每张图片右上角有半透明圆形删除按钮 "×"
- 图片数量显示在区域标题处，如 "图片 (3/9)"

#### FAB + BottomSheet

点击右下角 FAB 弹出 `BottomSheetDialog`：

```
┌─────────────────────────────┐
│         选择图片来源         │
├─────────────────────────────┤
│   📷  拍照                  │
│   🖼  从相册选择            │
├─────────────────────────────┤
│         [ 取消 ]            │
└─────────────────────────────┘
```

- 已有 9 张图片时 FAB 隐藏或禁用
- 相机选项需检查 CAMERA 权限
- 相册选项调用 `GetMultipleContents()`，限制选择数量

### 首页笔记列表（MainActivity）

#### 卡片布局变更

带图片的笔记卡片新增封面区域：

```
┌─────────────────────────────────┐
│  ┌───────────────────────────┐  │
│  │                           │  │
│  │     封面缩略图 thumbPath   │  │  ← 新增：封面图区域
│  │                           │  │
│  └───────────────────────────┘  │
│  这是一条笔记的文字内容...       │  ← 原有文字区域
│  ─────────────────────────────  │
│  🕐 3分钟前                     │
└─────────────────────────────────┘
```

#### 卡片类型判断

- 无图片的笔记：保持现有纯文字卡片样式不变
- 有图片的笔记：文字上方显示封面缩略图
- 封面图来源：`NoteImageDao.getCoverByNoteId()` 优先取封面，无则取第一张

#### 适配器改造

在 `loadNotes()` 中查出所有 Note，再批量查封面，组装后传给 Adapter：

```java
List<Note> notes = db.noteDao().getAll();
Map<Long, NoteImage> coverMap = new HashMap<>();
for (Note note : notes) {
    NoteImage cover = db.noteImageDao().getCoverByNoteId(note.getId());
    if (cover != null) coverMap.put(note.getId(), cover);
}
adapter.setData(notes, coverMap);
```

### 全屏图片查看器（ImageViewerActivity）

#### 功能

- 点击任意图片进入全屏查看
- 支持双指缩放（使用 PhotoView 库）
- 左右滑动切换图片（`ViewPager2`）
- 底部显示当前图片索引，随滑动动态更新，如 "2 / 5"
- 默认显示 `displayPath`（展示图）
- 底部 "查看原图" 按钮，点击后加载 `originalPath`

#### 布局

```
┌─────────────────────────────┐
│                             │
│                             │
│      ViewPager2 (图片)      │
│                             │
│                             │
├─────────────────────────────┤
│   2 / 5    [ 查看原图 ]     │
└─────────────────────────────┘
```

#### Intent 传递数据

```java
Intent intent = new Intent(context, ImageViewerActivity.class);
intent.putStringArrayListExtra("displayPaths", displayPaths);
intent.putStringArrayListExtra("originalPaths", originalPaths);
intent.putExtra("currentPosition", position);
```

#### 底部索引动态更新

```java
viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        tvIndex.setText((position + 1) + " / " + displayPaths.size());
    }
});
```

#### "查看原图" 按钮交互

- 默认显示 displayPath
- 点击 "查看原图" → 显示 loading → 加载 originalPath → 按钮变为 "已加载原图" 并置灰
- 原图可能较大，加载时显示 ProgressBar

#### 图片加载

统一使用 Glide：

```groovy
implementation 'com.github.bumptech.glide:glide:4.16.0'
annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'
```

| 场景 | 加载方式 |
|------|---------|
| 首页封面缩略图 | `Glide.with(iv).load(new File(thumbPath)).into(iv)` |
| 编辑页图片列表 | `Glide.with(iv).load(new File(thumbPath)).into(iv)` |
| 全屏查看（默认） | `Glide.with(iv).load(new File(displayPath)).into(iv)` |
| 查看原图 | `Glide.with(iv).load(new File(originalPath)).into(iv)` |

## 权限与 Manifest 配置

### AndroidManifest.xml 新增

```xml
<!-- 相机权限 -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />

<!-- FileProvider（相机拍照需要） -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

### 新增 `res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path name="images" path="images/" />
</paths>
```

### 权限申请流程

```
用户点击 "拍照"
       ↓
检查 CAMERA 权限
       ↓
未授权 → ActivityResultContracts.RequestPermission() 申请
       ↓
已授权 → 调用相机 Intent
```

- 相册选择不需要存储权限（`GetMultipleContents` 通过 SAF 访问）
- 相机拍照需要 `CAMERA` 权限，运行时申请

## 新增依赖

```groovy
// 图片加载
implementation 'com.github.bumptech.glide:glide:4.16.0'
annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'

// 图片查看（双指缩放）
implementation 'com.github.chrisbanes:PhotoView:2.3.0'
```

## 实现范围

本次实现：
- 新建 `NoteImage` 实体、`NoteImageDao`、数据库 Migration
- 新建 `ImageProcessor` 图片处理工具类
- 改造 `NoteEditActivity`：FAB、图片列表、相机/相册集成
- 改造 `MainActivity`：卡片封面显示、适配器扩展
- 新建 `ImageViewerActivity`：全屏查看、缩放、滑动、查看原图
- 新增 `FileProvider` 配置
- 新增 Glide、PhotoView 依赖

后续迭代：
- 编辑已有笔记（修改文字、增删图片）
- 图片分享功能
- 图片导出到相册
