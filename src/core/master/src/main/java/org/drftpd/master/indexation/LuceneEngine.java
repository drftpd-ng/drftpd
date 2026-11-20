/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.drftpd.master.indexation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectorManager;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.FieldComparatorSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LeafFieldComparator;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Pruning;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorable;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.indexation.AdvancedSearchParams.InodeType;
import org.drftpd.master.indexation.analysis.AlphanumericalAnalyzer;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.reflections.Reflections;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;
import java.util.regex.PatternSyntaxException;

/**
 * Implementation of an Index engine based on <a href="https://lucene.apache.org">Apache Lucene</a>.
 * <p>
 * One document is stored per inode (see {@link #makeDocumentFromInode(ImmutableInodeHandle)}),
 * identified by its {@code fullPath} field. Searches go through a {@link SearcherManager} that is
 * refreshed before every query so that they see every change made so far (near real time), as the
 * previous implementation did by opening a reader from the writer for every search.
 */
public class LuceneEngine implements IndexEngineInterface {
    protected static final Analyzer ANALYZER = new AlphanumericalAnalyzer();
    protected static final String INDEX_DIR = "userdata/index";
    /** Suffix added to the index directory when it is moved aside because it can't be read. */
    static final String INCOMPATIBLE_SUFFIX = ".incompatible-";
    /**
     * Commit user data entry set to "true" while the index is not a complete image of the VFS:
     * from the creation of an empty index replacing an unreadable one, or from the start of a
     * rebuild, until the final commit of a successful rebuild. Every commit made meanwhile
     * (periodic commits, shutdown) keeps it, so the next start rebuilds again.
     */
    static final String REBUILD_PENDING_KEY = "drftpd.rebuildPending";

    private static final Logger logger = LogManager.getLogger(LuceneEngine.class);
    private static final String EXCEPTION_OCCURED_WHILE_SEARCHING = "An exception occured while searching, check stack trace";

    static final String FIELD_NAME = "name";
    static final String FIELD_FULL_NAME = "fullName";
    static final String FIELD_FULL_NAME_REVERSE = "fullNameReverse";
    static final String FIELD_PARENT_PATH = "parentPath";
    static final String FIELD_FULL_PATH = "fullPath";
    static final String FIELD_OWNER = "owner";
    static final String FIELD_GROUP = "group";
    static final String FIELD_TYPE = "type";
    static final String FIELD_SLAVES = "slaves";
    static final String FIELD_SLAVES_NBR = "nbrOfSlaves";
    static final String FIELD_LASTMODIFIED = "lastModified";
    static final String FIELD_SIZE = "size";

    private static final Set<String> CORE_FIELDS = Set.of(FIELD_NAME, FIELD_FULL_NAME, FIELD_FULL_NAME_REVERSE,
            FIELD_PARENT_PATH, FIELD_FULL_PATH, FIELD_OWNER, FIELD_GROUP, FIELD_TYPE, FIELD_SLAVES, FIELD_SLAVES_NBR,
            FIELD_LASTMODIFIED, FIELD_SIZE);
    /** Stored fields loaded by {@link #findInode} (replaces SimpleSearchFieldSelector). */
    private static final Set<String> SIMPLE_SEARCH_FIELDS = Set.of(FIELD_FULL_PATH);
    /** Stored fields loaded by {@link #advancedFind} (replaces AdvancedSearchFieldSelector). */
    private static final Set<String> ADVANCED_SEARCH_FIELDS = Set.of(FIELD_FULL_PATH, FIELD_TYPE);

    private static final Query QUERY_DIRECTORY = new TermQuery(new Term(FIELD_TYPE, "d"));
    private static final Query QUERY_FILE = new TermQuery(new Term(FIELD_TYPE, "f"));

    private final Path _indexPath;
    /** Guards opening/closing the index and taking backups. */
    private final Object _lock = new Object();
    /**
     * Serialises a rename (several writer operations) against the other index updates: add, update
     * and delete share the read lock, a rename takes the write lock.
     */
    private final ReadWriteLock _updateLock = new ReentrantReadWriteLock();

    private volatile Directory _storage;
    private volatile IndexWriter _iWriter;
    private volatile SearcherManager _searcherManager;
    private volatile SnapshotDeletionPolicy _snapshotPolicy;

    private int _maxHitsNumber = 50;
    private int _maxDocsBuffer = IndexWriterConfig.DISABLE_AUTO_FLUSH;
    private int _maxRAMBufferSize = 16;
    private boolean _nativeLocking = true;

    private LuceneMaintenanceThread _maintenanceThread;
    private LuceneBackupThread _backupThread;

    private IndexingVirtualFileSystemListener _listener;
    private volatile boolean _rebuilding;
    private volatile boolean _rebuildRequired;
    private volatile RebuildProgress _rebuildProgress;
    private final AtomicBoolean _pendingRebuildStarted = new AtomicBoolean();
    private volatile Path _movedAsideIndex;

    private final List<IndexDataExtensionInterface> _dataExtensions = new ArrayList<>();
    private final List<QueryTermExtensionInterface> _queryExtensions = new ArrayList<>();

    public LuceneEngine() {
        this(Paths.get(INDEX_DIR));
    }

    /**
     * @param indexPath directory holding the index (used by the tests).
     */
    LuceneEngine(Path indexPath) {
        _indexPath = indexPath;
    }

