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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.AdvancedSearchParams.InodeType;
import org.drftpd.vfs.index.lucene.analysis.AlphanumericalAnalyzer;

import se.mog.io.File;

/**
 * Implementation of an Index engine based o\n <a
 * href="http://lucene.apache.org">Apache Lucene</a>
 * 
 * @author fr0w
 * @version $Id$
 */
public class LuceneEngine implements IndexEngineInterface {
	private static final Logger logger = Logger.getLogger(LuceneEngine.class);

	private static final Analyzer ANALYZER = new AlphanumericalAnalyzer();
	protected static final String INDEX_DIR = "index";

	private Directory _storage;
	private IndexWriter _iWriter;
	private IndexReader _iReader;
	private IndexSearcher _iSeacher;

	private final TermQuery _dirQuery = new TermQuery(new Term("type", "d"));
	private final TermQuery _fileQuery = new TermQuery(new Term("type", "f"));;
	
	private final Term _pathTerm = new Term("path", "/");

	private int _maxHitsNumber;
	private int _maxDocsBuffer;
	private int _maxRAMBufferSize;

	private boolean _nativeLocking;

	private LuceneMaintenanceThread _maintenanceThread;
	private LuceneBackupThread _backupThread;

	/**
	 * Creates all the needed resources for the Index to work.
	 * <ul>
	 * <li>IndexSearcher / IndexWriter / IndexReader</li>
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
				_storage = FSDirectory.getDirectory(INDEX_DIR, new NativeFSLockFactory(INDEX_DIR));
			} else {
				_storage = FSDirectory.getDirectory(INDEX_DIR);
			}

			_iWriter = new IndexWriter(_storage, ANALYZER, new MaxFieldLength(1024)); // TODO make configurable?
			_iSeacher = new IndexSearcher(_storage);
			_iReader = IndexReader.open(_storage, true); // IndexReader will be read-only

			_iWriter.setMaxBufferedDocs(_maxDocsBuffer);
			_iWriter.setRAMBufferSizeMB(_maxRAMBufferSize);
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
		int updateSearcherInterval = Integer.parseInt(cfg.getProperty("searchupdate_interval", "15")) * 60 * 1000;
		_maintenanceThread.setOptimizationInterval(optimizeInterval);
		_maintenanceThread.setSearcherCreationInterval(updateSearcherInterval);
		
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
			if (_iSeacher != null)
				_iSeacher.close();
			if (_iWriter != null)
				_iWriter.close();
			if (_iReader != null)
				_iReader.close();
			if (_storage != null)
				_storage.close();
		} catch (Exception e) {
			logger.error(e, e);
		}

		_iSeacher = null;
		_iWriter = null;
		_iReader = null;
		_storage = null;
	}

	/**
	 * Shortcut to create Lucene Document from the Inode's data. The fields that
	 * are stored in the index are:
	 * <ul>
	 * <li>path</li>
	 * <li>owner - The user who owns the file</li>
	 * <li>group - The group of the user who owns the file</li>
	 * <li>type - File or Directory</li>
	 * <li>size - The size of the inode</li>
	 * <li>slaves - If the inode is a file, then the slaves are stored</li>
	 * </ul>
	 * 
	 * @param inode
	 * @throws FileNotFoundException
	 */
	private Document makeDocumentFromInode(InodeHandle inode) throws FileNotFoundException {
		Document doc = new Document();
		InodeType inodeType = inode.isDirectory() ? InodeType.DIRECTORY : InodeType.FILE;

		doc.add(new Field("path", inode.getPath(), Field.Store.YES, Field.Index.ANALYZED));
		doc.add(new Field("owner", inode.getUsername(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field("group", inode.getGroup(), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field("type", inodeType.toString().toLowerCase().substring(0, 1), Field.Store.YES, Field.Index.NOT_ANALYZED));
		doc.add(new Field("size", String.valueOf(inode.getSize()), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));

		if (inodeType == InodeType.FILE) {
			StringBuffer sb = new StringBuffer();
			for (String slaveName : ((FileHandle) inode).getSlaveNames()) {
				sb.append(slaveName+",");				
			}
			doc.add(new Field("slaves", sb.toString(), Field.Store.YES, Field.Index.ANALYZED));
		}

		return doc;
	}

	/**
	 * Shortcut to create Lucene Terms from a given inode.
	 * 
	 * @param inode
	 */
	private Term makeTermFromInode(InodeHandle inode) {
		return _pathTerm.createTerm(inode.getPath());
	}

	/* {@inheritDoc} */
	public void addInode(InodeHandle inode) throws IndexException {
		try {
			Document doc = makeDocumentFromInode(inode);
			_iWriter.addDocument(doc);
		} catch (FileNotFoundException e) {
			logger.error("Unable to add " + inode.getPath() + " to the index", e);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to add " + inode.getPath() + " to the index", e);
		}
	}

	/* {@inheritDoc} */
	public void deleteInode(InodeHandle inode) throws IndexException {
		Term term = makeTermFromInode(inode);
		try {
			_iWriter.deleteDocuments(term);
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to delete " + inode.getPath() + " to the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to delete " + inode.getPath() + " to the index", e);
		}
	}

	/* {@inheritDoc} */
	public void updateInode(InodeHandle inode) throws IndexException {
		try {
			_iWriter.updateDocument(makeTermFromInode(inode), makeDocumentFromInode(inode));
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to update " + inode.getPath() + " to the index", e);
		} catch (FileNotFoundException e) {
			logger.error("The inode was here but now it isn't!", e);
		} catch (IOException e) {
			throw new IndexException("Unable to update " + inode.getPath() + " to the index", e);
		}
	}
	
	/* {@inheritDoc} */
	public void renameInode(InodeHandle fromInode, InodeHandle toInode) throws IndexException {
		//TODO
	}

	/**
	 * {@inheritDoc}
	 * Forces the Index to be saved. Simply calls IndexWriter.flush().
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
		closeAll();

		File f = new File(INDEX_DIR);
		f.deleteRecursive();

		openStreams();

		try {
			recurseAndBuild(GlobalContext.getGlobalContext().getRoot());
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Inner function called by rebuildIndex().
	 * 
	 * @param dir
	 * @throws FileNotFoundException
	 * @throws IndexException
	 */
	private void recurseAndBuild(DirectoryHandle dir) throws FileNotFoundException, IndexException {
		for (InodeHandle inode : dir.getInodeHandlesUnchecked()) {
			if (inode.isDirectory()) {
				addInode(inode);
				recurseAndBuild((DirectoryHandle) inode);
			} else if (inode.isFile()) {
				addInode(inode);
			}
		}
	}

	/* {@inheritDoc} */
	public Set<String> advancedFind(AdvancedSearchParams params) throws IndexException {
		// TODO Auto-generated method stub
		return null;
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
		try {
			Set<String> inodes = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

			IndexSearcher iSearcher = new IndexSearcher(_storage);
			BooleanQuery query = new BooleanQuery();

			if (!startNode.getPath().equals(VirtualFileSystem.separator)) {
				text = startNode.getPath() + " " + text;
			}

			Query pathQuery = analyzePath(text);
			query.add(pathQuery, Occur.MUST);

			if (inodeType == InodeType.ANY) {
				/*
				 * The following isnt needed, we simply ignore search for this
				 * field.
				 * 
				 * query.add(dirQuery, Occur.SHOULD); query.add(fileQuery,
				 * Occur.SHOULD);
				 */
			} else if (inodeType == InodeType.DIRECTORY) {
				query.add(_dirQuery, Occur.MUST);
			} else if (inodeType == InodeType.FILE) {
				query.add(_fileQuery, Occur.MUST);
			}

			TopDocs topDocs = iSearcher.search(query, _maxHitsNumber);
			logger.debug("Query: " + query);

			for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
				Document doc = iSearcher.doc(scoreDoc.doc, new SimpleSearchFieldSelector());
				inodes.add(doc.getField("path").stringValue());
			}

			return inodes;
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to search the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to search the index", e);
		}
	}

	/**
	 * Parses the path removing unwanted chars from it.
	 * 
	 * @param path
	 */
	private Query analyzePath(String path) {
		TokenStream ts = ANALYZER.tokenStream("path", new StringReader(path));
		Token token = new Token();

		BooleanQuery bQuery = new BooleanQuery();
		WildcardQuery wQuery = null;

		Set<String> set = new HashSet<String>(); // avoids repeated terms.

		while (true) {
			try {
				token = ts.next(token);
			} catch (IOException e) {
				token = null;
				break;
			}
			
			if (token == null) {
				break;
			}
			
			set.add(new String(token.termBuffer(), 0, token.termLength()));
		}

		Iterator<String> iter = set.iterator();
		while (iter.hasNext()) {
			wQuery = new WildcardQuery(_pathTerm.createTerm(iter.next()));
			bQuery.add(wQuery, Occur.MUST);
		}

		return bQuery;
	}

	/**
	 * This method returns a Map containing information about the index engine.<br>
	 * Right now this Map contains the info bellow:
	 * <ul>
	 * <li>Number of inodes (ley => "inodes")</li>
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
		String lastSearch = df.format(new Date(_maintenanceThread.getSearcherCreationTime()));
		String lastBackup = df.format(new Date(_backupThread.getLastBackup()));

		status.put("inodes", String.valueOf(_iWriter.maxDoc()));
		status.put("backend", "Apache Lucene (http://lucene.apache.org)");
		status.put("max hits", String.valueOf(_maxHitsNumber));
		status.put("last optimization", lastOp);
		status.put("last backup", lastBackup);
		status.put("last search engine update", lastSearch);
		status.put("cached inodes", String.valueOf(_iWriter.numRamDocs()));
		status.put("ram usage", Bytes.formatBytes(_iWriter.ramSizeInBytes()));

		long size = 0L;
		String[] paths = null;
		try {
			paths = _storage.list();
			for (String path : paths) {
				size += new File(INDEX_DIR + "/" + path).length();
			}

			status.put("size", Bytes.formatBytes(size));
		} catch (IOException e) {
		}

		return status;
	}

	protected Directory getStorage() {
		return _storage;
	}
	
	protected IndexWriter getWriter() {
		return _iWriter;
	}
	
	protected IndexSearcher getSearcher() {
		return _iSeacher;
	}
	
	protected void setSearcher(IndexSearcher searcher) {
		_iSeacher = searcher;
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
			// doing that we ensure that nothing operations is running while closing the streams.
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
