package science.prokulski.apknewsreader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Store(context: Context) : SQLiteOpenHelper(context, "reader.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE sources(url TEXT PRIMARY KEY, title TEXT NOT NULL DEFAULT '', kind TEXT NOT NULL DEFAULT 'auto')")
        db.execSQL("CREATE TABLE articles(id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL, link TEXT NOT NULL, summary TEXT NOT NULL DEFAULT '', published TEXT NOT NULL DEFAULT '', media TEXT NOT NULL DEFAULT '', media_type TEXT NOT NULL DEFAULT '', saved INTEGER NOT NULL DEFAULT 0, read INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX articles_source ON articles(source)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun addSource(url: String, kind: String = "auto") {
        writableDatabase.execSQL("INSERT OR IGNORE INTO sources(url,kind) VALUES (?,?)", arrayOf(url, kind))
    }
    fun sources(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT url FROM sources ORDER BY url", null).use { c ->
            while (c.moveToNext()) result.add(c.getString(0))
        }
        return result
    }
    fun save(items: List<NewsItem>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val values = android.content.ContentValues().apply {
                    put("id", item.id); put("source", item.source); put("title", item.title)
                    put("link", item.link); put("summary", item.summary); put("published", item.date)
                    put("media", item.media); put("media_type", item.mediaType)
                }
                db.insertWithOnConflict("articles", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun articles(): List<NewsItem> {
        val result = mutableListOf<NewsItem>()
        readableDatabase.rawQuery("SELECT id,title,link,summary,source,published,media,media_type FROM articles ORDER BY rowid DESC LIMIT 1000", null).use { c ->
            while (c.moveToNext()) result.add(NewsItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7)))
        }
        return result
    }
    fun bookmark(id: String) {
        writableDatabase.execSQL("UPDATE articles SET saved = 1 - saved WHERE id = ?", arrayOf(id))
    }
    fun markRead(id: String) {
        writableDatabase.execSQL("UPDATE articles SET read = 1 WHERE id = ?", arrayOf(id))
    }
}
