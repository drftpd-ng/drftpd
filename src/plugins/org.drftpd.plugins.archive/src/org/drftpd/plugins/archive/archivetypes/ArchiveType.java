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
package org.drftpd.plugins.archive.archivetypes;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.plugins.archive.DuplicateArchiveException;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.plugins.jobmanager.JobManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author CyBeR
 * @version $Id$
 */
public abstract class ArchiveType {
	private static final Logger logger = Logger.getLogger(ArchiveType.class);

	// Used for: Arching dirs After This ammount of time
	private long _archiveAfter;

	// Current directory being archived
	private DirectoryHandle _directory;

	protected Archive _parent;

	// Current section directory is in.
	protected SectionInterface _section;
	
	// Current .conf loop number we are on.  Used for other archive types to grab extra configureations 
	protected int _confnum;

	protected Set<RemoteSlave> _slaveList;

	// Used For: number of slaves to archive too
	protected int _numOfSlaves;
	
	// Uses For: setting priority vs other archives/jobs
	protected int _priority;
	
	// Used for: archiving only dirs that match this regex form. (Default is .*)
	private String _archiveRegex;
	
	// Used for: moving directory to another folder
	protected DirectoryHandle _archiveToFolder;
	
	// Used for: setting destincation directory ONLY after moving it (more for events)
	private DirectoryHandle _destinationDirectory;
	
	// Used for: a specific type of folder to archive too.  (Alpha/Dated)
	protected String _archiveDirType;
	
	// Check to see if we are going to move the relase after archive
	private boolean _moveRelease;
	
	// Checks to see if we are moving the release only (no slave -> slave archive)
	protected boolean _moveReleaseOnly;
	
	// Used for: how many times to repeat during each cycle
	private int _repeat;

	//  Used for holding all failed directorys during each cycle
	private ArrayList<String> _failedDirs;

	/**
	 * Sets _slaveList, _numOfSlaves, _section, _parent, and _archiveAfter Each
	 * implementing ArchiveType still needs to check/validate these figures to
	 * their specific needs After the constructor finishes, _slaveList is
	 * guaranteed to be non-null, but can still be empty
	 * 
	 * @param archive
	 * @param section
	 * @param p
	 * @param confnum
	 */
	public ArchiveType(Archive archive, SectionInterface section, Properties p, int confnum) {
		_confnum = confnum;
		_parent = archive;
		_section = section;
		setProperties(p);
	}

	/**
	 * Used to determine a list of slaves dynamically during runtime, only gets
	 * called if _slaveList == null
	 * 
	 * @return
	 */
	public abstract Set<RemoteSlave> findDestinationSlaves();

	/**
	 * if the directory is archived by this type's definition, this method
	 * returns true
	 * @throws FileNotFoundException 
	 */
	protected abstract boolean isArchivedDir(DirectoryHandle directory) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException;	

	/*
	 * Returns the string eqivilant for the archive type
	 */
	public abstract String toString();
	
	/*
	 * Returns the current directory being archived
	 */
	public final DirectoryHandle getDirectory() {
		return _directory;
	}
	
	/*
	 * Returns how many times loop should repeat during each cycle
	 */
	public final int getRepeat() {
		return _repeat;
	}

	/**
	 * Returns the oldest LinkedRemoteFile(directory) that needs to be archived
	 * by this type's definition If no such directory exists, it returns null
	 * 
	 * Checks dir by regex, and by lastmodified.
	 */
	public final DirectoryHandle getOldestNonArchivedDir() {
		ArrayList<DirectoryHandle> oldDirs = new ArrayList<DirectoryHandle>();

		try {
			for (Iterator<DirectoryHandle> iter = getSection().getCurrentDirectory().getDirectoriesUnchecked().iterator(); iter.hasNext();) {
				DirectoryHandle lrf = iter.next();
				try {
					_parent.checkPathForArchiveStatus(lrf.getPath());
				} catch (DuplicateArchiveException e1) {
					continue;
				}

				try {
					// Checks regex to see if dir should be archived or not
					if (lrf.getName().matches(getArchiveRegex())) {
						//make sure dir is archived before moving it
						if (!isArchivedDir(lrf)) {
							if ((System.currentTimeMillis() - lrf.lastModified()) > getArchiveAfter()) {
								oldDirs.add(lrf);
							}
						} else {
							//move release to dest folder if needed
							if (_moveRelease) {
								if ((System.currentTimeMillis() - lrf.lastModified()) > getArchiveAfter()) {
									oldDirs.add(lrf);
								}								
							}
						}
					}
				} catch (IncompleteDirectoryException e) {
					continue;
				} catch (OfflineSlaveException e) {
					continue;
				} catch (FileNotFoundException e) {
					continue;
					// directory was deleted or moved
				}
			}
		} catch (FileNotFoundException e) {
			// section does not exist, no directories to archive
			// list is empty so the rest of the code will handle that
		}

		DirectoryHandle oldestDir = null;
		long oldestDirLM = 0;
		for (Iterator<DirectoryHandle> iter = oldDirs.iterator(); iter
				.hasNext();) {
			DirectoryHandle temp = iter.next();

			if (oldestDir == null) {
				oldestDir = temp;
				try {
					oldestDirLM = oldestDir.lastModified();
				} catch (FileNotFoundException e) {
					oldestDir = null;
					iter.remove();
				}
				continue;
			}
			try {
				if (oldestDirLM > temp.lastModified()) {
					oldestDir = temp;
				}
			} catch (FileNotFoundException e) {
				iter.remove();
				continue;
			}
		}
		if (oldestDir != null) {
			logger.debug(getClass().toString() + " - Returning the oldest directory " + oldestDir);
			return oldestDir;
		}
		logger.debug(getClass().toString() + " - All directories are archived");
		return null;
	}

