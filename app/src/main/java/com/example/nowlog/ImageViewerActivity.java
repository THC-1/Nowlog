package com.example.nowlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {

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

        ArrayList<String> displayPaths = getIntent().getStringArrayListExtra("displayPaths");
        ArrayList<String> originalPaths = getIntent().getStringArrayListExtra("originalPaths");
        int currentPosition = getIntent().getIntExtra("currentPosition", 0);

        if (displayPaths == null || displayPaths.isEmpty()) {
            finish();
            return;
        }

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new ImagePagerAdapter(displayPaths));
        viewPager.setCurrentItem(currentPosition, false);
    }

    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {
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

        static class ImageViewHolder extends RecyclerView.ViewHolder {
            final ImageView imageView;

            ImageViewHolder(ImageView itemView) {
                super(itemView);
                imageView = itemView;
            }
        }
    }
}
