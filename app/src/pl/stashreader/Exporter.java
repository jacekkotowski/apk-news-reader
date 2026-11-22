package pl.stashreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.TimeZone;

/** Writes kept items as Markdown notes into the folder the user picked once (Storage Access Framework). */
final class Exporter {
    private Exporter() {}
    static final String PREF = "exportUri";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("stash", Context.MODE_PRIVATE); }

    static Uri folder(Context c) {
        String s = prefs(c).getString(PREF, "");
        return s.isEmpty() ? null : Uri.parse(s);
    }

    /** A short name for the chosen folder, or "" when none is set. */
    static String folderName(Context c) {
        Uri u = folder(c);
        if (u == null) return "";
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
        if (n == null || folder(c) == null) return "";
        return writeText(c, MarkdownExport.fileName(n, TimeZone.getDefault()), MarkdownExport.note(n, TimeZone.getDefault()));
    }

    /** Writes or rewrites one Markdown file in the chosen folder. "" on success or when no folder is set, else a message. */
    static String writeText(Context c, String name, String text) {
        Uri tree = folder(c);
        if (tree == null) return "";
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
        if (folder(c) == null) return "Choose an export folder first.";
        int ok = 0; String firstError = "";
        for (long id : db.keptIds()) {
            String e = write(c, db, id);
            if (e.isEmpty()) ok++; else if (firstError.isEmpty()) firstError = e;
        }
        return firstError.isEmpty() ? "Wrote " + ok + " note" + (ok == 1 ? "" : "s") + " to " + folderName(c) : firstError + " (" + ok + " written)";
    }
}
