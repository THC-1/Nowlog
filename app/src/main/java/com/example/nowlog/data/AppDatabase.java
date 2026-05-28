package com.example.nowlog.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Note.class, NoteImage.class}, version = 2)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract NoteImageDao noteImageDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "nowlog.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

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
                    + "createdAt INTEGER NOT NULL,"
                    + "FOREIGN KEY(noteId) REFERENCES notes(id) ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX index_note_images_noteId ON note_images(noteId)");
        }
    };
}
