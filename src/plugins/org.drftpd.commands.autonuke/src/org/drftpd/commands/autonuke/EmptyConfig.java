package org.drftpd.commands.autonuke;

import org.drftpd.vfs.DirectoryHandle;

import java.util.Properties;

/**
 * @author scitz0
 */
public class EmptyConfig extends Config {

	public EmptyConfig(int i, Properties p) {
		super(i, p);
	}

	/**
	 * Boolean to return empty status
	 * Minimum percent optional
	 * @param 	configData	Object holding return data
	 * @param 	dir 		Directory currently being handled
	 * @return				Return false if dir should be nuked, else true
	 */
	public boolean process(ConfigData configData, DirectoryHandle dir) {
		return !dir.getAllFilesRecursiveUnchecked().isEmpty();
	}

}
