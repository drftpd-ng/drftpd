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
import java.text.SimpleDateFormat;
import java.util.Arrays;
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
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Hit;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
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
 * Implementation of an Index engine based on <a
 * href="http://lucene.apache.org">Apache Lucene</a>
 * 
 * @author fr0w
 * @version $Id$
 */
public class LuceneEngine implements IndexEngineInterface {
	private static final Logger logger = Logger.getLogger(LuceneEngine.class);

	private static final Analyzer ANALYZER = new AlphanumericalAnalyzer();
	private static final String INDEX_DIR = "index";
	private static final String BACKUP_DIR = "index.bkp";
	private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");

	private Directory _storage;
	private IndexWriter _iWriter;
	private IndexReader _iReader;
	private IndexSearcher _iSeacher;

	private TermQuery _dirQuery;
	private TermQuery _fileQuery;

	private int _maxHitsNumber;
	private int _maxDocsBuffer;

	private boolean _nativeLocking;

	private int _optimizeInterval;
	private int _updateSearcherInterval;
	private long _lastOptimization;
	private long _lastSearcherCreation;

	private boolean _stopFlag = false;
	private IndexMaintenanceThread _maintenanceThread;

	private boolean _backupRunning = false;
	private int _backupInterval;
	private int _maxNumberBackup;
	private long _lastBackup;
	private IndexBackupThread _backupThread;

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

		reload();

		openStreams();
		initializeQueries();

		Runtime.getRuntime().addShutdownHook(new Thread(new IndexShutdownHookRunnable(), "IndexSaverThread"));

		_maintenanceThread = new IndexMaintenanceThread();
		_maintenanceThread.start();

		_backupThread = new IndexBackupThread();
		_backupThread.start();
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

			_iWriter = new IndexWriter(_storage, true, ANALYZER);
			_iSeacher = new IndexSearcher(_storage);
			_iReader = IndexReader.open(_storage);

			_iWriter.setMaxBufferedDocs(_maxDocsBuffer);
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
		_maxDocsBuffer = Integer.parseInt(cfg.getProperty("maxdocs_buffer", "60"));

		// in minutes, convert'em!
		_optimizeInterval = Integer.parseInt(cfg.getProperty("optimize_interval", "15")) * 60 * 1000;
		_updateSearcherInterval = Integer.parseInt(cfg.getProperty("searchupdate_interval", "15")) * 60 * 1000;

		_nativeLocking = cfg.getProperty("native_locking", "true").equals("true");

