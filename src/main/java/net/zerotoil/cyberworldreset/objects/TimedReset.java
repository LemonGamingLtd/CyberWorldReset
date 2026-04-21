package net.zerotoil.cyberworldreset.objects;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import me.nahu.scheduler.wrapper.runnable.WrappedRunnable;
import net.zerotoil.cyberworldreset.CyberWorldReset;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

// used for resetting worlds at the correct timing
public class TimedReset {

    private static final CronDefinition CRON_DEFINITION = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
    private static final CronParser CRON_PARSER = new CronParser(CRON_DEFINITION);

    private final CyberWorldReset main;
    private final String world;
    private final String cronExpression;
    private final ExecutionTime executionTime;
    private final ZoneId zoneId = ZoneId.systemDefault();

    private Timer timer = new Timer();
    private final ArrayList<Timer> warningTimers = new ArrayList<>();
    private final List<Long> warningSeconds = new ArrayList<>();

    private long resetTime;

    public TimedReset(CyberWorldReset main, String world, String cronExpression, ArrayList<Long> warningSeconds) {
        this.main = main;
        this.world = world;
        this.cronExpression = cronExpression.trim();
        this.executionTime = ExecutionTime.forCron(parseCron(this.cronExpression));
        if (warningSeconds != null) this.warningSeconds.addAll(warningSeconds);
        scheduleNextRun(false);
    }

    public static boolean isValidCronExpression(String cronExpression) {
        try {
            parseCron(cronExpression);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Cron parseCron(String cronExpression) {
        Cron cron = CRON_PARSER.parse(cronExpression.trim());
        cron.validate();
        return cron;
    }

    public void scheduleNextRun(boolean cancelExistingTimer) {
        if (cancelExistingTimer) cancelTimer();
        cancelWarningTimers();
        timer = new Timer();

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(now);
        if (nextExecution.isEmpty()) {
            resetTime = 0;
            main.getLogger().warning("No future execution found for world \"" + world + "\" using cron \"" + cronExpression + "\".");
            return;
        }

        long delayMillis = Duration.between(now, nextExecution.get()).toMillis();
        if (delayMillis <= 0) {
            resetTime = 0;
            return;
        }

        resetTime = System.currentTimeMillis() + delayMillis;
        scheduleWarningTimers(delayMillis);
        timer.schedule(new MyTimeTask(), delayMillis);
        main.getLogger().info("Scheduled world \"" + world + "\" with cron \"" + cronExpression + "\" for " + nextExecution.get() + ".");
    }

    private void scheduleWarningTimers(long delayMillis) {
        if (warningSeconds.isEmpty()) return;
        if (!main.worlds().getWorld(world).isWarningEnabled()) return;

        long secondsUntilReset = Math.max(0L, delayMillis / 1000L);
        for (long warningSecond : warningSeconds) {
            if (secondsUntilReset < warningSecond) continue;

            long runInSeconds = Math.max(0L, secondsUntilReset - warningSecond);
            Timer warningTimer = new Timer();
            warningTimers.add(warningTimer);
            warningTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        main.worlds().getWorld(world).sendWarning(cronExpression);
                    } catch (Exception ignored) {
                    } finally {
                        warningTimer.cancel();
                        warningTimer.purge();
                    }
                }
            }, runInSeconds * 1000L);
        }
    }

    private class MyTimeTask extends TimerTask {

        @Override
        public void run() {
            main.getLogger().info("Running scheduled reset for world \"" + world + "\" from cron \"" + cronExpression + "\".");
            main.getScheduler().runTask(() -> main.worlds().getWorld(world).regenWorld(null));

            (new WrappedRunnable() {
                @Override
                public void run() {
                    scheduleNextRun(true);
                }
            }).runTaskLater(main, 20L * 10);
        }

    }

    public void cancelAllTimers() {
        cancelTimer();
        cancelWarningTimers();
    }

    private void cancelWarningTimers() {
        if (warningTimers.isEmpty()) return;
        for (Timer warningTimer : warningTimers) {
            warningTimer.cancel();
            warningTimer.purge();
        }
        warningTimers.clear();
    }

    private void cancelTimer() {
        timer.cancel();
        timer.purge();
    }

    public long timeToReset() {
        if (resetTime == 0) return 0;
        return Math.max(0L, (resetTime - System.currentTimeMillis()) / 1000L);
    }

}