	/*
	 * Returns unmodifiable Set<RemoteSlave>.
	 */
	public final Set<RemoteSlave> getRSlaves() {
		return _slaveList == null ? null : Collections.unmodifiableSet(_slaveList);
	}
	
	/*
	 * Gets the jobmananger, hopefully its loaded.
	 */
	public JobManager getJobManager() {
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof JobManager) {
				return (JobManager) plugin;
			}
		}
		throw new RuntimeException("JobManager is not loaded");
	}

	/**
	 * Adds relevant Jobs to the JobManager and returns an ArrayList of those
	 * Job's
	 * 
	 * @throws FileNotFoundException
	 */
	public ArrayList<Job> send() throws FileNotFoundException {
		ArrayList<Job> jobs = recursiveSend(getDirectory());
		JobManager jm = getJobManager();
		jm.addJobsToQueue(jobs);
		return jobs;
	}

	/*
	 * Adds the files in the directory (and subdirs) to the job manager to be archived
	 */
	protected ArrayList<Job> recursiveSend(DirectoryHandle lrf) throws FileNotFoundException {
		ArrayList<Job> jobQueue = new ArrayList<Job>();

		for (Iterator<DirectoryHandle> iter = lrf.getDirectoriesUnchecked().iterator(); iter.hasNext();) {
			jobQueue.addAll(recursiveSend(iter.next()));
		}
		for (Iterator<FileHandle> iter = lrf.getFilesUnchecked().iterator(); iter.hasNext();) {
			FileHandle file = iter.next();
			logger.info("Adding " + file.getPath() + " to the job queue");
			Job job = new Job(file, _priority, _numOfSlaves, getRSlaves());
			jobQueue.add(job);
		}

		return jobQueue;
	}

	/*
	 * Checks to see if files are achived to the all the slaves configured
	 */
	protected static final boolean isArchivedToXSlaves(DirectoryHandle lrf,int x) throws OfflineSlaveException {
		HashSet<RemoteSlave> slaveSet = null;
		Set<DirectoryHandle> directories = null;
		Set<FileHandle> files = null;
		try {
			directories = lrf.getDirectoriesUnchecked();
			files = lrf.getFilesUnchecked();
		} catch (FileNotFoundException e1) {
			// directory doesn't exist, no files to archive
			return true;
		}

		for (Iterator<DirectoryHandle> iter = directories.iterator(); iter.hasNext();) {
			if (!isArchivedToXSlaves(iter.next(), x)) {
				return false;
			}
		}
		
		for (Iterator<FileHandle> iter = files.iterator(); iter.hasNext();) {
			FileHandle file = iter.next();
			Collection<RemoteSlave> availableSlaves;
			try {
				if (!file.isAvailable()) {
					throw new OfflineSlaveException(file.getPath() + " is offline");
				}
				availableSlaves = file.getSlaves();
			} catch (FileNotFoundException e) {
				// can't archive a directory with files that have been moved,
				// we'll come back later
				return true;
			}

			if (slaveSet == null) {
				slaveSet = new HashSet<RemoteSlave>(availableSlaves);
			} else {
				if (!(slaveSet.containsAll(availableSlaves) && availableSlaves.containsAll(slaveSet))) {
					return false;
				}
			}
		}

		if (slaveSet == null) { // no files found in directory
			return true;
		}

		for (RemoteSlave rslave : slaveSet) {
			if (!rslave.isAvailable()) {
				throw new OfflineSlaveException(rslave.getName() + " is offline");
			}
		}

		return (slaveSet.size() == x);
	}

	/*
	 * Checks to see if directory is currently being archived.
	 */
	public final boolean isBusy() {
		return (getDirectory() != null);
	}

	/*
	 * Returns when to archive files (after X duration)
	 */
	protected final long getArchiveAfter() {
		return _archiveAfter;
	}

	/*
	 * Returns section that directory is in
	 */
	public final SectionInterface getSection() {
		return _section;
	}

	/*
	 * Returns the REGEX string for directory checks
	 */
	public final String getArchiveRegex() {
		return _archiveRegex;
	}
	
	/**
	 * Sets standard properties for this ArchiveType
	 */
	private void setProperties(Properties properties) {
		_failedDirs = new ArrayList<String>();
		_archiveAfter = 60000 * Long.parseLong(PropertyHelper.getProperty(properties, _confnum + ".archiveafter","0"));
		_numOfSlaves = Integer.parseInt(properties.getProperty(_confnum + ".numofslaves", "0"));
		_repeat = Integer.parseInt(properties.getProperty(_confnum + ".repeat", "1"));
		if (_repeat < 1) {
			_repeat = 1;
		}
		
		
		/*
		 * Grabs archiveRegex property to check if archive dir matches.  If empty, archives all dirs
		 */
		_archiveRegex = properties.getProperty(_confnum+ ".archiveregex",".*");
		try {
			Pattern.compile(_archiveRegex);
		} catch (PatternSyntaxException e) {
			logger.error("Regex Entry for " + _confnum + " is invalid");
			_archiveRegex = ".*";
		}
		

		/*
		 * Gets toDirectory property to check if the folder should be moved after archiving
		 */
		_archiveToFolder = null;
		_destinationDirectory = null;
		_moveRelease = false;
		_archiveDirType = "";
		String _moveToDirProp = properties.getProperty(_confnum + ".todirectory",""); 
		if (_moveToDirProp != "") {
			SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().getSection(_moveToDirProp);
	        if (sec.getName().isEmpty()) {
	        	try {
			        DirectoryHandle moveInode = new DirectoryHandle(_moveToDirProp);
			        if (!moveInode.exists()) {
			        	// dir doesn't exist.  Lets check if Parent does
			        	if (!moveInode.getParent().exists()) {
			        		// parent dir doesn't exist either, really don't wanna make 3 sets of dirs.
			        		logger.error("Directory and ParentDirectory for conf number '" + _confnum + "' Not Found: " + _moveToDirProp);        		
			        	} else {
			        		// Parent Exists = Good we can do this
			        		_archiveToFolder = moveInode;
			        	}
			        } else {
			        	// Destination exists = Good we can do this
			        	_archiveToFolder = moveInode;
			        }
	        	} catch (IllegalArgumentException e) {
	        		//todirectory does not exist.
	        	}
	        } else {
	        	// Section exists = Good we can do this
	        	_archiveToFolder = sec.getCurrentDirectory();
	        }
	        
	        if (_archiveToFolder != null) {
	        	_moveRelease = true;
	        	
		        /*
		         * If a dir/section is selected, check to see if a specific type of subdir needs to be created.
		         */
	        	_archiveDirType = properties.getProperty(_confnum + ".todirectorytype","");
		        
	        }
		}
		
		HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();

		for (int i = 1;; i++) {
			String slavename = null;

			try {
				slavename = PropertyHelper.getProperty(properties, _confnum + ".slavename." + i);
			} catch (NullPointerException e) {
				break; // done
			}

			try {
				RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
				destSlaves.add(rslave);
			} catch (ObjectNotFoundException e) {
				logger.error("Unable to get slave " + slavename + " from the SlaveManager");
			}
		}
		_slaveList = destSlaves;
		
		/*
		 * Checks to see if any slaves were in the conf file and
		 * if none were to check it the conf wants to move the rls which sets the 
		 * variable correctly.
		 */
		_moveReleaseOnly = ((_slaveList.isEmpty()) && (_moveRelease));
		
		_priority = Integer.parseInt(properties.getProperty(_confnum + ".priority", "3"));
	}

	/*
	 * Sets the current directory to be archived.
	 */
	public final void setDirectory(DirectoryHandle lrf) {
		_directory = lrf;
	}

	/*
	 * Sets the slaves to archive too
	 */
	public final void setRSlaves(Set<RemoteSlave> slaveList) {
		_slaveList = slaveList;
	}

	/*
	 * Loops through and waits for all files to archive to configured slaves.
	 */
	public final void waitForSendOfFiles(ArrayList<Job> jobQueue) {
		while (true) {
			for (Iterator<Job> iter = jobQueue.iterator(); iter.hasNext();) {
				Job job = iter.next();

				if (job.isDone()) {
					logger.debug("Job " + job + " is done being sent");
					iter.remove();
				}
			}

			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}

			if (jobQueue.isEmpty()) {
				break;
			}
		}
	}

	/*
	 * Loops though all the slaves, and returns all current slaves for archive
	 */
	protected String outputSlaves(Collection<RemoteSlave> slaveList) {
		StringBuilder slaveBuilder = new StringBuilder();
		
		for (Iterator<RemoteSlave> iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();
			slaveBuilder.append(rslave.getName());

			if (iter.hasNext()) {
				slaveBuilder.append(',');
			} else {
				return slaveBuilder.toString();
			}
		}

		return "Empty";
	}

	/*
	 * This will move the release to the folder specified
	 * It will check if it exists, if not it will create it and its parent if that doesn't exist either.
	 * It will however only go back to its parent to create the dirs.
	 */
	public boolean moveRelease(DirectoryHandle fromDir) {
		if (_moveRelease) {
			if (!_archiveToFolder.exists()) {
	        	// dir doesn't exist.  Lets check if Parent does
	        	if (!_archiveToFolder.getParent().exists()) {
	        		logger.warn("Cannot Archive '" + getDirectory().getPath() + "' to '" + _archiveToFolder.getPath() + " because parent path does not exist");
	        		return false;
	        	}
	    		try {
	    			_archiveToFolder.getParent().createDirectorySystem(_archiveToFolder.getName());
	    		} catch (FileExistsException e) {
	    			// ignore...directory now exists 
	    		} catch (FileNotFoundException e) {
	    			logger.warn("Cannot Archive '" + getDirectory().getPath() + "' to '" + _archiveToFolder.getPath() + " unable to create '" + _archiveToFolder.getPath() + "'" );
	    			return false;
	    		}		    			
			}
			
			// Check if we need to add another extension to the destination folder
			String type = getDirType(_archiveDirType,fromDir);
			if (!_archiveDirType.isEmpty()) {
				if (type != null) {
					DirectoryHandle typeInode = new DirectoryHandle(_archiveToFolder.getPath() + VirtualFileSystem.separator + type);
					if (!typeInode.exists()) {
			    		try {
			    			typeInode.getParent().createDirectorySystem(typeInode.getName());
			    		} catch (FileExistsException e) {
			    			// ignore...directory now exists 
			    		} catch (FileNotFoundException e) {
			    			logger.warn("Cannot Archive '" + getDirectory().getPath() + "' to '" + _archiveToFolder.getPath() + " unable to create dir tpye '" + typeInode.getPath() + "'" );
			    			return false;
			    		}		    						
					}
				}
			}

			try {
				DirectoryHandle toInode = new DirectoryHandle(_archiveToFolder.getPath() + VirtualFileSystem.separator + fromDir.getName());
				if (type != null) {
					toInode = new DirectoryHandle(_archiveToFolder.getPath() + VirtualFileSystem.separator + type + VirtualFileSystem.separator + fromDir.getName());					
				}
				fromDir.renameToUnchecked(toInode);
				_destinationDirectory = toInode; 
				return true;
			} catch (FileExistsException e) {
				logger.warn("Cannot Archive '" + getDirectory().getPath() + "' to '" + _archiveToFolder.getPath() + " because it already exists at destination"); 
			} catch (FileNotFoundException e) {
				logger.warn("Cannot Archive '" + getDirectory().getPath() + "' to '" + _archiveToFolder.getPath() + " because '" + getDirectory().getPath() + "' no longer exists");
			}			
		}
		return false;
	}

	/*
	 * This is used to get the actual dir that the archived dir needs to be moved too
	 * This is overidable to other types can be made within different archive types
	 */
	protected String getDirType(String type, DirectoryHandle inode) {
		if (type.equals("alpha")) {
			if (inode.getName().matches("^[0-9].*$")) {
				return "0-9";
			} else if (inode.getName().matches("^[a-zA-Z].*$")) { 
				return "" + inode.getName().toUpperCase().charAt(0);
			}
		} else if (type.toLowerCase().startsWith("dated:")) {
			String[] splittype = _archiveDirType.split(":");
			if (splittype.length > 1) {
				SimpleDateFormat _dateFormat = new SimpleDateFormat(splittype[1]);
				return _dateFormat.format(new Date());
			}
			logger.warn("Date format invalid for: _archiveDirType");
		}
		logger.warn("No valid type found for: " + type);
		return null;
	}

	/*
	 * Returns if we are just moving the directory and not archive to other slaves
	 */
	public boolean moveReleaseOnly() {
		return _moveReleaseOnly;
	}

	/*
	 * Returns if the release is moving to a different dir
	 */
	public boolean isMovingRelease() {
		return _moveRelease;
	}
	
	/*
	 * returns the destination directory (including type) after move (mostly for events)
	 */
	public DirectoryHandle getDestinationDirectory() {
		return _destinationDirectory;
	}

	/*
	 * Adds failed dir for archiving when using repeat
	 */
	public void addFailedDir(String path) {
		_failedDirs.add(path);
	}
	
	/*
	 * Returns if dir has been added to the Failed dirs
	 */
	public boolean checkFailedDir(String path) {
		return _failedDirs.contains(path);
	}	
	
}