		// in minutes, convert it!
		_backupInterval = Integer.parseInt(cfg.getProperty("backup_interval", "120")) * 60 * 1000;
		_maxNumberBackup = Integer.parseInt(cfg.getProperty("max_backups", "2"));
	}

	/**
	 * Saves some resources.<br>
	 * Since this queries are used all the time creating them only once is a
	 * good practice.
	 */
	private void initializeQueries() {
		_dirQuery = new TermQuery(new Term("type", "d"));
		_fileQuery = new TermQuery(new Term("type", "f"));
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

		doc.add(new Field("path", inode.getPath(), Field.Store.YES, Field.Index.TOKENIZED));
		doc.add(new Field("owner", inode.getUsername(), Field.Store.YES, Field.Index.UN_TOKENIZED));
		doc.add(new Field("group", inode.getGroup(), Field.Store.YES, Field.Index.UN_TOKENIZED));
		doc.add(new Field("type", inodeType.toString().toLowerCase().substring(0, 1), Field.Store.YES, Field.Index.UN_TOKENIZED));
		doc.add(new Field("size", String.valueOf(inode.getSize()), Field.Store.YES, Field.Index.UN_TOKENIZED));

		if (inodeType == InodeType.FILE) {
			doc.add(new Field("slaves", ((FileHandle) inode).getSlaveNames().toString(), Field.Store.YES, Field.Index.TOKENIZED));
		}

		return doc;
	}

	/**
	 * Shortcut to create Lucene Terms from a given inode.
	 * 
	 * @param inode
	 */
	private Term makeTermFromInode(InodeHandle inode) {
		return new Term("path", inode.getPath());
	}

	/**
	 * Adds an inode to the Index.
	 */
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

	/**
	 * Deletes an Inode from the Index.
	 */
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

	/**
	 * Updates an Inode information. (Renaming, etc...)
	 */
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

	/**
	 * Forces the Index to be saved. Simply calls IndexWriter.flush().
	 */
	public void commit() throws IndexException {
		try {
			_iWriter.flush();
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to commit the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to commit the index", e);
		}
	}

	/**
	 * Removes ALL the content from the Index and recurse through the site
	 * recreating the index.
	 */
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

			Hits hits = iSearcher.search(query);
			logger.debug("Query: " + query);

			for (Iterator<Hit> iter = hits.iterator(); iter.hasNext();) {
				Hit hit = iter.next();
				inodes.add(hit.get("path"));

				if (inodes.size() == _maxHitsNumber) {
					// search truncated.
					break;
				}
			}

			return inodes;
		} catch (CorruptIndexException e) {
			throw new IndexException("Unable to search the index", e);
		} catch (IOException e) {
			throw new IndexException("Unable to search the index", e);
		}
	}

	/**
	 * Parses the path removing unwanted stuff from it.
	 * 
	 * @param path
	 */
	private Query analyzePath(String path) {
		TokenStream ts = ANALYZER.tokenStream("path", new StringReader(path));
		Token t = null;

		BooleanQuery bQuery = new BooleanQuery();
		WildcardQuery wQuery = null;

		Set<String> set = new HashSet<String>(); // avoids repeated terms.

		while (true) {
			try {
				t = ts.next();
			} catch (IOException e) {
				t = null;
			}

			if (t == null) {
				break; // EOS
			}

			set.add(t.termText());
		}

		Iterator<String> iter = set.iterator();
		while (iter.hasNext()) {
			wQuery = new WildcardQuery(new Term("path", iter.next()));
			bQuery.add(wQuery, Occur.MUST);
		}

		return bQuery;
	}

	public Map<String, String> getStatus() {
		Map<String, String> status = new HashMap<String, String>();

		DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG);
		String lastOp = df.format(new Date(_lastOptimization));
		String lastSearch = df.format(new Date(_lastSearcherCreation));
		String lastBackup = df.format(new Date(_lastBackup));

		status.put("backend", "Apache Lucene (http://lucene.apache.org)");
		status.put("inodes", String.valueOf(_iWriter.docCount()));
		status.put("last optimization", lastOp);
		status.put("last backup", lastBackup);
		status.put("search engine last update", lastSearch);
		status.put("max hits", String.valueOf(_maxHitsNumber));
		status.put("cached inodes", String.valueOf(_iWriter.numRamDocs()));
		status.put("cached memory", Bytes.formatBytes(_iWriter.ramSizeInBytes()));

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

	/**
	 * Hook ran by the JVM before shutting down itself completely. This hook
	 * saves the index state to keep it usable the next time you start DrFTPd.
	 */
	private final class IndexShutdownHookRunnable implements Runnable {
		public void run() {
			_stopFlag = true; // make maintenance thread stop.

			// obtaining the objects' lock.
			// doing that we ensure that no operations are running while closing
			// the streams.
			synchronized (_maintenanceThread) {
				_maintenanceThread.notify();
			}
			synchronized (_backupThread) {
				_backupThread.notify();
			}

			while (_maintenanceThread.isAlive() || _backupThread.isAlive()) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}

			logger.debug("Saving index...");
			closeAll();
		}
	}

	/**
	 * Optimizes and update the search engine.
	 * 
	 * @throws RuntimeException
	 *             If a problem occurs while executing the maintenance.
	 */
	private final class IndexMaintenanceThread extends Thread {
		private long _currentTime;
		private int _minDelay;

		public IndexMaintenanceThread() {
			setName("IndexMaintenanceThread");

			_lastOptimization = System.currentTimeMillis();
			_lastSearcherCreation = System.currentTimeMillis();
		}

		public void run() {
			while (true) {
				_currentTime = System.currentTimeMillis();

				try {
					if (_stopFlag) {
						break;
					}

					_minDelay = Math.min(_optimizeInterval, _updateSearcherInterval);

					if (_currentTime >= _lastOptimization + _optimizeInterval) {
						_iWriter.optimize();
						_iWriter.flush();
						_lastOptimization = _currentTime;

						logger.debug("Index was optimized successfully.");
					}

					if (_currentTime >= _lastSearcherCreation + _updateSearcherInterval) {
						logger.debug("Creating a new IndexSearcher.");

						IndexSearcher oldSearcher = _iSeacher;
						IndexSearcher newSeacher = new IndexSearcher(_storage);

						// locking here so nobody can touch it for now.
						synchronized (oldSearcher) {
							// replacing old instance
							_iSeacher = newSeacher;

							// closing old instance.
							oldSearcher.close();
						}

						_lastSearcherCreation = _currentTime;

						logger.debug("Search engine updated successfully.");
					}

					// obtaining the object monitor's.
					synchronized (this) {
						wait(_minDelay);
					}
				} catch (InterruptedException e) {
					continue;
				} catch (CorruptIndexException e) {
					throw new IllegalStateException("Corrupt index, couldn't run periodical maintenance, that's bad!", e);
				} catch (IOException e) {
					throw new IllegalStateException("Corrupt index, couldn't run periodical maintenance, that's bad!", e);
				}
			}
		}
	}

	/**
	 * Executes backup operations on the index.
	 */
	private final class IndexBackupThread extends Thread {
		private final File _bkpHome;

		public IndexBackupThread() {
			setName("IndexBackupThread");
			_bkpHome = new File(BACKUP_DIR);
		}

		public void run() {
			String[] backups;
			int x = 0;

			while (true) {
				if (_stopFlag) {
					break;
				}

				// store a limited amount of backups.
				// the code bellow remove older backups.
				backups = _bkpHome.list();
				Arrays.sort(backups, String.CASE_INSENSITIVE_ORDER);
				for (x = 0; x < backups.length; x++) {
					if (_bkpHome.list().length < _maxNumberBackup) {
						break;
					}
					new File(BACKUP_DIR + "/" + backups[x]).deleteRecursive();
				}

				// locking the writer object so that noone can use it.
				// this might be useful.
				synchronized (_iWriter) {
					_backupRunning = true;

					String dateTxt = sdf.format(new Date(System.currentTimeMillis()));
					File f = new File(BACKUP_DIR + "/" + dateTxt);

					try {
						if (!f.mkdirs()) {
							throw new IOException("Impossible to create backup directory, not enough permissions.");
						}

						// creating the destination directory.
						FSDirectory bkpDirectory = FSDirectory.getDirectory(f);
						
						Directory.copy(_storage, bkpDirectory, false);
						logger.debug("A backup of the index was created successfully.");
						_lastBackup = System.currentTimeMillis();
					} catch (IOException e) {
						logger.error(e, e);
					}
				}

				try {
					synchronized (this) {
						_backupRunning = false;
						wait(_backupInterval);
					}
				} catch (InterruptedException e) {
				}
			}
		}
	}
}
