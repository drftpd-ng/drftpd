package net.sf.drftpd.master.sections;

import java.util.Collections;
import java.util.List;

import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
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
