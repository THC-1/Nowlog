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

import java.util.ArrayList;
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
        // 点击笔记：如果有图片，打开图片查看器
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