    /**
     * Creates all the needed resources for the Index to work.
     * <ul>
     * <li>IndexWriter / SearcherManager</li>
     * <li>Reads <i>config/indexation.conf</i> to grab some tweaking
     * settings, if this file is not found, a default values are loaded.</li>
     * <li>Adds a Shutdown Hook to save the index while closing DrFTPD</li>
     * <li>Creates the Maintenance and Backup threads.</li>
     * <li>If the index on disk could not be read (written by an older Lucene, corrupt...), it has
     * been moved aside by {@link #openStreams()} and a rebuild is started in the background
     * once the plugins are loaded ({@link #pluginsLoaded()}).</li>
     * </ul>
     */
    public void init() throws IndexException {
        logger.debug("Initializing Index");

        // Load index data extensions
        Set<Class<? extends IndexDataExtensionInterface>> indexExtensions = new Reflections("org.drftpd")
                .getSubTypesOf(IndexDataExtensionInterface.class);
        try {
            for (Class<? extends IndexDataExtensionInterface> indexExtension : indexExtensions) {
                _dataExtensions.add(indexExtension.getConstructor().newInstance());
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for org.drftpd.master.vfs.index.lucene extension point 'IndexData', possibly the " +
                    "org.drftpd.master.vfs.index.lucene extension point definition has changed in the plugin.xml", e);
        }

        // Load query term extensions
        Set<Class<? extends QueryTermExtensionInterface>> queryExtensions = new Reflections("org.drftpd")
                .getSubTypesOf(QueryTermExtensionInterface.class);
        try {
            for (Class<? extends QueryTermExtensionInterface> queryExtension : queryExtensions) {
                _queryExtensions.add(queryExtension.getConstructor().newInstance());
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for org.drftpd.master.vfs.index.lucene extension point 'QueryTerm', possibly the " +
                    "org.drftpd.master.vfs.index.lucene extension point definition has changed in the plugin.xml", e);
        }

        AnnotationProcessor.process(this);
        createThreads();
        reload();

        openStreams();

        Runtime.getRuntime().addShutdownHook(new Thread(new IndexShutdownHookRunnable(), "IndexSaverThread"));
        _maintenanceThread.start();
        if (_backupThread._maxNumberBackup > 0) {
            _backupThread.start();
        }

        _listener = new IndexingVirtualFileSystemListener();
        _listener.init();

        if (_rebuildRequired) {
            logger.warn("The index must be rebuilt, the rebuild starts once the plugins are loaded");
        }
    }

    private void createThreads() {
        _maintenanceThread = new LuceneMaintenanceThread(this);
        _backupThread = new LuceneBackupThread(this);
    }

    /**
     * Starts the automatic rebuild, if the index must be rebuilt (see {@link #isRebuildRequired()}).
     * It is not started by {@link #init()}: the index is initialised before the master plugins
     * are loaded, and the rebuild indexes the whole VFS with the master fully set up.
     * Until it starts, the index behaves as during a rebuild: searches return what has been
     * indexed so far and no backup is made.
     */
    @Override
    public void pluginsLoaded() {
        startPendingRebuild(this::walkVirtualFileSystem);
    }

    /**
     * Starts the pending rebuild with the given walker in a background thread, at most once.
     *
     * @return the rebuild thread, or null if no rebuild was required or one was already started
     */
    Thread startPendingRebuild(InodeWalker walker) {
        if (!_rebuildRequired || !_pendingRebuildStarted.compareAndSet(false, true)) {
            return null;
        }
        Thread rebuild = new Thread(() -> {
            logger.warn("Rebuilding the index from the virtual file system in the background, " +
                    "SITE SEARCH/DUPE results are incomplete until it is done");
            try {
                rebuildIndex(walker);
            } catch (Exception e) {
                logger.error("Automatic index rebuild failed, run SITE REBUILDINDEX", e);
            }
        }, "IndexRebuildThread");
        rebuild.setDaemon(true);
        rebuild.start();
        return rebuild;
    }

    /**
     * Opens all the needed streams that the engine needs to work properly.
     * <p>
     * If the existing index can't be read by this Lucene version (an index written by Lucene 3.6,
     * which DrFTPD used up to 4.0.11, or a corrupt one), the index directory is renamed to
     * {@code <index dir>.incompatible-<date>} (nothing is deleted), a new empty index is created and
     * {@link #isRebuildRequired()} becomes true.
     *
     * @throws IndexException
     */
    void openStreams() throws IndexException {
        synchronized (_lock) {
            try {
                Files.createDirectories(_indexPath);
                try {
                    openWriter();
                } catch (IndexFormatTooOldException | IndexFormatTooNewException | CorruptIndexException |
                         EOFException e) {
                    abortAll();
                    Path aside = moveIndexAside(e);
                    _movedAsideIndex = aside;
                    logger.warn("The index in {} can't be read by Lucene {} ({}). It was moved to {} and a new, " +
                                    "empty index was created: it has to be rebuilt from the virtual file system " +
                                    "(SITE REBUILDINDEX). The old directory can be deleted once the rebuild is done.",
                            _indexPath, Version.LATEST, e.getMessage(), aside);
                    openWriter();
                    // persisted, so that the rebuild is resumed if the master stops before it ends
                    setRebuildPending(_iWriter, true);
                    _iWriter.commit();
                    _rebuildRequired = true;
                }
            } catch (IOException e) {
                abortAll();
                throw new IndexException("Unable to initialize the index", e);
            }
        }
    }

    private void openWriter() throws IOException {
        // FSDirectory.open() picks MMapDirectory; NativeFSLockFactory is what Lucene 3.6 used whatever
        // native_locking said. native_locking=false now selects a lock file instead (see indexation.conf).
        _storage = _nativeLocking
                ? FSDirectory.open(_indexPath, NativeFSLockFactory.INSTANCE)
                : FSDirectory.open(_indexPath, SimpleFSLockFactory.INSTANCE);

        IndexWriterConfig conf = new IndexWriterConfig(ANALYZER);
        conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        conf.setRAMBufferSizeMB(_maxRAMBufferSize);
        conf.setMaxBufferedDocs(_maxDocsBuffer);
        // keeps the files of a commit alive while a backup copies them
        _snapshotPolicy = new SnapshotDeletionPolicy(new KeepOnlyLastCommitDeletionPolicy());
        conf.setIndexDeletionPolicy(_snapshotPolicy);

        boolean rebuildPending = false;
        if (DirectoryReader.indexExists(_storage)) {
            Map<String, String> userData = SegmentInfos.readLatestCommit(_storage).getUserData();
            rebuildPending = "true".equals(userData.get(REBUILD_PENDING_KEY));
        }

        _iWriter = new IndexWriter(_storage, conf);
        // opening a reader also opens every segment of an existing index
        _searcherManager = new SearcherManager(_iWriter, null);
        // carry the flag over to the next commits until a rebuild clears it
        setRebuildPending(_iWriter, rebuildPending);
        // make sure there is a commit point, needed by backups (no-op on an existing, unchanged index)
        _iWriter.commit();

        if (rebuildPending) {
            _rebuildRequired = true;
            logger.warn("The index in {} is incomplete: a rebuild was started but did not finish. " +
                    "It will be rebuilt again.", _indexPath);
        }
    }

    private static void setRebuildPending(IndexWriter writer, boolean pending) {
        Map<String, String> userData = pending ? Map.of(REBUILD_PENDING_KEY, "true") : Map.of();
        writer.setLiveCommitData(userData.entrySet());
    }

    /**
     * @return whether the last commit on disk is flagged as an incomplete index.
     */
    boolean isRebuildPendingOnDisk() throws IOException {
        synchronized (_lock) {
            return "true".equals(SegmentInfos.readLatestCommit(_storage).getUserData().get(REBUILD_PENDING_KEY));
        }
    }

