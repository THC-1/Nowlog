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
