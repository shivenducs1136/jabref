package org.jabref.logic.search.indexing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.strings.StringUtil;
import org.jabref.preferences.FilePreferences;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jabref.model.search.SearchFieldConstants.ANNOTATIONS;
import static org.jabref.model.search.SearchFieldConstants.CONTENT;
import static org.jabref.model.search.SearchFieldConstants.MODIFIED;
import static org.jabref.model.search.SearchFieldConstants.PAGE_NUMBER;
import static org.jabref.model.search.SearchFieldConstants.PATH;

/**
 * Utility class for reading the data from LinkedFiles of a BibEntry for Lucene.
 */
public final class DocumentReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentReader.class);

    private static final Pattern HYPHEN_LINEBREAK_PATTERN = Pattern.compile("\\-\n");
    private static final Pattern LINEBREAK_WITHOUT_PERIOD_PATTERN = Pattern.compile("([^\\\\.])\\n");

    private final FilePreferences filePreferences;

    /**
     * Creates a new DocumentReader using a BibEntry.
     *
     */
    public DocumentReader(FilePreferences filePreferences) {
        this.filePreferences = filePreferences;
    }

    /**
     * Reads a LinkedFile of a BibEntry and converts it into a Lucene Document which is then returned.
     *
     * @return An Optional of a Lucene Document with the (meta)data. Can be empty if there is a problem reading the LinkedFile.
     */
    public Optional<List<Document>> readLinkedPdf(BibDatabaseContext databaseContext, LinkedFile pdf) {
        Optional<Path> pdfPath = pdf.findIn(databaseContext, filePreferences);
        return pdfPath.map(path -> readPdfContents(pdf, path));
    }

    /**
     * Reads each LinkedFile of a BibEntry and converts them into Lucene Documents which are then returned.
     *
     * @return A List of Documents with the (meta)data. Can be empty if there is a problem reading the LinkedFile.
     */
    public List<Document> readLinkedPdfs(BibDatabaseContext databaseContext, BibEntry entry) {
        return entry.getFiles().stream()
                    .map(pdf -> readLinkedPdf(databaseContext, pdf))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
    }

    public List<Document> readPdfContents(LinkedFile pdf, Path resolvedPdfPath) {
        List<Document> pages = new ArrayList<>();
        try (PDDocument pdfDocument = Loader.loadPDF(resolvedPdfPath.toFile())) {
            int numberOfPages = pdfDocument.getNumberOfPages();
            for (int pageNumber = 1; pageNumber <= numberOfPages; pageNumber++) {
                Document newDocument = new Document();
                addIdentifiers(newDocument, pdf.getLink());
                addMetaData(newDocument, resolvedPdfPath, pageNumber);
                addContentIfNotEmpty(pdfDocument, newDocument, resolvedPdfPath, pageNumber);

                pages.add(newDocument);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", resolvedPdfPath.toAbsolutePath(), e);
        }
        if (pages.isEmpty()) {
            Document newDocument = new Document();
            addIdentifiers(newDocument, pdf.getLink());
            addMetaData(newDocument, resolvedPdfPath, 1);
            pages.add(newDocument);
        }
        return pages;
    }

    private void addStringField(Document newDocument, String field, String value) {
        if (!isValidField(value)) {
            return;
        }
        newDocument.add(new StringField(field, value, Field.Store.YES));
    }

    private boolean isValidField(String value) {
        return !StringUtil.isNullOrEmpty(value);
    }

    public static String mergeLines(String text) {
        String mergedHyphenNewlines = HYPHEN_LINEBREAK_PATTERN.matcher(text).replaceAll("");
        return LINEBREAK_WITHOUT_PERIOD_PATTERN.matcher(mergedHyphenNewlines).replaceAll("$1 ");
    }

    private void addMetaData(Document newDocument, Path resolvedPdfPath, int pageNumber) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(resolvedPdfPath, BasicFileAttributes.class);
            addStringField(newDocument, MODIFIED, String.valueOf(attributes.lastModifiedTime().to(TimeUnit.SECONDS)));
        } catch (IOException e) {
            LOGGER.error("Could not read timestamp for {}", resolvedPdfPath, e);
        }
        addStringField(newDocument, PAGE_NUMBER, String.valueOf(pageNumber));
    }

    private void addContentIfNotEmpty(PDDocument pdfDocument, Document newDocument, Path resolvedPath, int pageNumber) {
        PDFTextStripper pdfTextStripper = new PDFTextStripper();
        pdfTextStripper.setLineSeparator("\n");
        pdfTextStripper.setStartPage(pageNumber);
        pdfTextStripper.setEndPage(pageNumber);

        try {
            String pdfContent = pdfTextStripper.getText(pdfDocument);
            if (StringUtil.isNotBlank(pdfContent)) {
                newDocument.add(new TextField(CONTENT, mergeLines(pdfContent), Field.Store.YES));
            }

            // Apache PDFTextStripper is 1-based. See {@link org.apache.pdfbox.text.PDFTextStripper.processPages}
            PDPage page = pdfDocument.getPage(pageNumber - 1);
            List<String> annotations = page.getAnnotations()
                                           .stream()
                                           .map(PDAnnotation::getContents)
                                           .filter(Objects::nonNull)
                                           .toList();

            if (!annotations.isEmpty()) {
                newDocument.add(new TextField(ANNOTATIONS, String.join("\n", annotations), Field.Store.YES));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read page {} of  {}", pageNumber, resolvedPath.toAbsolutePath(), e);
        }
    }

    private void addIdentifiers(Document newDocument, String path) {
        newDocument.add(new StringField(PATH, path, Field.Store.YES));
    }
}
