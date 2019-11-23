package org.drftpd.commands.autonuke;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.PropertyHelper;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * @author scitz0
 */
public class MissingConfig extends Config {
	private static final Logger logger = LogManager.getLogger(MissingConfig.class);
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
            logger.warn("AutoNuke checkMissing: FileNotFoundException - {}", dir.getName());
			return true;
		}
		return false;
	}

}
