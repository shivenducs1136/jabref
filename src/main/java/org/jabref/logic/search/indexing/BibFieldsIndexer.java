package org.jabref.logic.search.indexing;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.jabref.gui.util.BackgroundTask;
import org.jabref.gui.util.TaskExecutor;
import org.jabref.logic.importer.util.FileFieldParser;
import org.jabref.logic.l10n.Localization;
import org.jabref.logic.util.HeadlessExecutorService;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.KeywordList;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.field.Field;
import org.jabref.model.search.LuceneIndexer;
import org.jabref.model.search.SearchFieldConstants;
import org.jabref.preferences.PreferencesService;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jabref.model.entry.field.StandardField.FILE;
import static org.jabref.model.entry.field.StandardField.GROUPS;
import static org.jabref.model.entry.field.StandardField.KEYWORDS;

public class BibFieldsIndexer implements LuceneIndexer {
    private static final Logger LOGGER = LoggerFactory.getLogger(BibFieldsIndexer.class);
    private final BibDatabaseContext databaseContext;
    private final TaskExecutor taskExecutor;
    private final PreferencesService preferences;
    private final String libraryName;
    private final Directory indexDirectory;
    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private IndexSearcher indexSearcher;

    public BibFieldsIndexer(BibDatabaseContext databaseContext, TaskExecutor executor, PreferencesService preferences) throws IOException {
        this.databaseContext = databaseContext;
        this.taskExecutor = executor;
        this.preferences = preferences;
        this.libraryName = databaseContext.getDatabasePath().map(path -> path.getFileName().toString()).orElseGet(() -> "unsaved");
        this.indexDirectory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(SearchFieldConstants.ANALYZER);

        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        this.indexWriter = new IndexWriter(indexDirectory, config);
        this.searcherManager = new SearcherManager(indexWriter, null);
    }

    @Override
    public void updateOnStart() {
        addToIndex(databaseContext.getDatabase().getEntries());
    }

    @Override
    public void addToIndex(Collection<BibEntry> entries) {
        new BackgroundTask<>() {
            @Override
            protected Void call() {
                int i = 1;
                for (BibEntry entry : entries) {
                    if (isCanceled()) {
                        updateMessage(Localization.lang("Indexing canceled: %0 of %1 entries added to the index", i, entries.size()));
                        return null;
                    }
                    if (entries.size() == 1) {
                        LOGGER.info("Adding entry {} to index", entry.getId());
                    }
                    addToIndex(entry);
                    updateProgress(i, entries.size());
                    updateMessage(Localization.lang("%0 of %1 entries added to the index", i, entries.size()));
                    i++;
                }
                return null;
            }
        }.showToUser(entries.size() > 1)
         .setTitle(Localization.lang("Indexing bib fields for %0", libraryName))
         .executeWith(taskExecutor);
    }

    private void addToIndex(BibEntry bibEntry) {
        try {
            LOGGER.debug("Adding entry {} to index", bibEntry.getId());
            Document document = new Document();
            org.apache.lucene.document.Field.Store store = org.apache.lucene.document.Field.Store.YES;

            document.add(new StringField(SearchFieldConstants.BIB_ENTRY_ID, bibEntry.getId(), store));
            document.add(new StringField(SearchFieldConstants.BIB_ENTRY_TYPE, bibEntry.getType().getName(), store));

            for (Map.Entry<Field, String> field : bibEntry.getFieldMap().entrySet()) {
                String fieldValue = field.getValue();
                String fieldName = field.getKey().getName();
                SearchFieldConstants.SEARCHABLE_BIB_FIELDS.add(fieldName);

                switch (field.getKey()) {
                    case KEYWORDS ->
                            KeywordList.parse(fieldValue, preferences.getBibEntryPreferences().getKeywordSeparator())
                                       .forEach(keyword -> document.add(new StringField(fieldName, keyword.toString(), store)));
                    case GROUPS ->
                            Arrays.stream(fieldValue.split(preferences.getBibEntryPreferences().getKeywordSeparator().toString()))
                                  .forEach(group -> document.add(new StringField(fieldName, group, store)));
                    case FILE ->
                            FileFieldParser.parse(fieldValue).stream()
                                           .map(LinkedFile::getLink)
                                           .forEach(link -> document.add(new StringField(fieldName, link, store)));
                    default ->
                            document.add(new TextField(fieldName, fieldValue, store));
                }
            }
            indexWriter.addDocument(document);
            LOGGER.debug("Entry {} added to index", bibEntry.getId());
        } catch (IOException e) {
            LOGGER.warn("Could not add an entry to the index.", e);
        }
    }

    @Override
    public void removeFromIndex(Collection<BibEntry> entries) {
        entries.forEach(this::removeFromIndex);
    }

    private void removeFromIndex(BibEntry entry) {
        BackgroundTask.wrap(() -> {
            try {
                LOGGER.info("Removing entry {} from index", entry.getId());
                indexWriter.deleteDocuments((new Term(SearchFieldConstants.BIB_ENTRY_ID, entry.getId())));
                LOGGER.info("Entry {} removed from index", entry.getId());
            } catch (IOException e) {
                LOGGER.error("Error deleting entry from index", e);
            }
        }).executeWithAndWait(taskExecutor);
    }

    @Override
    public void updateEntry(BibEntry entry, String oldValue, String newValue) {
        LOGGER.info("Updating entry {} in index", entry.getId());
        removeFromIndex(List.of(entry));
        addToIndex(List.of(entry));
    }

    @Override
    public void removeAllFromIndex() {
        BackgroundTask.wrap(() -> {
            try {
                LOGGER.info("Removing all bib fields from index");
                indexWriter.deleteAll();
                LOGGER.info("All bib fields removed from index");
            } catch (IOException e) {
                LOGGER.error("Error deleting all linked files from index", e);
            }
        }).executeWithAndWait(taskExecutor);
    }

    @Override
    public void rebuildIndex() {
        removeAllFromIndex();
        addToIndex(databaseContext.getDatabase().getEntries());
    }

    @Override
    public IndexSearcher getIndexSearcher() {
        LOGGER.info("Getting index searcher");
        try {
            if (indexSearcher != null) {
                LOGGER.info("Releasing index searcher");
                searcherManager.release(indexSearcher);
            }
            LOGGER.info("Refreshing searcher");
            searcherManager.maybeRefresh();
            LOGGER.info("Acquiring index searcher");
            indexSearcher = searcherManager.acquire();
        } catch (IOException e) {
            LOGGER.error("Error refreshing searcher", e);
        }
        return indexSearcher;
    }

    @Override
    public void close() {
        LOGGER.info("Closing index");
        HeadlessExecutorService.INSTANCE.execute(() -> {
            try {
                searcherManager.close();
                indexWriter.close();
                indexDirectory.close();
                LOGGER.info("Index closed");
            } catch (IOException e) {
                LOGGER.error("Error closing index", e);
            }
        });
    }
}
