package net.sf.drftpd.slave;

import se.mog.io.File;

/**
 * @author mog
 * @version $Id: Root.java,v 1.6 2003/11/19 00:20:54 mog Exp $
 */
public class Root {
	private File _rootFile;
	private String _root;
	private long _minSpaceFree = 50000000; //50,000,000 = 50mb
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
	
	/**
	 * Returns true if File.getDiskSpaceAvailable() is less than getMinSpaceFree()
	 * @return true if File.getDiskSpaceAvailable() is less than getMinSpaceFree()
	 */
	public boolean isFull() {
		return getFile().getDiskSpaceAvailable() < getMinSpaceFree();
	}
}
