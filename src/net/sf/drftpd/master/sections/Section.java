package net.sf.drftpd.master.sections;

import java.util.List;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
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
