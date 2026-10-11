package app.viewmodels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import app.models.BirMail;
import app.viewmodels.BirMailViewModel.SyncResult;
import app.viewmodels.BirMailViewModel.SyncState;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.concurrent.Task;

/**
 * Runs BIR mail downloads independently of any page, so a download keeps going
 * (and is saved to the cache) after the user navigates away. Opening the
 * confirmation page again while a download is running joins it instead of
 * starting a second one.
 *
 * Threading: every method here must be called on the JavaFX thread. The only
 * work that happens on another thread is the mail download itself.
 */
public final class BirMailSyncManager {

    private static final Map<Integer, Job> RUNNING = new HashMap<>();   // one job per taxpayer

    private BirMailSyncManager() {}

    /** How a finished download turned out. error is null on success. */
    public record Outcome(List<BirMail> all, int newCount, boolean reset, Throwable error) {}

    public static final class Job {
        private final int taxId;
        private final Task<SyncResult> task;
        private final List<Consumer<Outcome>> listeners = new ArrayList<>();
        private Outcome outcome;   // null while running

        private Job(int taxId, Task<SyncResult> task) {
            this.taxId = taxId;
            this.task = task;
        }

        public ReadOnlyDoubleProperty progressProperty() { return task.progressProperty(); }
        public ReadOnlyStringProperty messageProperty() { return task.messageProperty(); }
        public ReadOnlyBooleanProperty runningProperty() { return task.runningProperty(); }
        public boolean isRunning() { return outcome == null; }

        /** Runs when the download finishes (right away if it already has). Called on the JavaFX thread. */
        public void whenDone(Consumer<Outcome> listener) {
            if (outcome != null) listener.accept(outcome);
            else listeners.add(listener);
        }

        private void finish(Outcome result) {
            if (RUNNING.get(taxId) == this) RUNNING.remove(taxId);
            outcome = result;
            List<Consumer<Outcome>> toCall = new ArrayList<>(listeners);
            listeners.clear();
            for (Consumer<Outcome> l : toCall) {
                try {
                    l.accept(result);
                } catch (RuntimeException ex) {
                    ex.printStackTrace();   // one broken listener must not stop the others
                }
            }
        }
    }

    public static boolean isRunning(int taxId) {
        Job job = RUNNING.get(taxId);
        return job != null && job.isRunning();
    }

    /** Starts a download for this taxpayer, or returns the one already in progress. */
    public static Job startOrJoin(BirMailViewModel vm) throws Exception {
        final int taxId = vm.getTaxId();
        Job running = RUNNING.get(taxId);
        if (running != null && running.isRunning()) return running;

        final SyncState state = vm.readSyncState();   // database read, on the JavaFX thread

        Task<SyncResult> task = new Task<>() {
            @Override protected SyncResult call() throws Exception {
                return vm.fetchNew(state, (done, total) -> {
                    updateProgress(done, total);
                    updateMessage("Downloading " + done + " of " + total + "...");
                });
            }
        };
        Job job = new Job(taxId, task);
        RUNNING.put(taxId, job);

        task.setOnSucceeded(_ -> {
            Outcome outcome;
            try {
                SyncResult result = task.getValue();
                List<BirMail> all = vm.save(result);   // the cache is updated even if no page is open
                outcome = new Outcome(all, result.fresh().size(), result.reset(), null);
            } catch (Exception ex) {
                ex.printStackTrace();
                outcome = new Outcome(List.of(), 0, false, ex);
            }
            job.finish(outcome);
        });
        task.setOnFailed(_ -> {
            Throwable error = task.getException();
            if (error != null) error.printStackTrace();
            job.finish(new Outcome(List.of(), 0, false, error));
        });

        Thread t = new Thread(task, "mail-sync-" + taxId);
        t.setDaemon(true);
        t.start();
        return job;
    }
}