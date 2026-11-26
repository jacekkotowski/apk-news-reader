package pl.stashreader;

import java.util.ArrayList;
import java.util.List;

/**
 * The one swipe on a Feed card that can still be undone. The items change state at once; what cannot be taken back
 * (writing the note file, erasing the text, counting a surprise turned down) waits until the swipe is final.
 * No Android imports.
 */
final class Swipes {
    interface Store {
        /** Marks the unread items among these as kept (keep) or less; returns the ones it changed. */
        List<Long> mark(List<Long> ids, boolean keep);
        /** Makes the item unread again. */
        void restore(long id);
        /** Writes the note file of a kept item. */
        void export(long id);
        /** Erases the stored text of an item marked less. */
        void dropText(long id);
        /** Makes surprises from the item's source rarer. */
        void surpriseLess(long id);
    }

    private final Store store;
    private List<Long> pending; // null when nothing can be undone
    private boolean kept;
    private long surprise;

    Swipes(Store store) { this.store = store; }

    /** The item ids in a comma separated list; anything that is not a positive number is left out. */
    static List<Long> parse(String list) {
        List<Long> out = new ArrayList<>();
        if (list == null) return out;
        for (String s : list.split(",")) {
            try {
                long id = Long.parseLong(s.trim());
                if (id > 0 && !out.contains(id)) out.add(id);
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    /** A new swipe: the one before it becomes final. surpriseId is the swiped surprise card, or 0. */
    synchronized void swipe(List<Long> ids, boolean keep, long surpriseId) {
        commit();
        List<Long> changed = store.mark(ids, keep);
        if (changed.isEmpty()) return;
        pending = changed;
        kept = keep;
        surprise = !keep && changed.contains(surpriseId) ? surpriseId : 0;
    }

    /** Takes the last swipe back, if it is not final yet. */
    synchronized void undo() {
        if (pending == null) return;
        for (long id : pending) store.restore(id);
        pending = null;
    }

    /** Makes the last swipe final. Does nothing when there is none. */
    synchronized void commit() {
        if (pending == null) return;
        List<Long> ids = pending;
        pending = null;
        for (long id : ids) {
            if (kept) store.export(id);
            else store.dropText(id);
        }
        if (surprise > 0) store.surpriseLess(surprise);
    }
}
