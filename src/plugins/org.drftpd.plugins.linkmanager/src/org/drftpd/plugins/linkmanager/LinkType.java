/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.linkmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author CyBeR
 * @version $Id: LinkType.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public abstract class LinkType {
	protected static final Logger logger = LogManager.getLogger(LinkType.class);
	
	private String _dirname;
	private String _eventtype;
	private SectionInterface _section;
	private String _linkname;
	private String[] _deleteon;
	private String _exclude;
	private String _sectionexclude;
	private String _addparentdir;
	
	/*
	 * Loads all the .conf information for the specific type
	 */
	public LinkType(Properties p, int confnum, String type) {
		_eventtype = type;
        _dirname = p.getProperty(confnum + ".dirname","%").trim();
        String section = p.getProperty(confnum + ".section","*").trim();
        _linkname = p.getProperty(confnum + ".linkname","").trim();
        _deleteon = p.getProperty(confnum + ".deleteon","*").trim().split(";");
        _exclude = p.getProperty(confnum + ".exclude","").trim();
        _sectionexclude = p.getProperty(confnum + ".sectionexclude","").trim();
        _addparentdir = p.getProperty(confnum + ".addparentdir","").trim();
        
        _section = null;
        if (!section.equals("*")) {
        	_section = GlobalContext.getGlobalContext().getSectionManager().getSection(section);
        	if (_section == null) {
        		throw new RuntimeException("Invalid Section for " + confnum + ".section - Skipping Config");
        	}
        }

        if (_linkname.isEmpty()) {
        	throw new RuntimeException("Invalid LinkName for " + confnum + ".linkname - Skipping Config");
        }		
        
	}
	
	/*
	 * Returns the directory of where the links are
	 * Suppose to be created, % denotes current directory's parent
	 */
	public String getDirName(DirectoryHandle dir) {
		if (_dirname.equals("%")) {
			if (dir.getName().matches(_addparentdir)) {
				if (!dir.getParent().isRoot()) {
					return dir.getParent().getParent().getPath();
				}
			}
			return dir.getParent().getPath();
		}
		return _dirname;
	}
	
	public String getEventType() {
		return _eventtype;
	}
	
	public SectionInterface getSectionInterface() {
		return _section;
	}
	
	public String getSection() {
		return _section.getName();
	}
	
	public String getLinkName() {
		return _linkname;
	}
	
	/*
	 * returns true/false to delete links on which types.
	 */
	public boolean getDeleteOnContains(String deleteon) {
		for (String cmp : _deleteon) {
			if ((cmp.equalsIgnoreCase(deleteon)) || (cmp.equals("*"))) {
				return true;
			}
		}
		return false;
	}
	
	public String getExclude() {
		return _exclude;
	}

	public String getSectionExclude() {
		return _sectionexclude;
	}
	
	public String getAddParentDir() {
		return _addparentdir;
	}
	
	/*
	 * Recursively Create parent directories
	 */
	protected boolean createDirectories(DirectoryHandle dir) {
		if (!dir.exists() && (!dir.isRoot())) {
			if (!dir.getParent().exists()) {
				if (!createDirectories(dir.getParent())) {
					return false;
				}
			}
			
			try {
    			dir.getParent().createDirectorySystem(dir.getName());
    		} catch (FileExistsException e) {
    			// ignore...directory now exists 
    		} catch (FileNotFoundException e) {
    			return false;
    		}		
					    				
		}
		return true;
	}
	
	/*
	 * This will create the link file in the proper folder based on the .conf file
	 * 
	 * It will also check and make sure it does not exist as an AddParentDir, but if it does, creates the link accordingly
	 * 
	 */
	protected void createLink(DirectoryHandle targetDir, String dirPath, String linkName) {
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(targetDir);
		String sectionname;
		if (!section.getName().isEmpty()) {
			if (targetDir.equals(section.getBaseDirectory())) {
				// Base Section - Skip
				return;
			}
		
			// If section is dated - ignore child dir (dated dir)
			if (!section.getBaseDirectory().equals(section.getCurrentDirectory())) {
				if (targetDir.getParent().equals(section.getBaseDirectory())) {
					// SubDir is the Dated Part of a section - Skip
					return;
				}
			}
			
			// This checks if the section is excluded (regex)
			// NOTE:  This only works if the section is a REAL section defined in sections.conf or
			// 	    	if using section.def
			if (section.getName().matches(getSectionExclude())) {
				// Section is excluded
				return;
			}
			
			// Save the section name for later use.
			sectionname = section.getName();
		} else {
			// Dir isn't in a section but lets check if its at the root of the FTP
			try {
				if (targetDir.getParent().isRoot()) {
					// Parent Is Root - Skip
					return;
				}
			} catch (IllegalStateException e) {
				// Directory Is Root - Skip
				return;
			}
			
			/* Since this isn't a real section name
			 * Lets find out what root dir/section it is from
			 */
			DirectoryHandle dir = targetDir.getParent();
			while (!dir.getParent().isRoot()) {
				dir = dir.getParent();
			}
			sectionname = dir.getName();
		}
		
		if (dirPath.matches(getExclude())) {
			// Exempt from creation
			return;
		}
		
		Pattern totalPat = Pattern.compile(getAddParentDir());
		Matcher totalMat = totalPat.matcher(linkName);
		
		String linkNameFinal = getLinkName().replace("${dirname}",linkName);
		if (totalMat.find()) {
			linkNameFinal = getLinkName().replace("${dirname}",dirPath.substring(dirPath.substring(0,dirPath.lastIndexOf("/")).lastIndexOf("/")+1).replace("/","-"));
		}

		linkNameFinal = linkNameFinal.replace("${section}",sectionname);		
		
		DirectoryHandle linkDir = new DirectoryHandle(getDirName(targetDir));
		if (!linkDir.exists()) {
            logger.debug("LinkDir does not exist, so we are going to create it ({})", getDirName(targetDir));
			
			if (!createDirectories(linkDir)) {
                logger.debug("Unable to create LinkDir - Aborting ({})", getDirName(targetDir));
				return;
			}
			
		}
		
		/*
		 * Create the link now that all info has been retrieved
		 */
		
		LinkHandle link = null;
		try {
			link = linkDir.getLinkUnchecked(linkNameFinal);
		} catch (FileNotFoundException e) {
			// this is okay, the link does not exist
		} catch (ObjectNotValidException e) {
            logger.error("There is already a non-Link inode in the place where this link should go. ({})", linkNameFinal, e);
			return;
		}

		/* If link already exists, lets update the path */
		if (link != null) {
			try {
				link.setTarget(targetDir.getPath());
				// Updated link path, we're done.
				return;
			} catch (FileNotFoundException e) {
				// No Link Exists, time to create it
			}
		}
		try {
			linkDir.createLinkUnchecked(linkNameFinal, targetDir.getPath(), "drftpd", "drftpd");
		} catch (FileExistsException e) {
            logger.error("{} already exists in {}, this should not happen, we just deleted it", linkNameFinal, linkDir, e);
		} catch (FileNotFoundException e) {
			// linkDir has been deleted, ignore
		}
	
	}
	
	/*
	 * This method will delete the link(s) corresponding with the .conf file
	 */
	protected void deleteLink(DirectoryHandle targetDir, String dirPath, String linkName) {
		DirectoryHandle linkDir = new DirectoryHandle(getDirName(targetDir));
		if (linkDir.exists()) {
			String linkNameFinal = getLinkName().replace("${dirname}",linkName);
			Pattern totalPat = Pattern.compile(getAddParentDir());
			Matcher totalMat = totalPat.matcher(linkName);
			if (totalMat.find()) {
		    	linkNameFinal = getLinkName().replace("${dirname}",dirPath.substring(dirPath.substring(0,dirPath.lastIndexOf("/")).lastIndexOf("/")+1).replace("/","-"));
			}
			/*
			 * Get section information for link
			 */
			SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(targetDir);
			String sectionname;
			if (!section.getName().isEmpty()) {
				sectionname = section.getName();
			} else {
				DirectoryHandle dir = targetDir.getParent();
				while (!dir.getParent().isRoot()) {
					dir = dir.getParent();
				}
				sectionname = dir.getName();
			}
			linkNameFinal = linkNameFinal.replace("${section}",sectionname);
			
			/*
			 * Find Exact Link Name
			 */
			try {
				LinkHandle link = linkDir.getLinkUnchecked(linkNameFinal);
				try {
					link.deleteUnchecked();
				} catch (FileNotFoundException e) {
					// Link no longer exists....ignore
				}
				
			} catch (FileNotFoundException e1) {
				// Not Found, ignore
			} catch (ObjectNotValidException e1) {
				// INode doesn't exist
			}
			
			/*
			 * Find Any Other Link Names With This Path
			 */
			try {
				for (LinkHandle link : linkDir.getLinksUnchecked()) {
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						try {
							if (link.getTargetStringWithSlash().startsWith(targetDir.getPath() + "/")) {
								link.deleteUnchecked();
							}
						} catch (FileNotFoundException e) {
							// Link no longer exists - Ignore
						}							
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						try {
							link.deleteUnchecked();
						} catch (FileNotFoundException e) {
							// Link no longer exists - Ignore
						}	
					}
				}
			} catch (FileNotFoundException e2) {
				//No Links Found - Ignore
			}
		}
	}

	/*
	 * This will rename all links that moved to the new destination
	 */
	protected void doRename(DirectoryHandle targetDir, DirectoryHandle oldDir) {
		DirectoryHandle linkDir = new DirectoryHandle(getDirName(targetDir));
		if (linkDir.exists()) {
			try {
				for (LinkHandle link : linkDir.getLinksUnchecked()) {
					/*
					 * Try to rename link if link is already in target Dir 
					 */
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						try {
							if (link.getTargetStringWithSlash().startsWith(oldDir.getPath() + "/")) {
								// Rename/Repoint Link
								try {
									LinkHandle newlink = new LinkHandle(link.getPath().replace(oldDir.getName(), targetDir.getName()));
									String oldtarget = link.getTargetStringWithSlash();

									link.renameToUnchecked(newlink);
									newlink.setTarget(oldtarget.replace(oldDir.getPath(),targetDir.getPath()));
								} catch (FileNotFoundException e) {
									link.deleteUnchecked();
								} catch (FileExistsException e) {
									link.deleteUnchecked();
								}
							} else {
								link.deleteUnchecked();	
							}
						} catch (FileNotFoundException e) {
							// Link no longer exists - Ignore
						}							
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						try {
							link.deleteUnchecked();
						} catch (FileNotFoundException e) {
							// Link no longer exists - Ignore
						}	
					}
				}
					
				/*
				 * Try to rename link if link is still in OLD target dir
				 */
				DirectoryHandle oldlinkDir = new DirectoryHandle(getDirName(oldDir));
				if (oldlinkDir.exists()) {
					try {
						for (LinkHandle link : oldlinkDir.getLinksUnchecked()) {					
							try {
								link.getTargetDirectoryUnchecked();
							} catch (FileNotFoundException e1) {
								try {
									if (link.getTargetStringWithSlash().startsWith(oldDir.getPath() + "/")) {
										// Rename/Repoint Link
										try {
											LinkHandle newlink = null;
											if (targetDir.getName().contains("[NUKED]-")) {
												newlink = new LinkHandle(link.getPath().replace(oldDir.getName(), targetDir.getName()));
											} else {
												newlink = new LinkHandle(link.getPath().replace(oldDir.getName(), targetDir.getName()).replace(link.getParent().getPath(),linkDir.getPath()));
											}
											String oldtarget = link.getTargetStringWithSlash();

											link.renameToUnchecked(newlink);
											newlink.setTarget(oldtarget.replace(oldDir.getPath(),targetDir.getPath()));
										} catch (FileNotFoundException e) {
											link.deleteUnchecked();
										} catch (FileExistsException e) {
											link.deleteUnchecked();
										}
									} else {
										link.deleteUnchecked();	
									}
								} catch (FileNotFoundException e) {
									// Link no longer exists - Ignore
								}							
							} catch (ObjectNotValidException e1) {
								// Link target isn't a directory, delete the link as it is bad
								try {
									link.deleteUnchecked();
								} catch (FileNotFoundException e) {
									// Link no longer exists - Ignore
								}	
							}
						}	
					} catch (FileNotFoundException e2) {
						//No Links Found - Ignore
					}
				}

			} catch (FileNotFoundException e2) {
				//No Links Found - Ignore
			}
			
		}
	}
	
	/*
	 * Abstract class to create the link with the specified link name
	 */
	public abstract void doCreateLink(DirectoryHandle targetDir);
	
	/*
	 * Abstract class to delete the link with the specified link name
	 */
	public abstract void doDeleteLink(DirectoryHandle targetDir);

	/*
	 * Abstract class used to fix links
	 */
	public abstract void doFixLink(DirectoryHandle targetDir);
}