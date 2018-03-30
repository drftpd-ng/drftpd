package org.drftpd.commands.autonuke;

import org.drftpd.PropertyHelper;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.io.FileNotFoundException;

/**
 * @author scitz0
 */
public class MissingConfig extends Config {
	private static final Logger logger = Logger.getLogger(MissingConfig.class);
	String _missing;

	public MissingConfig(int i, Properties p) {
		super(i, p);
		_missing = PropertyHelper.getProperty(p, i + ".missing");
	}

	/**
	 * Boolean to return missing status
	 * Minimum percent optional
	 * @param 	configData	Object holding return data
	 * @param 	dir 		Directory currently being handled
	 * @return				Return false if dir should be nuked, else true
	 */
	public boolean process(ConfigData configData, DirectoryHandle dir) {
		try {
			for (InodeHandle i : dir.getInodeHandlesUnchecked()) {
				if (i.isFile() || i.isDirectory()) {
					if (i.getName().matches(_missing)) {
						return true;
					}
				}
			}
		} catch (FileNotFoundException e) {
			logger.warn("AutoNuke checkMissing: FileNotFoundException - " + dir.getName());
			return true;
		}
		return false;
	}

}
