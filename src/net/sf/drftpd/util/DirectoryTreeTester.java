package net.sf.drftpd.util;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
import net.sf.drftpd.LinkedRemoteFile;
import java.io.File;

public class DirectoryTreeTester {

	public static void main(String[] args) {
		LinkedRemoteFile root = new LinkedRemoteFile(null, new File(args[args.length-1]));
		
		LinkedRemoteFile files[] = root.listFiles();
		for(int i=0; i<files.length; i++) {
			System.out.println(files[i]);
		}
	}
}
