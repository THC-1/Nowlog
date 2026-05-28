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
