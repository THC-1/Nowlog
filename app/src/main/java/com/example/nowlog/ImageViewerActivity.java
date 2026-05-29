package com.example.nowlog;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {
    private ViewPager2 viewPager;
    private TextView tvIndex;
    private MaterialButton btnViewOriginal;

    private ArrayList<String> displayPaths;
    private ArrayList<String> originalPaths;
    private int currentPosition;
    private boolean[] originalLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_image_viewer);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
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

        ImagePagerAdapter adapter = new ImagePagerAdapter(displayPaths);
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
        if (originalPaths != null && currentPosition < originalPaths.size()) {
            if (originalLoaded[currentPosition]) {
                btnViewOriginal.setText("已加载原图");
                btnViewOriginal.setEnabled(false);
            } else {
                btnViewOriginal.setText("查看原图");
                btnViewOriginal.setEnabled(true);
            }
        }
    }

    private void loadOriginal() {
        if (originalPaths == null || currentPosition >= originalPaths.size()) return;

        String originalPath = originalPaths.get(currentPosition);
        originalLoaded[currentPosition] = true;

        // Find current PhotoView in ViewPager2
        RecyclerView rv = (RecyclerView) viewPager.getChildAt(0);
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(currentPosition);
        if (vh instanceof ImageViewHolder) {
            ImageView photoView = ((ImageViewHolder) vh).imageView;
            Glide.with(this)
                .load(new File(originalPath))
                .into(photoView);
        }

        updateUI();
    }

    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImageViewHolder> {
        private final List<String> paths;

        ImagePagerAdapter(List<String> paths) {
            this.paths = paths;
        }

        @Override
        public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(ImageViewHolder holder, int position) {
            Glide.with(holder.imageView)
                    .load(new File(paths.get(position)))
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return paths.size();
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageView;

        ImageViewHolder(ImageView itemView) {
            super(itemView);
            imageView = itemView;
        }
    }
}
