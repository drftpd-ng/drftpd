package net.sf.drftpd.master.sections;

import java.util.Collection;
import java.util.Collections;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: PlainSection.java,v 1.3 2003/12/23 13:38:20 mog Exp $
 */
public class PlainSection implements Section {

	private LinkedRemoteFile _dir;
	private String _name;

	public PlainSection(String name, LinkedRemoteFile dir) {
		_name = name;
		_dir = dir;
	}

	public LinkedRemoteFile getFile() {
		return _dir;
	}

	public Collection getFiles() {
		return Collections.singletonList(getFile());
	}
	
	public String getName() {
		return _name;
	}
}
