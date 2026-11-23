package pl.stashreader;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads kept articles aloud with the phone's own speech engine. A foreground service with a media notification (pause, next),
 * so it keeps going with the screen off. It remembers the sentence where each item stopped, and goes on to the next item of the play queue.
 */
public class Speaker extends Service {
    static final String PLAY = "pl.stashreader.PLAY", TOGGLE = "pl.stashreader.TOGGLE", NEXT = "pl.stashreader.NEXT", STOP = "pl.stashreader.STOP", RATE = "pl.stashreader.RATE";
    private static final String CHANNEL = "listen";
    private static final int NOTE_ID = 61, AHEAD = 4;

    // read by the app's bridge from another thread
    private static volatile boolean active, playing;
    private static volatile long currentId = -1;
    private static volatile String currentTitle = "";
    private static volatile int currentIndex, currentTotal;

    static boolean isActive() { return active; }

    static String state() {
        return "{\"active\":" + active + ",\"playing\":" + playing + ",\"id\":\"" + currentId + "\",\"title\":\"" + Gemini.esc(currentTitle) + "\",\"index\":" + currentIndex + ",\"total\":" + currentTotal + "}";
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ttsReady;
    private Runnable whenReady;
    private Db db;
    private List<String> sentences = new ArrayList<>();
    private int next;          // the next sentence to hand to the engine
    private int spoken;        // the sentence being spoken (or last started)
    private int gen;           // changes whenever the speech queue is flushed, so late callbacks of the old queue are ignored
    private boolean wantPlay;  // the user wants sound (false while paused)
    private boolean lostFocus;
    private String lang = "";
    private AudioManager audio;
    private AudioFocusRequest focus;
    private PowerManager.WakeLock wake;
    private MediaSession session;
    private boolean noisyRegistered;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause(); }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        db = Db.get(this);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Listening", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "StashReader");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onSkipToNext() { skip(); }
            @Override public void onStop() { stopAll(); }
        });
        session.setActive(true);
        tts = new TextToSpeech(this, status -> main.post(() -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { main.post(() -> started(id)); }
                    @Override public void onDone(String id) { main.post(() -> finished(id)); }
                    @Override public void onError(String id) { main.post(() -> finished(id)); }
                });
            }
            Runnable r = whenReady; whenReady = null;
            if (r != null) r.run();
        }));
    }

    @Override public int onStartCommand(Intent in, int flags, int startId) {
        startForegroundNow(currentTitle.isEmpty() ? "Stash Reader" : currentTitle, "");
        String a = in == null ? "" : String.valueOf(in.getAction());
        if (PLAY.equals(a)) {
            final long id = in.getLongExtra("id", -1);
            runWhenReady(() -> startItem(id, true));
        } else if (TOGGLE.equals(a)) { if (playing) pause(); else resume(); }
        else if (NEXT.equals(a)) skip();
        else if (STOP.equals(a)) stopAll();
        else if (RATE.equals(a)) { if (wantPlay && ttsReady) restartFromCurrent(); }
        return START_NOT_STICKY;
    }

    private void runWhenReady(Runnable r) {
        if (ttsReady) r.run();
        else if (tts != null) whenReady = r;
    }

    // ---- items ----

    private void startItem(long id, boolean fresh) {
        String[] in = db.listenInput(id);
        if (in == null) { skipFrom(id); return; }
        boolean full = in[3].equals("1");
        sentences = Listen.sentences(in[0], full, in[2]);
        lang = Listen.language(in[2]);
        currentId = id; currentTitle = in[0]; currentTotal = sentences.size();
        int at = Listen.start(db.listenPos(id), sentences.size(), db.listenDone(id));
        if (fresh && at >= sentences.size()) at = 0;
        db.setListenPos(id, at, false);
        spoken = at; next = at; currentIndex = at;
        active = true;
        if (!applyLanguage()) { toast("This phone's speech engine has no voice for this language. Install one in Settings, Text-to-speech."); }
        wantPlay = true;
        if (!requestFocus()) { wantPlay = false; playing = false; refresh(); return; }
        speakAhead(true);
    }

    private boolean applyLanguage() {
        Locale want = lang.equals("pl") ? new Locale("pl", "PL") : Locale.getDefault();
        int r = tts.setLanguage(want);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (lang.isEmpty()) { tts.setLanguage(Locale.US); }
            return false;
        }
        return true;
    }

    private void toast(final String m) { main.post(() -> android.widget.Toast.makeText(Speaker.this, m, android.widget.Toast.LENGTH_LONG).show()); }

    /** Hands the engine a few sentences at a time, so a pause or a skip takes effect quickly. */
    private void speakAhead(boolean flush) {
        if (!ttsReady || !wantPlay) return;
        tts.setSpeechRate(Listen.rate(getSharedPreferences("stash", MODE_PRIVATE).getInt("ttsRate", 100)) / 100f);
        if (flush) { gen++; tts.stop(); }
        int sent = 0;
        // count what is already queued: sentences from `spoken` to `next`
        int queued = Math.max(0, next - spoken);
        while (next < sentences.size() && queued + sent < AHEAD) {
            tts.speak(sentences.get(next), TextToSpeech.QUEUE_ADD, null, gen + ":" + next);
            next++; sent++;
        }
        playing = true;
        acquireWake();
        refresh();
    }

    private void started(String utterance) {
        int i = indexOf(utterance);
        if (i < 0) return;
        spoken = i; currentIndex = i;
        db.setListenPos(currentId, i, false);
        refresh();
    }

    private void finished(String utterance) {
        int i = indexOf(utterance);
        if (i < 0 || !wantPlay) return;
        if (i >= sentences.size() - 1) { // the end of the item
            db.setListenPos(currentId, 0, true);
            skipFrom(currentId);
            return;
        }
        spoken = i + 1;
        speakAhead(false);
    }

    private int indexOf(String utterance) {
        try {
            int c = utterance == null ? -1 : utterance.indexOf(':');
            if (c < 0 || Integer.parseInt(utterance.substring(0, c)) != gen) return -1;
            return Integer.parseInt(utterance.substring(c + 1));
        } catch (NumberFormatException e) { return -1; }
    }

    private void restartFromCurrent() {
        next = spoken;
        speakAhead(true);
    }

    // ---- controls ----

    private void pause() {
        if (!playing && !wantPlay) return;
        wantPlay = false; playing = false;
        if (ttsReady) tts.stop();
        releaseWake();
        refresh();
    }

    private void resume() {
        if (!active || wantPlay) return;
        wantPlay = true;
        if (!requestFocus()) { wantPlay = false; return; }
        next = spoken;
        speakAhead(true);
    }

    private void skip() { if (active) skipFrom(currentId); }

    /** Goes on with the next item of the play queue, or ends when there is none. */
    private void skipFrom(long id) {
        long n = db.listenNext(id);
        if (ttsReady) tts.stop();
        if (n < 0) { stopAll(); return; }
        startItem(n, true);
    }

    private void stopAll() {
        wantPlay = false; playing = false; active = false; currentId = -1; currentTitle = "";
        if (ttsReady) tts.stop();
        releaseWake(); abandonFocus();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        wantPlay = false; playing = false; active = false;
        releaseWake(); abandonFocus();
        if (noisyRegistered) { try { unregisterReceiver(noisy); } catch (RuntimeException ignored) { } noisyRegistered = false; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (session != null) { session.setActive(false); session.release(); }
        super.onDestroy();
    }

    // ---- audio focus, wake lock ----

    private boolean requestFocus() {
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> main.post(() -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) { if (wantPlay) { lostFocus = true; pause(); } }
                    else if (change == AudioManager.AUDIOFOCUS_GAIN && lostFocus) { lostFocus = false; resume(); }
                })).build();
        }
        lostFocus = false;
        if (!noisyRegistered) { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); noisyRegistered = true; }
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() { if (focus != null && audio != null) audio.abandonAudioFocusRequest(focus); }

    private void acquireWake() {
        if (wake == null) wake = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stashreader:listen");
        if (!wake.isHeld()) wake.acquire(4L * 3600 * 1000);
    }
    private void releaseWake() { if (wake != null && wake.isHeld()) wake.release(); }

    // ---- notification ----

    private PendingIntent action(String a, int code) {
        Intent i = new Intent(this, Speaker.class).setAction(a);
        return PendingIntent.getService(this, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification build(String title, String text) {
        int small = getResources().getIdentifier("ic", "drawable", getPackageName());
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
            .setContentTitle(title).setContentText(text).setOngoing(playing).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
            .setDeleteIntent(action(STOP, 3));
        if (small != 0) b.setSmallIcon(small); else b.setSmallIcon(android.R.drawable.ic_media_play);
        b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), playing ? "Pause" : "Play", action(TOGGLE, 1)).build());
        b.addAction(new Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Next", action(NEXT, 2)).build());
        b.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1));
        return b.build();
    }

    private void startForegroundNow(String title, String text) {
        Notification n = build(title, text);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTE_ID, n);
    }

    private void refresh() {
        if (!active) return;
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_STOP)
            .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTE_ID, build(currentTitle.isEmpty() ? "Stash Reader" : currentTitle, "Sentence " + (currentIndex + 1) + " of " + currentTotal));
    }
}
