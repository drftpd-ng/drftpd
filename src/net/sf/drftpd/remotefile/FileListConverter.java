package net.sf.drftpd.remotefile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import net.sf.drftpd.master.SlaveManagerImpl;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class FileListConverter {
	public static void main(String[] args) throws IOException {
		if(args.length != 0) {
			System.out.println("Converts from files.xml to files.mlst");
			return;
		}
		System.out.println("Converting files.xml to files.mlst");
		System.out.println("This might take a while for large files and/or slow servers, have patience...");
		LinkedRemoteFile root = SlaveManagerImpl.loadFileDatabase(SlaveManagerImpl.loadRSlaves(), null);
		MLSTSerialize.serialize(root, new PrintStream(new FileOutputStream("files.mlst")));
		System.out.println("Completed, have a nice day");
	}
}
