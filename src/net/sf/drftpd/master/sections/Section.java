package net.sf.drftpd.master.sections;

import java.util.Collection;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: Section.java,v 1.3 2003/12/23 13:38:20 mog Exp $
 */
public interface Section {

	/**
	 * @return The name of this section
	 */
	public String getName();

	/**
	 * @return the (current) directory for this section
	 */
	public LinkedRemoteFile getFile();

	/**
	 * @return all directories for this section. For example if this is a dated-dir section, it would return all dated dirs, including current dir.
	 */
	public Collection getFiles();
}
