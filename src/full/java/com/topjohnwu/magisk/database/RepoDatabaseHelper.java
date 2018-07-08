package com.topjohnwu.magisk.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.topjohnwu.magisk.MagiskManager;
import com.topjohnwu.magisk.container.Repo;
import com.topjohnwu.magisk.utils.Const;
import com.topjohnwu.magisk.utils.Utils;

import java.util.HashSet;
import java.util.Set;

public class RepoDatabaseHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VER = 3;
    private static final String TABLE_NAME = "repos";

    private SQLiteDatabase mDb;
    private MagiskManager mm;

    public RepoDatabaseHelper(Context context) {
        super(context, "repo.db", null, DATABASE_VER);
        mm = Utils.getMagiskManager(context);
        mDb = getWritableDatabase();

        // Remove outdated repos
        mDb.delete(TABLE_NAME, "minMagisk<?",
                new String[] { String.valueOf(Const.MIN_MODULE_VER()) });
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        onUpgrade(db, 0, DATABASE_VER);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            if (oldVersion < 3) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                db.execSQL(
                        "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " " +
                                "(id TEXT, name TEXT, version TEXT, versionCode INT, minMagisk INT, " +
                                "author TEXT, description TEXT, repo_name TEXT, last_update INT, " +
                                "PRIMARY KEY(id))");
                mm.prefs.edit().remove(Const.Key.ETAG_KEY).apply();
                oldVersion = 3;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Reset database
            onDowngrade(db, DATABASE_VER, 0);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, 0, DATABASE_VER);
    }

    public void clearRepo() {
        mDb.delete(TABLE_NAME, null, null);
    }


    public void removeRepo(String id) {
        mDb.delete(TABLE_NAME, "id=?", new String[] { id });
    }

    public void removeRepo(Repo repo) {
        mDb.delete(TABLE_NAME, "repo_name=?", new String[] { repo.getRepoName() });
    }

    public void removeRepo(Iterable<String> list) {
        for (String id : list) {
            if (id == null) continue;
            mDb.delete(TABLE_NAME, "id=?", new String[] { id });
        }
    }

    public void addRepo(Repo repo) {
        mDb.replace(TABLE_NAME, null, repo.getContentValues());
    }

    public Repo getRepo(String id) {
        try (Cursor c = mDb.query(TABLE_NAME, null, "id=?", new String[] { id }, null, null, null)) {
            if (c.moveToNext()) {
                return new Repo(c);
            }
        }
        return null;
    }

    public Cursor getRawCursor() {
        return mDb.query(TABLE_NAME, null, null, null, null, null, null);
    }

    public Cursor getRepoCursor() {
        String orderBy = null;
        switch (mm.repoOrder) {
            case Const.Value.ORDER_NAME:
                orderBy = "name COLLATE NOCASE";
                break;
            case Const.Value.ORDER_DATE:
                orderBy = "last_update DESC";
        }
        return mDb.query(TABLE_NAME, null, "minMagisk<=? AND minMagisk>=?",
                new String[] { String.valueOf(mm.magiskVersionCode), String.valueOf(Const.MIN_MODULE_VER()) },
                null, null, orderBy);
    }

    public Set<String> getRepoIDSet() {
        HashSet<String> set = new HashSet<>(300);
        try (Cursor c = mDb.query(TABLE_NAME, null, null, null, null, null, null)) {
            while (c.moveToNext()) {
                set.add(c.getString(c.getColumnIndex("id")));
            }
        }
        return set;
    }
}