    /**
     * Renames the unreadable index directory. When the index path is a symbolic link (or a
     * junction), the directory it points to is renamed and recreated empty, the link is kept.
     */
    private Path moveIndexAside(IOException cause) throws IndexException {
        Path source = _indexPath;
        Path target = null;
        try {
            source = _indexPath.toRealPath();
            String suffix = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            target = source.resolveSibling(source.getFileName() + INCOMPATIBLE_SUFFIX + suffix);
            for (int i = 1; Files.exists(target); i++) {
                target = source.resolveSibling(source.getFileName() + INCOMPATIBLE_SUFFIX + suffix + "-" + i);
            }
            Files.move(source, target);
            Files.createDirectories(source);
            return target;
        } catch (IOException e) {
            IndexException failure = new IndexException("The index in " + source + " can't be read by Lucene "
                    + Version.LATEST + " (" + cause.getMessage() + ") and could not be renamed to "
                    + (target == null ? "a backup name" : target) + " (" + e + "). Stop the master, move or rename "
                    + source + " by hand, then start the master again: the index will be rebuilt.", e);
            failure.addSuppressed(cause);
            throw failure;
        }
    }

    /**
     * Reads all tweak settings from <i>config/indexation.conf</i> if found,
     * otherwise use default values.
     */
    private void reload() {
        Properties cfg = ConfigLoader.loadConfig("indexation.conf");
        _maxHitsNumber = Integer.parseInt(cfg.getProperty("max_hits", "50"));
        _maxDocsBuffer = Integer.parseInt(cfg.getProperty("maxdocs_buffer", "-1"));
        _maxRAMBufferSize = Integer.parseInt(cfg.getProperty("max_rambuffer", "16"));
        _nativeLocking = cfg.getProperty("native_locking", "true").equals("true");

        // in minutes, convert'em!
        int optimizeInterval = Integer.parseInt(cfg.getProperty("optimize_interval", "15")) * 60 * 1000;
        _maintenanceThread.setOptimizationInterval(optimizeInterval);

        // in minutes, convert it!
        boolean doBackups = cfg.getProperty("do_backups", "true").equals("true");
        int interval = Integer.parseInt(cfg.getProperty("backup_interval", "120")) * 60 * 1000;
        int maxNumber = Integer.parseInt(cfg.getProperty("max_backups", "2"));
        _backupThread.setDoBackups(doBackups);
        _backupThread.setBackupInterval(interval);
        _backupThread.setMaximumNumberBackup(maxNumber);
    }

    void setMaxHitsNumber(int maxHits) {
        _maxHitsNumber = maxHits;
    }

    void addDataExtension(IndexDataExtensionInterface extension) {
        _dataExtensions.add(extension);
    }

    void addQueryExtension(QueryTermExtensionInterface extension) {
        _queryExtensions.add(extension);
    }

    /**
     * Commits and closes all streams, ignoring exceptions.
     */
    void closeAll() {
        synchronized (_lock) {
            try {
                if (_searcherManager != null)
                    _searcherManager.close();
            } catch (Exception e) {
                logger.error("Error closing the index searcher manager", e);
            }
            try {
                // commits; a rebuild in progress is committed with its "rebuild pending" flag,
                // so the next start sees the index is incomplete and rebuilds it again
                if (_iWriter != null)
                    _iWriter.close();
            } catch (Exception e) {
                logger.error("Error closing the index writer", e);
            }
            try {
                if (_storage != null)
                    _storage.close();
            } catch (Exception e) {
                logger.error("Error closing the index directory", e);
            }

            _searcherManager = null;
            _iWriter = null;
            _storage = null;
        }
    }

