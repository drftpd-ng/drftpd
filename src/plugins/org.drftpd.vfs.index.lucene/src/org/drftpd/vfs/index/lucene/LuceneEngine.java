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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.regex.RegexQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.util.Version;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.io.PhysicalFile;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.IndexingVirtualFileSystemListener;
import org.drftpd.vfs.index.AdvancedSearchParams.InodeType;
import org.drftpd.vfs.index.lucene.analysis.AlphanumericalAnalyzer;


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
	private static final Logger logger = Logger.getLogger(LuceneEngine.class);

	private static final String EXCEPTION_OCCURED_WHILE_SEARCHING = "An exception occured while indexing, check stack trace";

	private static final Analyzer ANALYZER = new AlphanumericalAnalyzer();
	protected static final String INDEX_DIR = "index";

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

		createThreads();
		reload();

		openStreams();

		Runtime.getRuntime().addShutdownHook(new Thread(new IndexShutdownHookRunnable(), "IndexSaverThread"));
		_maintenanceThread.start();
		_backupThread.start();

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

			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_32 ,ANALYZER);
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
		int interval = Integer.parseInt(cfg.getProperty("backup_interval", "120")) * 60 * 1000;
		int maxNumber = Integer.parseInt(cfg.getProperty("max_backups", "2"));
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

		return INDEX_DOCUMENT;
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
	private Document makeDocumentFromRealInode(InodeHandle inode) throws FileNotFoundException {
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
			for (String slaveName : ((FileHandle) inode).getSlaveNames()) {
				sb.append(slaveName).append(",");
			}
			FIELD_SLAVES_NBR.setIntValue(((FileHandle) inode).getSlaveNames().size());
			FIELD_SLAVES.setValue(sb.toString());
		} else {
			FIELD_SLAVES_NBR.setIntValue(0);
			FIELD_SLAVES.setValue("");
		}

		FIELD_LASTMODIFIED.setLongValue(inode.lastModified());
		FIELD_SIZE.setLongValue(inode.getSize());

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
				new SortField("fullPath", SortField.STRING));
	}

	/* {@inheritDoc} */
	public void addInode(ImmutableInodeHandle inode) throws IndexException {
		try {
			synchronized (INDEX_DOCUMENT) {
				Document doc = makeDocumentFromInode(inode);
				_iWriter.addDocument(doc);
			}
		} catch (FileNotFoundException e) {
			logger.error("Unable to add " + inode.getPath() + " to the index", e);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		} catch (IOException e) {
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
		}
	}

	/* {@inheritDoc} */
	public void rebuildIndex() throws IndexException {
		if (_rebuilding) {
			throw new IndexException("A previous rebuildindex command is already in progress.");
		}
		_rebuilding = true;

		closeAll();

		PhysicalFile f = new PhysicalFile(INDEX_DIR);
		f.deleteRecursive();

		openStreams();

		try {
			recurseAndBuild(GlobalContext.getGlobalContext().getRoot());
			commit(); // commit the writer so that the searcher can see the new stuff.
		} catch (IndexException e) {
			logger.error("Exception whilst rebuilding lucene index",e);
			throw e;
		} finally {
			_rebuilding = false;
		}
	}

	/**
	 * Inner function called by rebuildIndex().
	 * 
	 * @param dir
	 * @throws FileNotFoundException
	 * @throws IndexException
	 */
	private void recurseAndBuild(DirectoryHandle dir) throws IndexException {
		try {
			for (InodeHandle inode : dir.getInodeHandlesUnchecked()) {
				if (inode.isDirectory()) {
					addRealInode(inode);
					recurseAndBuild((DirectoryHandle) inode);
				} else if (inode.isFile()) {
					addRealInode(inode);
				}
			}
		} catch (FileNotFoundException e) {
			// Dir gone, error logged in addInode(inode) call from parent dir,
			// ignore and continue to build index.
		}
	}
	
	private void addRealInode(InodeHandle inode) throws IndexException {
		try {
			synchronized (INDEX_DOCUMENT) {
				Document doc = makeDocumentFromRealInode(inode);
				_iWriter.addDocument(doc);
			}
		} catch (FileNotFoundException e) {
			logger.error("Unable to add " + inode.getPath() + " to the index", e);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
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
			Map<String,String> inodes = new LinkedHashMap<String,String>();

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
				Query slaveQuery = analyze("slaves", TERM_SLAVES, sb.toString().trim());
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
				if (!validWildcards(params.getName())) {
					throw new IllegalArgumentException("Wildcards in the first three chars not allowed.");
				}
				Query nameQuery = analyze("name", TERM_NAME, params.getName());
				query.add(nameQuery, Occur.MUST);
			}
			if (params.getExact() != null) {
				if (!validWildcards(params.getExact())) {
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

			if (params.getSortField() != null) {
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
			}

			if (params.getPrivPathRegex() != null) {
				query.add(makeFullPathRegexQueryFromString(params.getPrivPathRegex()), Occur.MUST_NOT);
			}

			int limit = _maxHitsNumber;

			if (params.getLimit() != null) {
				limit = params.getLimit();
			}

			logger.debug("Query: " + query);

			iReader = IndexReader.open(_iWriter, true);
			iSearcher = new IndexSearcher(iReader);
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

	private boolean validWildcards(String text) {
		int wc1 = text.indexOf("*");
		int wc2 = text.indexOf("?");
		return !((wc1 > 0 && wc1 <= 3) || (wc2 > 0 && wc2 <= 3));
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
			Set<String> inodes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

			BooleanQuery query = new BooleanQuery();

			if (!startNode.getPath().equals(VirtualFileSystem.separator)) {
				PrefixQuery parentQuery = new PrefixQuery(makeParentPathTermFromInode(startNode));
				query.add(parentQuery, Occur.MUST);
			}

			Query nameQuery = analyze("name", TERM_NAME, text);
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
			logger.debug("Query: " + query);

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
	 * Parses the inode name removing unwanted chars from it.
	 * 
	 * @param field
	 * @param term
	 * @param name
	 * @return Query
	 */
	private Query analyze(String field, Term term, String name) {
		TokenStream ts = ANALYZER.tokenStream(field, new StringReader(name));

		BooleanQuery bQuery = new BooleanQuery();
		WildcardQuery wQuery;

		Set<String> tokens = new HashSet<String>(); // avoids repeated terms.

		// get the CharTermAttribute from the TokenStream
		CharTermAttribute termAtt = ts.addAttribute(CharTermAttribute.class);

		try {
			ts.reset();
			while (ts.incrementToken()) {
				tokens.add(termAtt.toString());
			}
			ts.end();
			ts.close();
		} catch (IOException e) {
			logger.error("IOException analyzing string", e);
		}

		for (String text : tokens) {
			wQuery = new WildcardQuery(term.createTerm(text));
			bQuery.add(wQuery, Occur.MUST);
		}

		return bQuery;
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
		Map<String, String> status = new HashMap<String, String>();

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG);
		String lastOp = df.format(new Date(_maintenanceThread.getLastOptimizationTime()));
		String lastBackup = df.format(new Date(_backupThread.getLastBackup()));

		status.put("inodes", String.valueOf(_iWriter.maxDoc()));
		status.put("backend", "Apache Lucene (http://lucene.apache.org)");
		status.put("max hits", String.valueOf(_maxHitsNumber));
		status.put("last optimization", lastOp);
		status.put("last backup", lastBackup);
		status.put("cached inodes", String.valueOf(_iWriter.numRamDocs()));
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
}
