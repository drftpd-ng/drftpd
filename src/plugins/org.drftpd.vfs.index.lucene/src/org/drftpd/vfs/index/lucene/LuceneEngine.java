/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.drftpd.vfs.index.lucene;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.*;
import org.apache.lucene.search.regex.RegexQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.util.Version;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.io.PhysicalFile;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.AdvancedSearchParams.InodeType;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.IndexingVirtualFileSystemListener;
import org.drftpd.vfs.index.lucene.analysis.AlphanumericalAnalyzer;
import org.drftpd.vfs.index.lucene.extensions.IndexDataExtensionInterface;
import org.drftpd.vfs.index.lucene.extensions.QueryTermExtensionInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.util.*;

/**
 * Implementation of an Index engine based on <a href="http://lucene.apache.org">Apache Lucene</a>
 * 
 * This engine reuses {@link Field}s and {@link Document} instances as suggested by
 * <a href="http://wiki.apache.org/lucene-java/ImproveIndexingSpeed">this article</a>
 * in order to have better Indexing performance.
 * 
 * @author fr0w
 * @version $Id$
 */
public class LuceneEngine implements IndexEngineInterface {
	private static final Logger logger = LogManager.getLogger(LuceneEngine.class);

	private static final String EXCEPTION_OCCURED_WHILE_SEARCHING = "An exception occured while indexing, check stack trace";

	protected static final Analyzer ANALYZER = new AlphanumericalAnalyzer();
	protected static final String INDEX_DIR = "userdata/index";

	private static final Document INDEX_DOCUMENT = new Document();

