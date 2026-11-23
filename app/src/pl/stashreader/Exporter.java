package pl.stashreader;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

/** Writes kept items as Markdown notes into the folder the user picked once (Storage Access Framework). */
final class Exporter {
    private Exporter() {}
    static final String PREF = "exportUri";
    /** Set when the reader pressed "Stop exporting". */
    static final String OFF = "exportOff";
    static final String AUTO_DIR = "Documents/Stash Reader/";
    private static final String MIME = "text/markdown";

    /** Android 10 and newer can write to Documents/Stash Reader without asking; no folder needs to be picked. */
    static boolean autoAvailable() { return Build.VERSION.SDK_INT >= 29; }

    /** True when the notes go to the automatic folder: nothing picked, not switched off, Android 10+. */
    static boolean auto(Context c) {
        return autoAvailable() && folder(c) == null && !prefs(c).getBoolean(OFF, false);
    }

    /** Notes are written somewhere: a picked folder or the automatic one. */
    static boolean enabled(Context c) { return folder(c) != null || auto(c); }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("stash", Context.MODE_PRIVATE); }

    static Uri folder(Context c) {
        String s = prefs(c).getString(PREF, "");
        return s.isEmpty() ? null : Uri.parse(s);
    }

    /** A short name for the chosen folder, or "" when none is set. */
    static String folderName(Context c) {
        Uri u = folder(c);
        if (u == null) return auto(c) ? "Documents/Stash Reader (automatic)" : (prefs(c).getBoolean(OFF, false) ? "off" : "");
        try {
            String id = DocumentsContract.getTreeDocumentId(u);
            int colon = id.indexOf(':');
            String shown = colon >= 0 ? id.substring(colon + 1) : id;
            return shown.isEmpty() ? "(storage root)" : shown;
        } catch (RuntimeException e) { return u.toString(); }
    }

    /** Text for sharing; null when the item is not kept. */
    static String markdown(Db db, long id) {
        MarkdownExport.Note n = db.noteFor(id);
        return n == null ? null : MarkdownExport.note(n, TimeZone.getDefault());
    }

    /** Writes or rewrites the note's file. Returns "" on success, "" when no folder is set, else a message. */
    static String write(Context c, Db db, long id) {
        MarkdownExport.Note n = db.noteFor(id);
        if (n == null || !enabled(c)) return "";
        return writeText(c, MarkdownExport.fileName(n, TimeZone.getDefault()), MarkdownExport.note(n, TimeZone.getDefault()));
    }

    /** Writes or rewrites one Markdown file in the chosen folder. "" on success or when no folder is set, else a message. */
    static String writeText(Context c, String name, String text) {
        Uri tree = folder(c);
        if (tree == null) return auto(c) ? writeAuto(c, name, text) : "";
        try {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            Uri existing = find(c, tree, name);
            Uri target = existing != null ? existing : DocumentsContract.createDocument(c.getContentResolver(), parent, "text/markdown", name);
            if (target == null) return "The folder did not accept the file " + name;
            try (OutputStream out = c.getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) return "Could not open " + name + " for writing";
                out.write(bytes);
            }
            return "";
        } catch (Exception e) {
            return "Export failed: " + e.getMessage();
        }
    }

    /** Writes the file into Documents/Stash Reader through MediaStore: finds the file this app made earlier and rewrites it, else creates it. */
    private static String writeAuto(Context c, String name, String text) {
        try {
            android.content.ContentResolver r = c.getContentResolver();
            Uri files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri target = null;
            try (Cursor q = r.query(files, new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?", new String[]{name, AUTO_DIR}, null)) {
                if (q != null && q.moveToFirst()) target = ContentUris.withAppendedId(files, q.getLong(0));
            }
            if (target == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, MIME);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, AUTO_DIR);
                target = r.insert(files, v);
            }
            if (target == null) return "Could not create " + name + " in Documents/Stash Reader";
            try (OutputStream out = r.openOutputStream(target, "wt")) {
                if (out == null) return "Could not open " + name + " for writing";
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return "";
        } catch (Exception e) {
            return "Export failed: " + e.getMessage();
        }
    }

    private static Uri find(Context c, Uri tree, String name) {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME};
        try (Cursor q = c.getContentResolver().query(children, cols, null, null, null)) {
            while (q != null && q.moveToNext()) {
                if (name.equals(q.getString(1))) return DocumentsContract.buildDocumentUriUsingTree(tree, q.getString(0));
            }
        }
        return null;
    }

    /** Rewrites every kept item. Returns a sentence for the screen. */
    static String writeAll(Context c, Db db) {
        if (!enabled(c)) return "Exporting is off. Open Options and choose a folder, or use the automatic one.";
        int ok = 0; String firstError = "";
        for (long id : db.keptIds()) {
            String e = write(c, db, id);
            if (e.isEmpty()) ok++; else if (firstError.isEmpty()) firstError = e;
        }
        return firstError.isEmpty() ? "Wrote " + ok + " note" + (ok == 1 ? "" : "s") + " to " + folderName(c) : firstError + " (" + ok + " written)";
    }
}
