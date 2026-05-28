package com.example.nowlog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.nowlog.data.AppDatabase;
import com.example.nowlog.data.Note;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteEditActivity extends AppCompatActivity {
    private EditText etContent;
    private AppDatabase db;
    private ExecutorService executor;
    private Handler mainHandler;

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

        etContent = findViewById(R.id.etContent);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        findViewById(R.id.btnSave).setOnClickListener(v -> saveNote());

        toolbar.setNavigationOnClickListener(v -> finish());

        etContent.requestFocus();
    }

    private void saveNote() {
        String content = etContent.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(content, System.currentTimeMillis());
        executor.execute(() -> {
            db.noteDao().insert(note);
            mainHandler.post(this::finish);
        });
    }
}