	private static final Field FIELD_NAME = new Field("name", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final Field FIELD_FULL_NAME = new Field("fullName", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_FULL_NAME_REVERSE = new Field("fullNameReverse", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_PARENT_PATH = new Field("parentPath", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_FULL_PATH = new Field("fullPath", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_OWNER = new Field("owner", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_GROUP = new Field("group", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_TYPE = new Field("type", "", Field.Store.YES, Field.Index.NOT_ANALYZED);
	private static final Field FIELD_SLAVES = new Field("slaves", "", Field.Store.YES, Field.Index.ANALYZED);
	private static final NumericField FIELD_SLAVES_NBR = new NumericField("nbrOfSlaves", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_LASTMODIFIED = new NumericField("lastModified", Field.Store.YES, Boolean.TRUE);
	private static final NumericField FIELD_SIZE = new NumericField("size", Field.Store.YES, Boolean.TRUE);

	private static final Field[] FIELDS = new Field[] {
		FIELD_NAME, FIELD_FULL_NAME, FIELD_FULL_NAME_REVERSE, FIELD_PARENT_PATH, FIELD_FULL_PATH, FIELD_OWNER, FIELD_GROUP, FIELD_TYPE, FIELD_SLAVES
	};
	private static final NumericField[] NUMERICFIELDS = new NumericField[] {
		FIELD_SLAVES_NBR, FIELD_LASTMODIFIED, FIELD_SIZE
	};

	static {
		for (Field field : FIELDS) {
			INDEX_DOCUMENT.add(field);
		}
		for (NumericField field : NUMERICFIELDS) {
			INDEX_DOCUMENT.add(field);
		}
	}

	private Directory _storage;
	private IndexWriter _iWriter;

	private static final TermQuery QUERY_DIRECTORY = new TermQuery(new Term("type", "d"));
	private static final TermQuery QUERY_FILE = new TermQuery(new Term("type", "f"));

	private static final Term TERM_NAME = new Term("name", "");
	private static final Term TERM_FULL_NAME = new Term("fullName", "");
	private static final Term TERM_FULL_NAME_REVERSE = new Term("fullNameReverse", "");
	private static final Term TERM_PARENT = new Term("parentPath", "");
	private static final Term TERM_FULL = new Term("fullPath", "");

	private static final Term TERM_OWNER = new Term("owner", "");
	private static final Term TERM_GROUP = new Term("group", "");
	private static final Term TERM_SLAVES = new Term("slaves", "");
	private static final SimpleSearchFieldSelector SIMPLE_FIELD_SELECTOR = new SimpleSearchFieldSelector();
	private static final AdvancedSearchFieldSelector ADVANCED_FIELD_SELECTOR = new AdvancedSearchFieldSelector();

	private Sort SORT = new Sort();

	private int _maxHitsNumber;
	private int _maxDocsBuffer;
	private int _maxRAMBufferSize;

	private boolean _nativeLocking;

	private LuceneMaintenanceThread _maintenanceThread;
	private LuceneBackupThread _backupThread;

	private IndexingVirtualFileSystemListener _listener;
	private boolean _rebuilding;
	
	private List<IndexDataExtensionInterface> _dataExtensions = new ArrayList<>();
	private List<QueryTermExtensionInterface> _queryExtensions = new ArrayList<>();

	/**
	 * Creates all the needed resources for the Index to work.
	 * <ul>
	 * <li>IndexSearcher / IndexWriter</li>
	 * <li>Reads <i>conf/plugins/lucene.conf</i> to grab some tweaking
	 * settings, if this file is not found, a default values are loaded.</li>
	 * <li>Adds a Shutdown Hook to save the index while closing DrFTPd</li>
	 * <li>Creates the Maintenance thread.(takes cares of updating the search
	 * engine and optimizing)</li>
	 * </ul>
	 */
	public void init() throws IndexException {
		logger.debug("Initializing Index");

		// Load index data extensions
		try {
			List<IndexDataExtensionInterface> loadedDataExtensions =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.vfs.index.lucene", "IndexData", "Class");
			for (IndexDataExtensionInterface dataExtension : loadedDataExtensions) {
				dataExtension.initializeFields(INDEX_DOCUMENT);
				_dataExtensions.add(dataExtension);
                logger.debug("Loading lucene index data extension from plugin {}", CommonPluginUtils.getPluginIdForObject(dataExtension));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.vfs.index.lucene extension point 'IndexData', possibly the "+
					"org.drftpd.vfs.index.lucene extension point definition has changed in the plugin.xml",e);
		}
		
		// Load query term extensions
		try {
			List<QueryTermExtensionInterface> loadedQueryExtensions =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.vfs.index.lucene", "QueryTerm", "Class");
			for (QueryTermExtensionInterface queryExtension : loadedQueryExtensions) {
				_queryExtensions.add(queryExtension);
                logger.debug("Loading lucene query term extension from plugin {}", CommonPluginUtils.getPluginIdForObject(queryExtension));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.vfs.index.lucene extension point 'QueryTerm', possibly the "+
					"org.drftpd.vfs.index.lucene extension point definition has changed in the plugin.xml",e);
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
	}

	private void createThreads() {
		_maintenanceThread = new LuceneMaintenanceThread();
		_backupThread = new LuceneBackupThread();
	}

	/**
	 * Opens all the needed streams that the engine needs to work properly.
	 * 
	 * @throws IndexException
	 */
	private void openStreams() throws IndexException {
		try {
			if (_nativeLocking) {
				_storage = FSDirectory.open(new File(INDEX_DIR), new NativeFSLockFactory(INDEX_DIR));
			} else {
				_storage = FSDirectory.open(new File(INDEX_DIR));
			}

			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_36 ,ANALYZER);
			conf.setMaxBufferedDocs(_maxDocsBuffer);
			conf.setRAMBufferSizeMB(_maxRAMBufferSize);

			_iWriter = new IndexWriter(_storage, conf);
		} catch (IOException e) {
			closeAll();

			throw new IndexException("Unable to initialize the index", e);
		}
	}

	/**
	 * Reads all tweak settings from <i>conf/plugins/lucene.conf</i> if found,
	 * otherwise use default values.
	 */
	private void reload() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("lucene");
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

	/**
	 * Closes all streams ignoring exceptions.
	 */
	private void closeAll() {
		try {
			if (_iWriter != null)
				_iWriter.close();
			if (_storage != null)
				_storage.close();
		} catch (Exception e) {
			logger.error(e, e);
		}

		_iWriter = null;
		_storage = null;
	}

	/**
	 * Shortcut to create Lucene Document from the Inode's data. The fields that
	 * are stored in the index are:
	 * <ul>
	 * <li>name - The name of the inode</li>
	 * <li>fullName - The full name of the inode</li>
	 * <li>fullNameReverse - The full name of the inode in reverse order</li>
	 * <li>parentPath - The full path of the parent inode</li>
	 * <li>fullPath - The full path of the inode</li>
	 * <li>owner - The user who owns the file</li>
	 * <li>group - The group of the user who owns the file</li>
	 * <li>type - File or Directory</li>
	 * <li>slaves - If the inode is a file, then the slaves are stored</li>
	 * <li>lastModified - Timestamp of when the inode was last modified</li>
	 * <li>size - The size of the inode</li>
	 * </ul>
	 * 
	 * @param inode
	 * @throws FileNotFoundException
	 */
	private Document makeDocumentFromInode(ImmutableInodeHandle inode) throws FileNotFoundException {
		InodeType inodeType = inode.isDirectory() ? InodeType.DIRECTORY : InodeType.FILE;

		FIELD_NAME.setValue(inode.getName());
		FIELD_FULL_NAME.setValue(inode.getName());
		FIELD_FULL_NAME_REVERSE.setValue(new StringBuilder(inode.getName()).reverse().toString());
		if (inode.getPath().equals(VirtualFileSystem.separator)) {
			FIELD_PARENT_PATH.setValue("");
		} else {
			FIELD_PARENT_PATH.setValue(inode.getParent().getPath() + VirtualFileSystem.separator);
		}
		if (inode.isDirectory())
			FIELD_FULL_PATH.setValue(inode.getPath() + VirtualFileSystem.separator);
		else
			FIELD_FULL_PATH.setValue(inode.getPath());
		FIELD_OWNER.setValue(inode.getUsername());
		FIELD_GROUP.setValue(inode.getGroup());
		FIELD_TYPE.setValue(inodeType.toString().toLowerCase().substring(0, 1));

		if (inodeType == InodeType.FILE) {
			StringBuilder sb = new StringBuilder();
			for (String slaveName : inode.getSlaveNames()) {
				sb.append(slaveName).append(",");
			}
			FIELD_SLAVES_NBR.setIntValue(inode.getSlaveNames().size());
			FIELD_SLAVES.setValue(sb.toString());
		} else {
			FIELD_SLAVES_NBR.setIntValue(0);
			FIELD_SLAVES.setValue("");
		}

		FIELD_LASTMODIFIED.setLongValue(inode.lastModified());
		FIELD_SIZE.setLongValue(inode.getSize());
		
		// Add data from any extensions
		for (IndexDataExtensionInterface dataExtension: _dataExtensions) {
			dataExtension.addData(INDEX_DOCUMENT, inode);
		}

		return INDEX_DOCUMENT;
	}
	
	private Term makeFullPathTermFromInode(ImmutableInodeHandle inode) {
		if (inode.isDirectory()) {
			return TERM_FULL.createTerm(inode.getPath() + VirtualFileSystem.separator);
		}

		return TERM_FULL.createTerm(inode.getPath());
	}

	private Term makeFullPathTermFromString(String path) {
		return TERM_FULL.createTerm(path);
	}

	private Term makeParentPathTermFromInode(InodeHandle inode) {
		if (inode.isDirectory()) {
			return TERM_PARENT.createTerm(inode.getPath() + VirtualFileSystem.separator);
		}

		return TERM_PARENT.createTerm(inode.getPath());
	}

	private WildcardQuery makeFullNameWildcardQueryFromString(String name) {
		return new WildcardQuery(TERM_FULL_NAME.createTerm(name));
	}

	private RegexQuery makeFullPathRegexQueryFromString(String regex) {
		return new RegexQuery(TERM_FULL.createTerm(regex));
	}

	private PrefixQuery makeFullNameReversePrefixQueryFromString(String name) {
		name = new StringBuilder(name).reverse().toString();
		return new PrefixQuery(TERM_FULL_NAME_REVERSE.createTerm(name));
	}

	private TermQuery makeOwnerTermQueryFromString(String owner) {
		return new TermQuery(TERM_OWNER.createTerm(owner));
	}

	private TermQuery makeGroupTermQueryFromString(String group) {
		return new TermQuery(TERM_GROUP.createTerm(group));
	}

	private void setSortField(boolean order) {
		SORT.setSort(new SortField("fullPath", SortField.STRING, order));
	}

	private void setSortField(String field, int type, boolean order) {
		SORT.setSort(new SortField(field, type, order),
				new SortField("fullPath", SortField.STRING, order));
	}

	private void setSortFieldRandom() {
		SORT.setSort(new SortField(
				"",
				new FieldComparatorSource() {
					@Override
					public FieldComparator<Integer> newComparator(String fieldname, int numHits, int sortPos, boolean reversed) throws IOException {
						return new RandomOrderFieldComparator();
					}
				}
		));
	}

	/* {@inheritDoc} */
	public void addInode(ImmutableInodeHandle inode) throws IndexException {
		try {
			synchronized (INDEX_DOCUMENT) {
				Document doc = makeDocumentFromInode(inode);
				_iWriter.addDocument(doc);
			}
		} catch (FileNotFoundException e) {
            logger.error("Unable to add {} to the index", inode.getPath(), e);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		} catch (RuntimeException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		}
	}

	/* {@inheritDoc} */
	public void deleteInode(ImmutableInodeHandle inode) throws IndexException {
		try {
			_iWriter.deleteDocuments(makeFullPathTermFromInode(inode));
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to delete " + inode.getPath() + " from the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to delete " + inode.getPath() + " from the index", e);
		} catch (RuntimeException e) {
			throw new IndexException("Unable to delete " + inode.getPath() + " from the index", e);
		}
	}

	/* {@inheritDoc} */
	public void updateInode(ImmutableInodeHandle inode) throws IndexException {
		try {
			synchronized (INDEX_DOCUMENT) {
				_iWriter.updateDocument(makeFullPathTermFromInode(inode), makeDocumentFromInode(inode));
			}
		} catch (FileNotFoundException e) {
			logger.error("The inode was here but now it isn't!", e);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to update " + inode.getPath() + " in the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to update " + inode.getPath() + " in the index", e);
		} catch (RuntimeException e) {
			throw new IndexException("Unable to update " + inode.getPath() + " in the index", e);
		}
	}

	/* {@inheritDoc} */
	public void renameInode(ImmutableInodeHandle fromInode, ImmutableInodeHandle toInode) throws IndexException {
		IndexSearcher iSearcher = null;
		IndexReader iReader = null;
		try {
			Term fromInodeTerm = makeFullPathTermFromInode(fromInode);
			synchronized (INDEX_DOCUMENT) {
				_iWriter.updateDocument(fromInodeTerm, makeDocumentFromInode(toInode));
			}
			if (toInode.isDirectory()) {
				PrefixQuery prefixQuery = new PrefixQuery(fromInodeTerm);

				iReader = IndexReader.open(_iWriter, true);
				iSearcher = new IndexSearcher(iReader);

				final BitSet bits = new BitSet(iReader.maxDoc());
				iSearcher.search(prefixQuery, new Collector() {
					private int docBase;

					// ignore scorer
					public void setScorer(Scorer scorer) {
					}

					// accept docs out of order (for a BitSet it doesn't matter)
					public boolean acceptsDocsOutOfOrder() {
						return true;
					}

					public void collect(int doc) {
						bits.set(doc + docBase);
					}

					public void setNextReader(IndexReader reader, int docBase) {
						this.docBase = docBase;
					}
				});

				for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i+1)) {
					Document doc = iSearcher.doc(i, SIMPLE_FIELD_SELECTOR);

					String oldPath = doc.getFieldable(FIELD_FULL_PATH.name()).stringValue();
					String newPath = toInode.getPath() + oldPath.substring(fromInode.getPath().length());
					doc.removeField(FIELD_FULL_PATH.name());
					doc.removeField(FIELD_PARENT_PATH.name());

					synchronized (INDEX_DOCUMENT) {
						FIELD_FULL_PATH.setValue(newPath);
						if (newPath.equals(VirtualFileSystem.separator)) {
							FIELD_PARENT_PATH.setValue("");
						} else {
							FIELD_PARENT_PATH.setValue(VirtualFileSystem.stripLast(newPath) + VirtualFileSystem.separator);
						}
						doc.add(FIELD_FULL_PATH);
						doc.add(FIELD_PARENT_PATH);
						_iWriter.updateDocument(makeFullPathTermFromString(oldPath), doc);
					}
				}
			}
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to rename " + fromInode.getPath() + " to " +
					toInode.getPath() + " in the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to rename " + fromInode.getPath() + " to " +
					toInode.getPath() + " in the index", e);
		} catch (RuntimeException e) {
			throw new IndexException("Unable to rename " + fromInode.getPath() + " to " +
					toInode.getPath() + " in the index", e);
		} finally {
			if (iSearcher != null) {
				try {
					iSearcher.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexSearcher", e);
				}
			}
			if (iReader != null) {
				try {
					iReader.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexReader obtained from the IndexWriter", e);
				}
			}
		}
	}

	/**
	 * {@inheritDoc}
	 * Forces the Index to be saved. Simply calls {@link IndexWriter}.commit();
	 */
	public void commit() throws IndexException {
		try {
			_iWriter.commit();
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to commit the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to commit the index", e);
		} catch (RuntimeException e) {
			throw new IndexException("Unable to commit the index", e);
		}
	}

	/* {@inheritDoc} */
	public void rebuildIndex() throws IndexException, FileNotFoundException {
		if (_rebuilding) {
			throw new IndexException("A previous rebuildindex command is already in progress.");
		}
		_rebuilding = true;

		closeAll();

		PhysicalFile f = new PhysicalFile(INDEX_DIR);
		f.deleteRecursive();

		openStreams();

		try {
			DirectoryHandle root = GlobalContext.getGlobalContext().getRoot();
			root.requestRefresh(true); // Start by adding root inode
			recurseAndBuild(root); // Recursively traverse the VFS and add all inodes
			commit(); // commit the writer so that the searcher can see the new stuff.
		} catch (IndexException e) {
			logger.error("Exception whilst rebuilding lucene index",e);
			throw e;
		} catch (FileNotFoundException e) {
			logger.error("Root directory not found whilst rebuilding lucene index", e);
			throw e;
		} finally {
			_rebuilding = false;
		}
	}

	/**
	 * Inner function called by rebuildIndex().
	 * 
	 * @param dir
	 * 
	 * @throws IndexException
	 */
	private void recurseAndBuild(DirectoryHandle dir) throws IndexException {
		try {
			for (InodeHandle inode : dir.getInodeHandlesUnchecked()) {
				if (inode.isDirectory()) {
					try {
						inode.requestRefresh(true);
						recurseAndBuild((DirectoryHandle) inode);
					} catch (FileNotFoundException e) {
						// Directory no longer present, silently skip
					}
					
				} else if (inode.isFile()) {
					try {
						inode.requestRefresh(true);
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
	 * @param startNode
	 *            The dir where the search will begin.
	 * @param params
	 *            Search options.
	 */
	public Map<String,String> advancedFind(DirectoryHandle startNode, AdvancedSearchParams params)
	throws IndexException, IllegalArgumentException {
		IndexSearcher iSearcher = null;
		IndexReader iReader = null;
		try {
			Map<String,String> inodes = new LinkedHashMap<>();

			BooleanQuery query = new BooleanQuery();

			if (!startNode.getPath().equals(VirtualFileSystem.separator)) {
				PrefixQuery parentQuery = new PrefixQuery(makeParentPathTermFromInode(startNode));
				query.add(parentQuery, Occur.MUST);
			}

			if (params.getInodeType() == InodeType.ANY) {
				/*
				 * The following isnt needed, we simply ignore search for this
				 * field.
				 *
				 * query.add(QUERY_DIRECTORY, Occur.SHOULD);
				 * query.add(QUERY_FILE, Occur.SHOULD);
				 */
			} else if (params.getInodeType() == InodeType.DIRECTORY) {
				query.add(QUERY_DIRECTORY, Occur.MUST);
			} else if (params.getInodeType() == InodeType.FILE) {
				query.add(QUERY_FILE, Occur.MUST);
			}

			if (params.getOwner() != null) {
				query.add(makeOwnerTermQueryFromString(params.getOwner()), Occur.MUST);
			}
			if (params.getGroup() != null) {
				query.add(makeGroupTermQueryFromString(params.getGroup()), Occur.MUST);
			}

			if (!params.getSlaves().isEmpty()) {
				StringBuilder sb = new StringBuilder();
				for (String slaveName : params.getSlaves()) {
					sb.append(slaveName).append(" ");
				}
				Query slaveQuery = LuceneUtils.analyze("slaves", TERM_SLAVES, sb.toString().trim());
				query.add(slaveQuery, Occur.MUST);
			}

			if (params.getMinAge() != null || params.getMaxAge() != null) {
				Query ageQuery = NumericRangeQuery.newLongRange("lastModified",
						params.getMinAge(), params.getMaxAge(), true, true);
				query.add(ageQuery, Occur.MUST);
			}

			if (params.getMinSize() != null || params.getMaxSize() != null) {
				Query sizeQuery = NumericRangeQuery.newLongRange("size",
						params.getMinSize(), params.getMaxSize(), true, true);
				query.add(sizeQuery, Occur.MUST);
			}

			if (params.getMinSlaves() != null || params.getMaxSlaves() != null) {
				Query nbrOfSlavesQuery = NumericRangeQuery.newIntRange("nbrOfSlaves",
						params.getMinSlaves(), params.getMaxSlaves(), true, true);
				query.add(nbrOfSlavesQuery, Occur.MUST);
			}

			if (params.getName() != null) {
				if (!LuceneUtils.validWildcards(params.getName())) {
					throw new IllegalArgumentException("Wildcards in the first three chars not allowed.");
				}
				Query nameQuery = LuceneUtils.analyze("name", TERM_NAME, params.getName());
				query.add(nameQuery, Occur.MUST);
			}
			if (params.getExact() != null) {
				if (!LuceneUtils.validWildcards(params.getExact())) {
					throw new IllegalArgumentException("Wildcards in the first three chars not allowed.");
				}
				query.add(makeFullNameWildcardQueryFromString(params.getExact()), Occur.MUST);
			}
			if (params.getRegex() != null) {
				query.add(makeFullPathRegexQueryFromString(params.getRegex()), Occur.MUST);
			}
			if (params.getEndsWith() != null) {
				query.add(makeFullNameReversePrefixQueryFromString(params.getEndsWith()), Occur.MUST);
			}

			if (params.getSortField() != null && params.getSortOrder() != null) {
				if (params.getSortField().equalsIgnoreCase("lastModified") ||
						params.getSortField().equalsIgnoreCase("size")) {
					setSortField(params.getSortField(), SortField.LONG, params.getSortOrder());
				} else if (params.getSortField().equalsIgnoreCase("nbrOfSlaves")) {
					setSortField(params.getSortField(), SortField.INT, params.getSortOrder());
				} else if (params.getSortField().equalsIgnoreCase("parentPath") ||
						params.getSortField().equalsIgnoreCase("owner") ||
						params.getSortField().equalsIgnoreCase("group") ||
						params.getSortField().equalsIgnoreCase("type")) {
					setSortField(params.getSortField(), SortField.STRING, params.getSortOrder());
				} else {
					setSortField(params.getSortOrder());
				}
			} else if (params.getSortOrder() == null) {
				setSortFieldRandom();
			} else {
				setSortField(params.getSortOrder());
			}

			int limit = _maxHitsNumber;

			if (params.getLimit() != null) {
				limit = params.getLimit();
			}
			
			// Add any query terms from extensions
			for (QueryTermExtensionInterface queryExtension : _queryExtensions) {
				queryExtension.addQueryTerms(query, params);
			}

            logger.debug("Query: {}", query);

			iReader = IndexReader.open(_iWriter, true);
			iSearcher = new IndexSearcher(iReader);
			if (limit == 0) {
				TotalHitCountCollector totalHitCountCollector = new TotalHitCountCollector();
				iSearcher.search(query, totalHitCountCollector);
				limit = totalHitCountCollector.getTotalHits();
				if (limit == 0) {
					return inodes;
				}
                logger.debug("Found {} inode match(es) in the index, using this as limit.", limit);
			}
			TopFieldCollector topFieldCollector = TopFieldCollector.create(SORT, limit, true, false, false, false);
			iSearcher.search(query, topFieldCollector);

			for (ScoreDoc scoreDoc : topFieldCollector.topDocs().scoreDocs) {
				Document doc = iSearcher.doc(scoreDoc.doc, ADVANCED_FIELD_SELECTOR);
				inodes.put(doc.getFieldable("fullPath").stringValue(), doc.getFieldable("type").stringValue());
			}

			return inodes;
		} catch (CorruptIndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} catch (IOException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} catch (RuntimeException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} finally {
			if (iSearcher != null) {
				try {
					iSearcher.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexSearcher", e);
				}
			}
			if (iReader != null) {
				try {
					iReader.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexReader obtained from the IndexWriter", e);
				}
			}
		}
	}

	/**
	 * @param startNode
	 *            The dir where the search will begin.
	 * @param text
	 *            The text to be searched.
	 * @param inodeType
	 *            If you are searching for a File, Dir or both.
	 */
	public Set<String> findInode(DirectoryHandle startNode, String text, InodeType inodeType) throws IndexException {
		IndexSearcher iSearcher = null;
		IndexReader iReader = null;
		try {
			Set<String> inodes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

			BooleanQuery query = new BooleanQuery();

			if (!startNode.getPath().equals(VirtualFileSystem.separator)) {
				PrefixQuery parentQuery = new PrefixQuery(makeParentPathTermFromInode(startNode));
				query.add(parentQuery, Occur.MUST);
			}

			Query nameQuery = LuceneUtils.analyze("name", TERM_NAME, text);
			query.add(nameQuery, Occur.MUST);

			if (inodeType == InodeType.ANY) {
				/*
				 * The following isnt needed, we simply ignore search for this
				 * field.
				 * 
				 * query.add(dirQuery, Occur.SHOULD); query.add(fileQuery,
				 * Occur.SHOULD);
				 */
			} else if (inodeType == InodeType.DIRECTORY) {
				query.add(QUERY_DIRECTORY, Occur.MUST);
			} else if (inodeType == InodeType.FILE) {
				query.add(QUERY_FILE, Occur.MUST);
			}

			iReader = IndexReader.open(_iWriter, true);
			iSearcher = new IndexSearcher(iReader);
			TopScoreDocCollector topScoreDocsCollector = TopScoreDocCollector.create(_maxHitsNumber, false);
			iSearcher.search(query, topScoreDocsCollector);
            logger.debug("Query: {}", query);

			for (ScoreDoc scoreDoc : topScoreDocsCollector.topDocs().scoreDocs) {
				Document doc = iSearcher.doc(scoreDoc.doc, SIMPLE_FIELD_SELECTOR);
				inodes.add(doc.getFieldable("fullPath").stringValue());
			}

			return inodes;
		} catch (CorruptIndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} catch (IOException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} catch (RuntimeException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
			throw new IndexException("Unable to search the index", e);
		} finally {
			if (iSearcher != null) {
				try {
					iSearcher.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexSearcher", e);
				}
			}
			if (iReader != null) {
				try {
					iReader.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexReader obtained from the IndexWriter", e);
				}
			}
		}
	}

	/**
	 * This method returns a Map containing information about the index engine.<br>
	 * Right now this Map contains the info bellow:
	 * <ul>
	 * <li>Number of inodes (key => "inodes")</li>
	 * <li>Storage backend (key => "backend")</li>
	 * <li>Maximum search hits (key => "max hits")</li>
	 * <li>The date of the last optimization (key => "last optimization")</li>
	 * <li>The date of the last backup (key => "last backup")</li>
	 * <li>The date of the last uptade of the search engine (key => "last search engine update")</li>
	 * <li>Amount of cached documents (key => "cached inodes")</li>
	 * <li>Amount of used memory (key => "ram usage")</li>
	 * <li>The size in disk of the index (key => "disk usage")</li>
	 * </ul>
	 */
	public Map<String, String> getStatus() {
		Map<String, String> status = new LinkedHashMap<>();

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG);
		String lastOp = df.format(new Date(_maintenanceThread.getLastOptimizationTime()));
		String lastBackup = df.format(new Date(_backupThread.getLastBackup()));
		status.put("backend", "Apache Lucene (http://lucene.apache.org)");

		try {
			status.put("inodes", String.valueOf(_iWriter.numDocs()));
		} catch (IOException e) {
			logger.error("IOException getting IndexWriter", e);
		}

		IndexReader iReader = null;
		try {
			iReader = IndexReader.open(_iWriter, true);
			status.put("deleted inodes", String.valueOf(iReader.numDeletedDocs()));
		} catch (CorruptIndexException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
		} catch (IOException e) {
			logger.error(EXCEPTION_OCCURED_WHILE_SEARCHING, e);
		} finally {
			if (iReader != null) {
				try {
					iReader.close();
				} catch (IOException e) {
					logger.error("IOException closing IndexReader obtained from the IndexWriter", e);
				}
			}
		}
		
		status.put("cached inodes", String.valueOf(_iWriter.numRamDocs()));
		status.put("max hits", String.valueOf(_maxHitsNumber));
		status.put("last optimization", lastOp);
		status.put("last backup", lastBackup);
		status.put("ram usage", Bytes.formatBytes(_iWriter.ramSizeInBytes()));

		long size = 0L;
		String[] paths;
		try {
			paths = _storage.listAll();
			for (String path : paths) {
				size += new PhysicalFile(INDEX_DIR + "/" + path).length();
			}

			status.put("size", Bytes.formatBytes(size));
		} catch (IOException e) {
			logger.error("IOException getting size of index dir", e);
		}

		return status;
	}

	protected Directory getStorage() {
		return _storage;
	}

	protected IndexWriter getWriter() {
		return _iWriter;
	}
	
	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<IndexDataExtensionInterface> loadedDataExtensions =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.vfs.index.lucene", "IndexData", "Class", event);
			if (!loadedDataExtensions.isEmpty()) {
				List<IndexDataExtensionInterface> clonedDataExtensions = new ArrayList<>(_dataExtensions);
				for (IndexDataExtensionInterface dataExtension : loadedDataExtensions) {
                    logger.debug("Loading lucene index data extension from plugin {}", CommonPluginUtils.getPluginIdForObject(dataExtension));
					synchronized (INDEX_DOCUMENT) {
						dataExtension.initializeFields(INDEX_DOCUMENT);
					}
					clonedDataExtensions.add(dataExtension);
				}
				_dataExtensions = clonedDataExtensions;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.vfs.index.lucene extension point 'IndexData'"
					+ ", possibly the org.drftpd.vfs.index.lucene extension point definition has changed in the plugin.xml",e);
		}
		
		try {
			List<QueryTermExtensionInterface> loadedQueryExtensions =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.vfs.index.lucene", "QueryTerm", "Class", event);
			if (!loadedQueryExtensions.isEmpty()) {
				List<QueryTermExtensionInterface> clonedQueryExtensions = new ArrayList<>(_queryExtensions);
				for (QueryTermExtensionInterface queryExtension : loadedQueryExtensions) {
                    logger.debug("Loading lucene query term extension from plugin {}", CommonPluginUtils.getPluginIdForObject(queryExtension));
					clonedQueryExtensions.add(queryExtension);
				}
				_queryExtensions = clonedQueryExtensions;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.vfs.index.lucene extension point 'QueryTerm'"
					+ ", possibly the org.drftpd.vfs.index.lucene extension point definition has changed in the plugin.xml",e);
		}
	}

	/**
	 * Hook ran by the JVM before shutting down itself completely. This hook
	 * saves the index state to keep it usable the next time you start DrFTPd.
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

	/**
	 * Custom FieldComparator to get a random result from index
	 */
	public static class RandomOrderFieldComparator extends FieldComparator<Integer> {

		private final SecureRandom random = new SecureRandom();

		@Override
		public int compare(int slot1, int slot2) {
			return random.nextInt();
		}

		@Override
		public int compareBottom(int doc) throws IOException {
			return random.nextInt();
		}

		@Override
		public void copy(int slot, int doc) throws IOException {
		}

		@Override
		public void setBottom(int bottom) {
		}

		@Override
		public void setNextReader(IndexReader reader, int docBase) throws IOException {
		}

		@Override
		public Integer value(int slot) {
			return random.nextInt();
		}

	}
}
