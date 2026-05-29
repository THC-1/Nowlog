package com.example.nowlog.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert
    long insert(Note note);

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    List<Note> getAll();

    @Delete
    void delete(Note note);
}