    /**
     * Closes all streams without committing anything, ignoring exceptions.
     */
    private void abortAll() {
        try {
            if (_searcherManager != null)
                _searcherManager.close();
        } catch (Exception e) {
            logger.debug("Error closing the searcher manager", e);
        }
        try {
            if (_iWriter != null)
                _iWriter.rollback();
        } catch (Exception e) {
            logger.debug("Error rolling back the index writer", e);
        }
        try {
            if (_storage != null)
                _storage.close();
        } catch (Exception e) {
            logger.debug("Error closing the index directory", e);
        }
        _searcherManager = null;
        _iWriter = null;
        _storage = null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * @param inodePath path of the inode, without trailing separator
     * @return the value of the parentPath field (kept as in previous versions: "//" for the
     * children of the root directory)
     */
    static String parentPathOf(String inodePath) {
        if (inodePath.equals(VirtualFileSystem.separator)) {
            return "";
        }
        return VirtualFileSystem.stripLast(inodePath) + VirtualFileSystem.separator;
    }

    private static void addSortableStringField(Document doc, String name, String value) {
        doc.add(new StringField(name, value, Field.Store.YES));
        doc.add(new SortedDocValuesField(name, new BytesRef(value)));
    }

    /**
     * Adds the fields maintained by the engine itself.
     */
    private static void addCoreFields(Document doc, String name, String parentPath, String fullPath, String owner,
                                      String group, String type, String slaves, int nbrOfSlaves, long lastModified,
                                      long size) {
        name = nullToEmpty(name);
        LuceneUtils.addTextField(doc, FIELD_NAME, name);
        doc.add(new StringField(FIELD_FULL_NAME, name, Field.Store.YES));
        doc.add(new StringField(FIELD_FULL_NAME_REVERSE, new StringBuilder(name).reverse().toString(), Field.Store.YES));
        addSortableStringField(doc, FIELD_PARENT_PATH, parentPath);
        addSortableStringField(doc, FIELD_FULL_PATH, fullPath);
        addSortableStringField(doc, FIELD_OWNER, nullToEmpty(owner));
        addSortableStringField(doc, FIELD_GROUP, nullToEmpty(group));
        addSortableStringField(doc, FIELD_TYPE, type);
        LuceneUtils.addTextField(doc, FIELD_SLAVES, slaves);
        LuceneUtils.addIntField(doc, FIELD_SLAVES_NBR, nbrOfSlaves);
        doc.add(new NumericDocValuesField(FIELD_SLAVES_NBR, nbrOfSlaves));
        LuceneUtils.addLongField(doc, FIELD_LASTMODIFIED, lastModified);
        doc.add(new NumericDocValuesField(FIELD_LASTMODIFIED, lastModified));
        LuceneUtils.addLongField(doc, FIELD_SIZE, size);
        doc.add(new NumericDocValuesField(FIELD_SIZE, size));
    }

    /**
     * Shortcut to create Lucene Document from the Inode's data. The fields that
     * are stored in the index are:
     * <ul>
     * <li>name - The name of the inode (analyzed)</li>
     * <li>fullName - The full name of the inode</li>
     * <li>fullNameReverse - The full name of the inode in reverse order</li>
     * <li>parentPath - The full path of the parent inode</li>
     * <li>fullPath - The full path of the inode (with a trailing / for directories)</li>
     * <li>owner - The user who owns the file</li>
     * <li>group - The group of the user who owns the file</li>
     * <li>type - File (f) or Directory (d)</li>
     * <li>slaves - If the inode is a file, then the slaves are stored</li>
     * <li>nbrOfSlaves - Number of slaves holding the file</li>
     * <li>lastModified - Timestamp of when the inode was last modified</li>
     * <li>size - The size of the inode</li>
     * </ul>
     * plus the fields of the {@link IndexDataExtensionInterface}s.
     * A new document is built for every call (the Lucene 3.6 code reused one shared, synchronized
     * document, which buys nothing with current Lucene versions).
     *
     * @param inode
     * @throws FileNotFoundException
     */
    Document makeDocumentFromInode(ImmutableInodeHandle inode) throws FileNotFoundException {
        Document doc = new Document();
        boolean isDirectory = inode.isDirectory();

        String slaves = "";
        int nbrOfSlaves = 0;
        if (!isDirectory) {
            StringBuilder sb = new StringBuilder();
            Set<String> slaveNames = inode.getSlaveNames();
            for (String slaveName : slaveNames) {
                sb.append(slaveName).append(",");
            }
            slaves = sb.toString();
            nbrOfSlaves = slaveNames.size();
        }

        addCoreFields(doc, inode.getName(), parentPathOf(inode.getPath()), fullPathOf(inode), inode.getUsername(),
                inode.getGroup(), isDirectory ? "d" : "f", slaves, nbrOfSlaves, inode.lastModified(), inode.getSize());

        // Add data from any extensions
        for (IndexDataExtensionInterface dataExtension : _dataExtensions) {
            dataExtension.addData(doc, inode);
        }

        return doc;
    }

    /**
     * Rebuilds the document of an inode whose parent directory was renamed from the stored values
     * of its previous document (a stored document can't be indexed again as it is).
     * Extension fields follow the convention of {@link IndexDataExtensionInterface}.
     */
    static Document rebuildDocument(Document stored, String newFullPath) {
        Document doc = new Document();
        String type = stored.get(FIELD_TYPE);
        String inodePath = "d".equals(type) ? VirtualFileSystem.fixPath(newFullPath) : newFullPath;

        addCoreFields(doc, stored.get(FIELD_FULL_NAME), parentPathOf(inodePath), newFullPath, stored.get(FIELD_OWNER),
                stored.get(FIELD_GROUP), type, nullToEmpty(stored.get(FIELD_SLAVES)),
                numericValue(stored, FIELD_SLAVES_NBR).intValue(), numericValue(stored, FIELD_LASTMODIFIED).longValue(),
                numericValue(stored, FIELD_SIZE).longValue());

        for (IndexableField field : stored.getFields()) {
            if (CORE_FIELDS.contains(field.name())) {
                continue;
            }
            Number number = field.numericValue();
            if (number instanceof Integer) {
                LuceneUtils.addIntField(doc, field.name(), number.intValue());
            } else if (number instanceof Long) {
                LuceneUtils.addLongField(doc, field.name(), number.longValue());
            } else if (number instanceof Float) {
                doc.add(new StoredField(field.name(), number.floatValue()));
            } else if (number instanceof Double) {
                doc.add(new StoredField(field.name(), number.doubleValue()));
            } else if (field.stringValue() != null) {
                LuceneUtils.addTextField(doc, field.name(), field.stringValue());
            } else if (field.binaryValue() != null) {
                doc.add(new StoredField(field.name(), BytesRef.deepCopyOf(field.binaryValue())));
            }
        }
        return doc;
    }

    private static Number numericValue(Document doc, String field) {
        IndexableField f = doc.getField(field);
        return f == null || f.numericValue() == null ? Integer.valueOf(0) : f.numericValue();
    }

    private static String fullPathOf(ImmutableInodeHandle inode) {
        if (inode.isDirectory()) {
            return inode.getPath() + VirtualFileSystem.separator;
        }
        return inode.getPath();
    }

    private static Term makeFullPathTermFromInode(ImmutableInodeHandle inode) {
        return new Term(FIELD_FULL_PATH, fullPathOf(inode));
    }

    private IndexWriter writer() throws IOException {
        IndexWriter writer = _iWriter;
        if (writer == null) {
            throw new IOException("The index is not open");
        }
        return writer;
    }

    private SearcherManager searcherManager() throws IOException {
        SearcherManager searcherManager = _searcherManager;
        if (searcherManager == null) {
            throw new IOException("The index is not open");
        }
        return searcherManager;
    }

    private static IndexSearcher acquireFreshSearcher(SearcherManager searcherManager) throws IOException {
        // see every change made so far, including the ones not committed yet
        searcherManager.maybeRefreshBlocking();
        return searcherManager.acquire();
    }

    /* {@inheritDoc} */
    public void addInode(ImmutableInodeHandle inode) throws IndexException {
        _updateLock.readLock().lock();
        try {
            // updateDocument rather than addDocument: never index the same path twice
            writer().updateDocument(makeFullPathTermFromInode(inode), makeDocumentFromInode(inode));
        } catch (FileNotFoundException e) {
            logger.error("Unable to add {} to the index", inode.getPath(), e);
        } catch (RuntimeException | IOException e) {
            throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
        } finally {
            _updateLock.readLock().unlock();
        }
    }

    /* {@inheritDoc} */
    public void deleteInode(ImmutableInodeHandle inode) throws IndexException {
        _updateLock.readLock().lock();
        try {
            writer().deleteDocuments(makeFullPathTermFromInode(inode));
        } catch (RuntimeException | IOException e) {
            throw new IndexException("Unable to delete " + inode.getPath() + " from the index", e);
        } finally {
            _updateLock.readLock().unlock();
        }
    }

    /* {@inheritDoc} */
    public void updateInode(ImmutableInodeHandle inode) throws IndexException {
        _updateLock.readLock().lock();
        try {
            writer().updateDocument(makeFullPathTermFromInode(inode), makeDocumentFromInode(inode));
        } catch (FileNotFoundException e) {
            logger.error("The inode was here but now it isn't!", e);
        } catch (RuntimeException | IOException e) {
            throw new IndexException("Unable to update " + inode.getPath() + " in the index", e);
        } finally {
            _updateLock.readLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     * The rename of a directory replaces the documents of all its content, one by one: it holds
     * the write lock of {@link #_updateLock} so that no other update is applied in the middle.
     */
    public void renameInode(ImmutableInodeHandle fromInode, ImmutableInodeHandle toInode) throws IndexException {
        _updateLock.writeLock().lock();
        try {
            IndexWriter writer = writer();
            Term fromInodeTerm = makeFullPathTermFromInode(fromInode);
            replaceDocument(writer, fromInodeTerm, makeFullPathTermFromInode(toInode), makeDocumentFromInode(toInode));
            if (toInode.isDirectory()) {
                SearcherManager searcherManager = searcherManager();
                IndexSearcher iSearcher = acquireFreshSearcher(searcherManager);
                try {
                    List<Integer> docIds = iSearcher.search(new PrefixQuery(fromInodeTerm), new DocIdCollectorManager());
                    StoredFields storedFields = iSearcher.storedFields();
                    for (int docId : docIds) {
                        Document stored = storedFields.document(docId);
                        String oldPath = stored.get(FIELD_FULL_PATH);
                        String newPath = toInode.getPath() + oldPath.substring(fromInode.getPath().length());
                        replaceDocument(writer, new Term(FIELD_FULL_PATH, oldPath), new Term(FIELD_FULL_PATH, newPath),
                                rebuildDocument(stored, newPath));
                    }
                } finally {
                    searcherManager.release(iSearcher);
                }
            }
        } catch (FileNotFoundException e) {
            logger.error("Unable to rename {} to {} in the index", fromInode.getPath(), toInode.getPath(), e);
        } catch (RuntimeException | IOException e) {
            throw new IndexException("Unable to rename " + fromInode.getPath() + " to " +
                    toInode.getPath() + " in the index", e);
        } finally {
            _updateLock.writeLock().unlock();
        }
    }

    /**
     * Atomically deletes the documents of the old path and of the new path (an event about the
     * renamed inode may already have indexed it there) and adds the new document.
     */
    private static void replaceDocument(IndexWriter writer, Term oldPath, Term newPath, Document doc)
            throws IOException {
        Query delete = new BooleanQuery.Builder()
                .add(new TermQuery(oldPath), Occur.SHOULD)
                .add(new TermQuery(newPath), Occur.SHOULD)
                .build();
        writer.updateDocuments(delete, List.of(doc));
    }

    /**
     * {@inheritDoc}
     * Forces the Index to be saved. Simply calls {@link IndexWriter}.commit();
     */
    public void commit() throws IndexException {
        try {
            writer().commit();
        } catch (RuntimeException | IOException e) {
            throw new IndexException("Unable to commit the index", e);
        }
    }

    /**
     * Walks the inodes to index during a rebuild (in production through the VFS refresh events,
     * which end up in {@link #updateInode}).
     */
    interface InodeWalker {
        void walk() throws IndexException, FileNotFoundException;
    }

    /* {@inheritDoc} */
    public void rebuildIndex() throws IndexException, FileNotFoundException {
        rebuildIndex(this::walkVirtualFileSystem);
    }

    private void walkVirtualFileSystem() throws IndexException, FileNotFoundException {
        DirectoryHandle root = GlobalContext.getGlobalContext().getRoot();
        root.requestRefresh(true); // Start by adding root inode
        inodeWalked();
        recurseAndBuild(root); // Recursively traverse the VFS and add all inodes
    }

    private void inodeWalked() {
        RebuildProgress progress = _rebuildProgress;
        if (progress != null) {
            progress.inodeWalked();
        }
    }

    /**
     * Empties the index and fills it again through the walker.
     * The commit that empties the index flags it as incomplete ({@link #REBUILD_PENDING_KEY});
     * every commit made meanwhile keeps the flag and only the final commit of a successful
     * rebuild clears it, so an interrupted rebuild is detected on the next start.
     */
    void rebuildIndex(InodeWalker walker) throws IndexException, FileNotFoundException {
        synchronized (_lock) {
            if (_rebuilding) {
                throw new IndexException("A previous rebuildindex command is already in progress.");
            }
            try {
                IndexWriter writer = writer();
                _rebuilding = true;
                setRebuildPending(writer, true);
                // drop every document; the writer and the searchers stay open
                writer.deleteAll();
                writer.commit();
            } catch (RuntimeException | IOException e) {
                _rebuilding = false;
                _rebuildRequired = true;
                throw new IndexException("Unable to start rebuilding the index", e);
            }
        }

        RebuildProgress progress = new RebuildProgress(System::nanoTime);
        _rebuildProgress = progress;
        boolean success = false;
        try {
            walker.walk();
            synchronized (_lock) {
                IndexWriter writer = writer();
                setRebuildPending(writer, false);
                writer.commit(); // commit the writer so that the searcher can see the new stuff.
                _rebuildRequired = false;
                success = true;
                logger.info("Index rebuild done: {}, {} entries in the index", progress.describe(),
                        writer.getDocStats().numDocs);
            }
        } catch (IndexException e) {
            logger.error("Exception whilst rebuilding lucene index", e);
            throw e;
        } catch (FileNotFoundException e) {
            logger.error("Root directory not found whilst rebuilding lucene index", e);
            throw e;
        } catch (RuntimeException | IOException e) {
            logger.error("Exception whilst rebuilding lucene index", e);
            throw new IndexException("Unable to rebuild the index", e);
        } finally {
            if (!success) {
                // the index is partial: keep it flagged, no backup, rebuild again on next start
                _rebuildRequired = true;
                IndexWriter writer = _iWriter;
                if (writer != null) {
                    try {
                        setRebuildPending(writer, true);
                    } catch (RuntimeException e) {
                        logger.debug("Unable to flag the index as incomplete", e);
                    }
                }
            }
            _rebuilding = false;
            _rebuildProgress = null;
        }
    }

    /**
     * Inner function called by rebuildIndex().
     *
     * @param dir
     * @throws IndexException
     */
    private void recurseAndBuild(DirectoryHandle dir) throws IndexException {
        try {
            for (InodeHandle inode : dir.getInodeHandlesUnchecked()) {
                if (inode.isDirectory()) {
                    try {
                        inode.requestRefresh(true);
                        inodeWalked();
                        recurseAndBuild((DirectoryHandle) inode);
                    } catch (FileNotFoundException e) {
                        // Directory no longer present, silently skip
                    }

                } else if (inode.isFile()) {
                    try {
                        inode.requestRefresh(true);
                        inodeWalked();
                    } catch (FileNotFoundException e) {
                        // File no longer present, silently skip
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Directory no longer present, silently skip
        }
    }

    /*
     * Method to check if an index rebuild is in process or not.
     */
    public boolean isRebuilding() {
        return _rebuilding;
    }

    /**
     * @return true if the index found on disk at startup could not be read and was replaced by an
     * empty one that has not been rebuilt yet.
     */
    public boolean isRebuildRequired() {
        return _rebuildRequired;
    }

    /**
     * @return where the unreadable index was moved, or null.
     */
    Path getMovedAsideIndex() {
        return _movedAsideIndex;
    }

    /**
     * Builds the query for {@link #advancedFind}.
     *
     * @param startPath path of the directory where the search begins
     * @throws IllegalArgumentException if the parameters are invalid (wildcards, regex)
     */
    Query buildAdvancedQuery(String startPath, AdvancedSearchParams params) throws IllegalArgumentException {
        try {
            return doBuildAdvancedQuery(startPath, params);
        } catch (TooComplexToDeterminizeException e) {
            // wildcard patterns are turned into automata when the query is built
            throw tooComplex(e);
        }
    }

    private static IllegalArgumentException tooComplex(TooComplexToDeterminizeException e) {
        return new IllegalArgumentException("Search pattern too complex, use fewer wildcards.", e);
    }

    private Query doBuildAdvancedQuery(String startPath, AdvancedSearchParams params) {
        BooleanQuery.Builder query = new BooleanQuery.Builder();

        if (!startPath.equals(VirtualFileSystem.separator)) {
            query.add(new PrefixQuery(new Term(FIELD_PARENT_PATH, startPath + VirtualFileSystem.separator)), Occur.MUST);
        }

        if (params.getInodeType() == InodeType.DIRECTORY) {
            query.add(QUERY_DIRECTORY, Occur.MUST);
        } else if (params.getInodeType() == InodeType.FILE) {
            query.add(QUERY_FILE, Occur.MUST);
        }

        if (params.getOwner() != null) {
            query.add(new TermQuery(new Term(FIELD_OWNER, params.getOwner())), Occur.MUST);
        }
        if (params.getGroup() != null) {
            query.add(new TermQuery(new Term(FIELD_GROUP, params.getGroup())), Occur.MUST);
        }

        if (!params.getSlaves().isEmpty()) {
            query.add(LuceneUtils.analyze(FIELD_SLAVES, String.join(" ", params.getSlaves())), Occur.MUST);
        }

        if (params.getMinAge() != null || params.getMaxAge() != null) {
            query.add(LuceneUtils.longRangeQuery(FIELD_LASTMODIFIED, params.getMinAge(), params.getMaxAge()), Occur.MUST);
        }

        if (params.getMinSize() != null || params.getMaxSize() != null) {
            query.add(LuceneUtils.longRangeQuery(FIELD_SIZE, params.getMinSize(), params.getMaxSize()), Occur.MUST);
        }

        if (params.getMinSlaves() != null || params.getMaxSlaves() != null) {
            query.add(LuceneUtils.intRangeQuery(FIELD_SLAVES_NBR, params.getMinSlaves(), params.getMaxSlaves()), Occur.MUST);
        }

        if (params.getName() != null) {
            if (!LuceneUtils.validWildcards(params.getName())) {
                throw new IllegalArgumentException("Wildcards in the first three chars not allowed.");
            }
            query.add(LuceneUtils.analyze(FIELD_NAME, params.getName()), Occur.MUST);
        }
        if (params.getExact() != null) {
            if (!LuceneUtils.validWildcards(params.getExact())) {
                throw new IllegalArgumentException("Wildcards in the first three chars not allowed.");
            }
            query.add(new WildcardQuery(new Term(FIELD_FULL_NAME,
                    LuceneUtils.escapeWildcardEscapeChar(params.getExact()))), Occur.MUST);
        }
        if (params.getRegex() != null) {
            try {
                query.add(new JavaRegexQuery(FIELD_FULL_PATH, params.getRegex()), Occur.MUST);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regular expression: " + e.getDescription()
                        + (e.getIndex() >= 0 ? " near index " + e.getIndex() : ""), e);
            }
        }
        if (params.getEndsWith() != null) {
            query.add(new PrefixQuery(new Term(FIELD_FULL_NAME_REVERSE,
                    new StringBuilder(params.getEndsWith()).reverse().toString())), Occur.MUST);
        }

        // Add any query terms from extensions
        for (QueryTermExtensionInterface queryExtension : _queryExtensions) {
            queryExtension.addQueryTerms(query, params);
        }

        return query.build();
    }

    /**
     * Sort for {@link #advancedFind}: random when the order is null, otherwise on the given field
     * (case insensitive) then on fullPath, reversed when the order is true.
     */
    static Sort buildSort(AdvancedSearchParams params) {
        Boolean reverse = params.getSortOrder();
        if (reverse == null) {
            return new Sort(new SortField("random", new RandomOrderComparatorSource()));
        }
        SortField fullPath = new SortField(FIELD_FULL_PATH, SortField.Type.STRING, reverse);
        String field = params.getSortField();
        if (field == null) {
            return new Sort(fullPath);
        }
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "lastmodified" -> new Sort(new SortField(FIELD_LASTMODIFIED, SortField.Type.LONG, reverse), fullPath);
            case "size" -> new Sort(new SortField(FIELD_SIZE, SortField.Type.LONG, reverse), fullPath);
            case "nbrofslaves" -> new Sort(new SortField(FIELD_SLAVES_NBR, SortField.Type.INT, reverse), fullPath);
            case "parentpath" -> new Sort(new SortField(FIELD_PARENT_PATH, SortField.Type.STRING, reverse), fullPath);
            case "owner" -> new Sort(new SortField(FIELD_OWNER, SortField.Type.STRING, reverse), fullPath);
            case "group" -> new Sort(new SortField(FIELD_GROUP, SortField.Type.STRING, reverse), fullPath);
            case "type" -> new Sort(new SortField(FIELD_TYPE, SortField.Type.STRING, reverse), fullPath);
            default -> new Sort(fullPath);
        };
    }

    /**
     * @param startNode The dir where the search will begin.
     * @param params    Search options.
     */
    public Map<String, String> advancedFind(DirectoryHandle startNode, AdvancedSearchParams params, String caller)
            throws IndexException, IllegalArgumentException {
        return advancedFind(startNode.getPath(), params, caller);
    }

    Map<String, String> advancedFind(String startPath, AdvancedSearchParams params, String caller)
            throws IndexException, IllegalArgumentException {
        Query query = buildAdvancedQuery(startPath, params);
        Sort sort = buildSort(params);
        int limit = params.getLimit() != null ? params.getLimit() : _maxHitsNumber;

        logger.debug("{} query: {}", caller, query);

        Map<String, String> inodes = new LinkedHashMap<>();
        try {
            SearcherManager searcherManager = searcherManager();
            IndexSearcher iSearcher = acquireFreshSearcher(searcherManager);
            try {
                if (limit == 0) {
                    limit = iSearcher.count(query);
                    if (limit == 0) {
                        return inodes;
                    }
                    logger.debug("Found {} inode match(es) in the index, using this as limit.", limit);
                }
                TopDocs topDocs = iSearcher.search(query, limit, sort, false);
                StoredFields storedFields = iSearcher.storedFields();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document doc = storedFields.document(scoreDoc.doc, ADVANCED_SEARCH_FIELDS);
                    inodes.put(doc.get(FIELD_FULL_PATH), doc.get(FIELD_TYPE));
                }
            } finally {
                searcherManager.release(iSearcher);
            }
            return inodes;
        } catch (RuntimeException | IOException e) {
            logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
            throw new IndexException("Unable to search the index", e);
        }
    }

    /**
     * @param startNode The dir where the search will begin.
     * @param text      The text to be searched.
     * @param inodeType If you are searching for a File, Dir or both.
     */
    public Set<String> findInode(DirectoryHandle startNode, String text, InodeType inodeType) throws IndexException {
        return findInode(startNode.getPath(), text, inodeType);
    }

    Set<String> findInode(String startPath, String text, InodeType inodeType) throws IndexException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        if (!startPath.equals(VirtualFileSystem.separator)) {
            builder.add(new PrefixQuery(new Term(FIELD_PARENT_PATH, startPath + VirtualFileSystem.separator)), Occur.MUST);
        }
        try {
            builder.add(LuceneUtils.analyze(FIELD_NAME, text), Occur.MUST);
        } catch (TooComplexToDeterminizeException e) {
            throw tooComplex(e);
        }
        if (inodeType == InodeType.DIRECTORY) {
            builder.add(QUERY_DIRECTORY, Occur.MUST);
        } else if (inodeType == InodeType.FILE) {
            builder.add(QUERY_FILE, Occur.MUST);
        }
        Query query = builder.build();
        logger.debug("Query: {}", query);

        Set<String> inodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try {
            SearcherManager searcherManager = searcherManager();
            IndexSearcher iSearcher = acquireFreshSearcher(searcherManager);
            try {
                int limit = _maxHitsNumber > 0 ? _maxHitsNumber : iSearcher.count(query);
                if (limit == 0) {
                    return inodes;
                }
                TopDocs topDocs = iSearcher.search(query, limit);
                StoredFields storedFields = iSearcher.storedFields();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    inodes.add(storedFields.document(scoreDoc.doc, SIMPLE_SEARCH_FIELDS).get(FIELD_FULL_PATH));
                }
            } finally {
                searcherManager.release(iSearcher);
            }
            return inodes;
        } catch (RuntimeException | IOException e) {
            logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
            throw new IndexException("Unable to search the index", e);
        }
    }

    /**
     * Copies the files of the latest commit to the given directory. A commit is made first and
     * the commit point is protected by a {@link SnapshotDeletionPolicy} while it is copied, so the
     * backup is a consistent index even though the writer keeps on indexing meanwhile.
     *
     * @param target the backup directory, created if needed
     * @return false if no backup was made because the index is being, or must be, rebuilt
     */
    boolean backupTo(Path target) throws IOException {
        synchronized (_lock) {
            if (_rebuilding || _rebuildRequired) {
                // the index is empty or partial, it must not replace a good backup
                return false;
            }
            IndexWriter writer = writer();
            writer.commit();
            IndexCommit snapshot = _snapshotPolicy.snapshot();
            try {
                Files.createDirectories(target);
                Collection<String> files = snapshot.getFileNames();
                try (Directory backup = FSDirectory.open(target)) {
                    for (String file : files) {
                        backup.copyFrom(_storage, file, file, IOContext.DEFAULT);
                    }
                    backup.sync(files);
                    backup.syncMetaData();
                }
            } finally {
                _snapshotPolicy.release(snapshot);
                writer.deleteUnusedFiles();
            }
            return true;
        }
    }

    /**
     * This method returns a Map containing information about the index engine.<br>
     * Right now this Map contains the info bellow:
     * <ul>
     * <li>Storage backend (key => "backend")</li>
     * <li>Number of inodes (key => "inodes")</li>
     * <li>Number of deleted inodes not merged away yet (key => "deleted inodes")</li>
     * <li>Amount of buffered documents (key => "cached inodes")</li>
     * <li>Amount of used memory (key => "ram usage")</li>
     * <li>Maximum search hits (key => "max hits")</li>
     * <li>The date of the last periodic commit (key => "last optimization")</li>
     * <li>The date of the last backup (key => "last backup")</li>
     * <li>The size in disk of the index (key => "size")</li>
     * <li>Whether the index must be or is being rebuilt (key => "rebuild", only when relevant)</li>
     * </ul>
     */
    public Map<String, String> getStatus() {
        Map<String, String> status = new LinkedHashMap<>();

        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG);
        status.put("backend", "Apache Lucene " + Version.LATEST + " (https://lucene.apache.org)");

        IndexWriter writer = _iWriter;
        SearcherManager searcherManager = _searcherManager;
        if (writer != null && searcherManager != null) {
            try {
                IndexSearcher iSearcher = acquireFreshSearcher(searcherManager);
                try {
                    status.put("inodes", String.valueOf(iSearcher.getIndexReader().numDocs()));
                    status.put("deleted inodes", String.valueOf(iSearcher.getIndexReader().numDeletedDocs()));
                } finally {
                    searcherManager.release(iSearcher);
                }
                status.put("cached inodes", String.valueOf(writer.numRamDocs()));
                status.put("ram usage", Bytes.formatBytes(writer.ramBytesUsed()));
            } catch (RuntimeException | IOException e) {
                logger.error("Error reading the index status", e);
            }
        }
        status.put("max hits", String.valueOf(_maxHitsNumber));
        if (_maintenanceThread != null) {
            status.put("last optimization", df.format(new Date(_maintenanceThread.getLastOptimizationTime())));
        }
        if (_backupThread != null) {
            status.put("last backup", df.format(new Date(_backupThread.getLastBackup())));
        }

        Directory storage = _storage;
        if (storage != null) {
            long size = 0L;
            try {
                for (String file : storage.listAll()) {
                    try {
                        size += storage.fileLength(file);
                    } catch (IOException e) {
                        // file deleted meanwhile
                    }
                }
                status.put("size", Bytes.formatBytes(size));
            } catch (IOException e) {
                logger.error("IOException getting size of index dir", e);
            }
        }

        RebuildProgress progress = _rebuildProgress;
        if (_rebuilding) {
            status.put("rebuild", "in progress" + (progress == null ? "" : ", " + progress.describe()));
        } else if (_rebuildRequired) {
            status.put("rebuild", "required, run SITE REBUILDINDEX" + (_movedAsideIndex == null
                    ? " (a previous rebuild did not complete)"
                    : " (unreadable index moved to " + _movedAsideIndex + ")"));
        }

        return status;
    }

    protected Directory getStorage() {
        return _storage;
    }

    protected IndexWriter getWriter() {
        return _iWriter;
    }

    /**
     * Counts the inodes walked by a rebuild and logs the progress every {@link #LOG_INTERVAL_NANOS}.
     * Only the rebuild thread calls {@link #inodeWalked()}.
     */
    static final class RebuildProgress {
        static final long LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

        private final LongSupplier _nanoClock;
        private final long _start;
        private long _lastLog;
        private volatile long _inodes;

        RebuildProgress(LongSupplier nanoClock) {
            _nanoClock = nanoClock;
            _start = nanoClock.getAsLong();
            _lastLog = _start;
        }

        /**
         * @return true if the progress was logged
         */
        boolean inodeWalked() {
            _inodes++;
            long now = _nanoClock.getAsLong();
            if (now - _lastLog < LOG_INTERVAL_NANOS) {
                return false;
            }
            _lastLog = now;
            logger.info("Index rebuild in progress: {}", describe(now));
            return true;
        }

        long getInodes() {
            return _inodes;
        }

        String describe() {
            return describe(_nanoClock.getAsLong());
        }

        private String describe(long now) {
            long inodes = _inodes;
            long elapsedMillis = Math.max(TimeUnit.NANOSECONDS.toMillis(now - _start), 0);
            long seconds = elapsedMillis / 1000;
            long rate = elapsedMillis == 0 ? inodes : inodes * 1000 / elapsedMillis;
            return String.format(Locale.ROOT, "%d inodes indexed in %dh%02dm%02ds (%d inodes/s)",
                    inodes, seconds / 3600, (seconds / 60) % 60, seconds % 60, rate);
        }
    }

    /**
     * Collects the (global) ids of all matching documents.
     */
    private static final class DocIdCollectorManager
            implements CollectorManager<DocIdCollectorManager.DocIdCollector, List<Integer>> {

        static final class DocIdCollector extends SimpleCollector {
            private final List<Integer> _docIds = new ArrayList<>();
            private int _docBase;

            @Override
            protected void doSetNextReader(LeafReaderContext context) {
                _docBase = context.docBase;
            }

            @Override
            public void collect(int doc) {
                _docIds.add(_docBase + doc);
            }

            @Override
            public ScoreMode scoreMode() {
                return ScoreMode.COMPLETE_NO_SCORES;
            }
        }

        @Override
        public DocIdCollector newCollector() {
            return new DocIdCollector();
        }

        @Override
        public List<Integer> reduce(Collection<DocIdCollector> collectors) {
            List<Integer> docIds = new ArrayList<>();
            for (DocIdCollector collector : collectors) {
                docIds.addAll(collector._docIds);
            }
            return docIds;
        }
    }

    /**
     * Sorts the hits in a random order: every document gets a pseudo random key derived from a
     * seed drawn for each query.
     */
    static final class RandomOrderComparatorSource extends FieldComparatorSource {
        private static final SecureRandom RANDOM = new SecureRandom();

        @Override
        public FieldComparator<?> newComparator(String fieldname, int numHits, Pruning pruning, boolean reversed) {
            return new RandomOrderFieldComparator(numHits, RANDOM.nextLong());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RandomOrderComparatorSource;
        }

        @Override
        public int hashCode() {
            return RandomOrderComparatorSource.class.hashCode();
        }
    }

    /**
     * Custom FieldComparator to get a random result from index
     */
    static final class RandomOrderFieldComparator extends FieldComparator<Long> {
        private final long[] _values;
        private final long _seed;
        private long _bottom;

        RandomOrderFieldComparator(int numHits, long seed) {
            _values = new long[numHits];
            _seed = seed;
        }

        private long key(int globalDoc) {
            // SplitMix64 finalizer
            long z = _seed + globalDoc * 0x9E3779B97F4A7C15L;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        @Override
        public int compare(int slot1, int slot2) {
            return Long.compare(_values[slot1], _values[slot2]);
        }

        @Override
        public void setTopValue(Long value) {
        }

        @Override
        public Long value(int slot) {
            return _values[slot];
        }

        @Override
        public LeafFieldComparator getLeafComparator(LeafReaderContext context) {
            final int docBase = context.docBase;
            return new LeafFieldComparator() {
                @Override
                public void setBottom(int slot) {
                    _bottom = _values[slot];
                }

                @Override
                public int compareBottom(int doc) {
                    return Long.compare(_bottom, key(docBase + doc));
                }

                @Override
                public int compareTop(int doc) {
                    return 0;
                }

                @Override
                public void copy(int slot, int doc) {
                    _values[slot] = key(docBase + doc);
                }

                @Override
                public void setScorer(Scorable scorer) {
                }
            };
        }
    }

    /**
     * Hook ran by the JVM before shutting down itself completely. This hook
     * saves the index state to keep it usable the next time you start DrFTPD.
     */
    private final class IndexShutdownHookRunnable implements Runnable {
        public void run() {
            _backupThread.stopBackup();
            _maintenanceThread.stopMaintenance();

            // obtaining the objects' lock.
            // doing that we ensure that no operations are running while closing the streams.
            synchronized (_maintenanceThread) {
                _maintenanceThread.notify();
            }
            synchronized (_backupThread) {
                _backupThread.notify();
            }

            while (_maintenanceThread.isAlive() || _backupThread.isAlive()) {
                try {
                    logger.debug("Waiting for the index maintenance threads to die...");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }

            logger.debug("Saving index...");
            closeAll();
        }
    }
}
