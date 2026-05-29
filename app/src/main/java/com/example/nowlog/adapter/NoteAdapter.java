package com.example.nowlog.adapter;

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
