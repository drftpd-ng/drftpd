/**
 * 
 */
package org.drftpd.remotefile;

import net.sf.drftpd.ObjectNotFoundException;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.dynamicdata.KeyedMap;

/**
 * @author zubov
 * @version $Id$ The Inode is the basic file information container
 */
abstract class Inode {
	private static final Logger logger = Logger.getLogger(Inode.class);

	String _group = null;

	private KeyedMap<Key, Object> _keyMap = null;

	long _lastModified;

	String _path = null;

	long _size;

	String _user = null;

	/**
	 * Holds the information about Files, Directories, and Links An Inode by
	 * itself should not be created
	 */
	protected Inode(String path, String user, String group) {
		validatePath(path);
		_path = path;
		_user = user;
		_group = group;
		_size = 0;
		_lastModified = System.currentTimeMillis();
		_keyMap = new KeyedMap<Key, Object>();
		commit();
	}

	private void commit() {
		FileManager.getFileManager().commit(this);
	}

	public String getName() {
		return FileManager.parseFileNameFromPath(_path);
	}

	public Object getObject(Key key) throws ObjectNotFoundException {
		try {
			return _keyMap.getObject(key);
		} catch (KeyNotFoundException e) {
			throw new ObjectNotFoundException(e);
		}
	}

	public String getPath() {
		return _path;
	}

	public long getSize() {
		return _size;
	}

	public boolean isDirectory() {
		return this instanceof Directory;
	}

	public boolean isFile() {
		return this instanceof File;
	}

	public boolean isLink() {
		return this instanceof Link;
	}

	public long lastModified() {
		return _lastModified;
	}

	public void setGroup(String group) {
		_group = group;
		commit();
	}

	public void setLastModified(long lastMod) {
		_lastModified = lastMod;
		commit();
	}

	public void setObject(Key key, Object obj) {
		_keyMap.setObject(key, obj);
		commit();
	}

	public void setUser(String user) {
		_user = user;
		commit();
	}

	public void validatePath(String path) {
		FileManager.validatePath(path);
	}
}
