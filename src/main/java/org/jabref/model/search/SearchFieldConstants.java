package org.jabref.model.search;

import java.util.HashSet;
import java.util.Set;

public class SearchFieldConstants {

    public static final String VERSION = "99";
    public static final String FILE_FIELDS_PREFIX = "f_";
    public static final String PATH = FILE_FIELDS_PREFIX + "path";
    public static final String CONTENT = FILE_FIELDS_PREFIX + "content";
    public static final String ANNOTATIONS = FILE_FIELDS_PREFIX + "annotations";
    public static final String PAGE_NUMBER = FILE_FIELDS_PREFIX + "pageNumber";
    public static final String MODIFIED = FILE_FIELDS_PREFIX + "modified";
    public static final String BIB_ENTRY_ID_HASH = "id_hash";
    public static final String[] PDF_FIELDS = {PATH, CONTENT, ANNOTATIONS};
    public static Set<String> searchableBibFields = new HashSet<>();
}
