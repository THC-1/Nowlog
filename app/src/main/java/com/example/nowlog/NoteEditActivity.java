package com.example.nowlog;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

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
