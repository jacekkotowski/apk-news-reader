package pl.stashreader;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Periodic refresh while the app is closed. Android decides the exact times; the job needs a network. */
public class RefreshJob extends JobService {
    private static final int JOB = 1;
    private static final long MIN_MS = 15 * 60 * 1000L;

    /** Every {@code hours} hours; 0 or less switches the background refresh off. */
    static void schedule(Context c, int hours) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (hours <= 0) { js.cancel(JOB); return; }
        long every = Math.max(MIN_MS, hours * 3600000L);
        JobInfo old = js.getPendingJob(JOB);
        if (old != null && old.isPeriodic() && old.getIntervalMillis() == every) return; // keep the existing schedule
        js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, RefreshJob.class))
            .setPeriodic(every)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true).build());
    }

    @Override public boolean onStartJob(final JobParameters p) {
        new Thread(new Runnable() {
            @Override public void run() {
                try { Refresher.all(RefreshJob.this); } finally { jobFinished(p, false); }
            }
        }, "refresh-job").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters p) { return true; }
}
