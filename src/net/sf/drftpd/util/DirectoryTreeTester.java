package net.sf.drftpd.util;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFileTree;

import java.io.File;
import java.io.IOException;

public class DirectoryTreeTester {

	public static void main(String[] args) {
		try {
			RemoteFileTree root = new LinkedRemoteFile(null, new FileRemoteFile(args[0], new File(args[0])));
		
		RemoteFileTree files[] = root.listFiles();
		for(int i=0; i<files.length; i++) {
			System.out.println(files[i]);
		}
		} catch(IOException ex) {
			ex.printStackTrace();
			return;
		}
	}
}
