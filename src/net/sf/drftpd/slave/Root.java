/*
 * Created on 2003-aug-10
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.slave;

import se.mog.io.File;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Root {
	private String _root;
	private long _minSpaceFree = 0;
	private int _priority = 0;

	public Root(String root, long minSpaceFree, int priority) {
		_root = root;
	}
	public File getFile() {
		return new File(_root);
	}
	public String getPath() {
		return _root;
	}

	public long getMinSpaceFree() {
		return _minSpaceFree;
	}
	public int getPriority() {
		return _priority;
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "[root=" + getPath() + "]";
	}
	public long getDiskSpaceAvailable() {
		return getFile().getDiskSpaceAvailable();
	}

	public long getDiskSpaceCapacity() {
		return getFile().getDiskSpaceCapacity();
	}

}
