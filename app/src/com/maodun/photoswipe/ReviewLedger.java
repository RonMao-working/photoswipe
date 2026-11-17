package com.maodun.photoswipe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.InputStream;
import java.security.MessageDigest;

/** Version-independent, local-only review ledger. No network or public-storage writes. */
final class ReviewLedger extends SQLiteOpenHelper {
    ReviewLedger(Context context) { super(context, "review_ledger.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reviewed (identity TEXT PRIMARY KEY, signature TEXT, digest TEXT, deleted INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX reviewed_digest ON reviewed(digest)");
        db.execSQL("CREATE INDEX reviewed_signature ON reviewed(signature)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    synchronized boolean contains(String identity, String signature) {
        try (Cursor c = getReadableDatabase().query("reviewed", new String[]{"identity"},
                "identity=? OR signature=?", new String[]{identity, signature}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }
    synchronized boolean containsDigest(String digest) {
        if (digest == null || digest.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().query("reviewed", new String[]{"identity"},
                "digest=?", new String[]{digest}, null, null, null, "1")) { return c.moveToFirst(); }
    }
    synchronized void record(String identity, String signature, String digest, boolean deleted) {
        try (Cursor c = getReadableDatabase().query("reviewed", new String[]{"digest", "deleted"},
                "identity=?", new String[]{identity}, null, null, null)) {
            if (c.moveToFirst()) {
                if (digest == null) digest = c.getString(0);
                deleted = deleted || c.getInt(1) == 1;
            }
        }
        ContentValues values = new ContentValues();
        values.put("identity", identity); values.put("signature", signature);
        values.put("digest", digest); values.put("deleted", deleted ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("reviewed", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized void markDeleted(String identity) {
        ContentValues values = new ContentValues(); values.put("deleted", 1);
        getWritableDatabase().update("reviewed", values, "identity=?", new String[]{identity});
    }
    synchronized void undo(String identity) {
        getWritableDatabase().delete("reviewed", "identity=? AND deleted=0", new String[]{identity});
    }
    synchronized void newRound() { getWritableDatabase().delete("reviewed", "deleted=0", null); }
    synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM reviewed", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    static String sha256(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536]; int n;
        while ((n = input.read(buffer)) != -1) { if (Thread.currentThread().isInterrupted()) throw new InterruptedException(); digest.update(buffer, 0, n); }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) result.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return result.toString();
    }
}
