/*
 * Created on 2003-aug-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.remotefile;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public interface RemoteFileTree extends RemoteFileInterface {
	public RemoteFileTree getParentFile();
	public String getPath();
}
