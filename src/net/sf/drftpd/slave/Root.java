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
	private File _rootFile;
	private String _root;
	private long _minSpaceFree = 0;
	private int _priority = 0;
	private long _lastModified;

	public Root(String root, long minSpaceFree, int priority) {
		_root = root;
		_rootFile = new File(_root);
		_lastModified = getFile().lastModified();
	}
	public File getFile() {
		return _rootFile;
	}
	public String getPath() {
		return _root;
	}
	public long lastModified() {
		return _lastModified;
	}
	public void touch() {
		getFile().setLastModified(_lastModified = System.currentTimeMillis());
	}
	public long getMinSpaceFree() {
		return _minSpaceFree;
	}
	public int getPriority() {
		return _priority;
	}

	public String toString() {
		return "[root=" + getPath() + "]";
	}
	public long getDiskSpaceAvailable() {
		return getFile().getDiskSpaceAvailable();
	}

	public long getDiskSpaceCapacity() {
		return getFile().getDiskSpaceCapacity();
	}

	public File getFile(String path) {
		return new File(_root + File.separator + path);
	}

	public boolean isFull() {
		return getFile().getDiskSpaceAvailable() > getMinSpaceFree();
	}
}
