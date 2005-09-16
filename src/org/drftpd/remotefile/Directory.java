/**
 * 
 */
package org.drftpd.remotefile;

import java.util.ArrayList;

import net.sf.drftpd.ObjectNotFoundException;

import org.drftpd.dynamicdata.Key;
import org.drftpd.usermanager.User;

/**
 * @author zubov
 * @version $Id$
 */
class Directory extends Inode {
	public static final Key INODES = new Key(Directory.class, "inodes",
			ArrayList.class);
	/**
	 * @param path
	 */
	public Directory(String path, String user, String group) {
		super(path, user, group);
		ArrayList<String> inodes = new ArrayList<String>();
		setObject(INODES,inodes);
	}
	
	protected void removeInode(Inode inode) {
		removeFile(FileManager.parseFileNameFromPath(inode.getPath()));
	}
	
	protected void removeFile(String name) {
		getListOfFiles().remove(name);
	}
	
	private ArrayList<String> getListOfFiles() {
		try {
			return (ArrayList) getObject(INODES);
		} catch (ObjectNotFoundException e) {
			throw new RuntimeException("Directory cannot exist without a list of files", e);
		}
	}
	
	public void addInode(Inode inode) {
		getListOfFiles().add(FileManager.parseFileNameFromPath(inode.getPath()));
	}
	
	public ArrayList<PathReference> getInodes(User user) {
		ArrayList<PathReference> files = new ArrayList<PathReference>();
		for (String name : getListOfFiles()) {
			files.add(new PathReference(getPath() + FileManager.separator + name));
		}
		return files;
	}

}
