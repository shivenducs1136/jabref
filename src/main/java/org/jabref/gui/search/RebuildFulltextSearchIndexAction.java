package org.jabref.gui.search;

import java.io.IOException;

import org.jabref.gui.DialogService;
import org.jabref.gui.LibraryTab;
import org.jabref.gui.StateManager;
import org.jabref.gui.actions.SimpleCommand;
import org.jabref.gui.util.BackgroundTask;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.pdf.search.LuceneIndexer;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.preferences.FilePreferences;
import org.jabref.preferences.PreferencesService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jabref.gui.actions.ActionHelper.needsDatabase;

public class RebuildFulltextSearchIndexAction extends SimpleCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryTab.class);

    private final StateManager stateManager;
    private final GetCurrentLibraryTab currentLibraryTab;
    private final DialogService dialogService;
    private final PreferencesService preferencesService;
    private final FilePreferences filePreferences;
    private final TaskExecutor taskExecutor;

    private BibDatabaseContext databaseContext;

    private boolean shouldContinue = true;

    public RebuildFulltextSearchIndexAction(StateManager stateManager, GetCurrentLibraryTab currentLibraryTab, DialogService dialogService, PreferencesService preferences, FilePreferences filePreferences,
                                            TaskExecutor taskExecutor) {
        this.stateManager = stateManager;
        this.currentLibraryTab = currentLibraryTab;
        this.dialogService = dialogService;
        this.preferencesService = preferences;
        this.filePreferences = filePreferences;
        this.taskExecutor = taskExecutor;

        this.executable.bind(needsDatabase(stateManager));
    }

    @Override
    public void execute() {
        init();
        BackgroundTask.wrap(this::rebuildIndex)
                      .executeWith(taskExecutor);
    }

    public void init() {
        if (stateManager.getActiveDatabase().isEmpty()) {
            return;
        }

        databaseContext = stateManager.getActiveDatabase().get();
        boolean confirm = dialogService.showConfirmationDialogAndWait(
                Localization.lang("Rebuild fulltext search index"),
                Localization.lang("Rebuild fulltext search index for current library?"));
        if (!confirm) {
            shouldContinue = false;
            return;
        }
        dialogService.notify(Localization.lang("Rebuilding fulltext search index..."));
    }

    private void rebuildIndex() {
        if (!shouldContinue || stateManager.getActiveDatabase().isEmpty()) {
            return;
        }
        try {
            currentLibraryTab.get().getIndexingTaskManager().createIndex(LuceneIndexer.of(databaseContext, preferencesService));
            currentLibraryTab.get().getIndexingTaskManager().updateIndex(LuceneIndexer.of(databaseContext, preferencesService));
        } catch (IOException e) {
            dialogService.notify(Localization.lang("Failed to access fulltext search index"));
            LOGGER.error("Failed to access fulltext search index", e);
        }
    }

    public interface GetCurrentLibraryTab {
        LibraryTab get();
    }
}
