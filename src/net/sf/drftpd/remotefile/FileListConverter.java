package net.sf.drftpd.remotefile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.log4j.BasicConfigurator;

import net.sf.drftpd.master.SlaveManagerImpl;

/**
 * @author mog
 *
 * @version $Id: FileListConverter.java,v 1.5 2003/12/23 13:38:21 mog Exp $
 */
public class FileListConverter {
	public static void main(String[] args) throws IOException {
		BasicConfigurator.configure();
		if(args.length != 0) {
			System.out.println("Converts from files.xml to files.mlst");
			return;
		}
		System.out.println("Converting files.xml to files.mlst");
		System.out.println("This might take a while for large filelists and/or slow servers, have patience...");
		LinkedRemoteFile root = SlaveManagerImpl.loadJDOMFileDatabase(SlaveManagerImpl.loadRSlaves(), null);
		MLSTSerialize.serialize(root, new PrintStream(new FileOutputStream("files.mlst")));
		System.out.println("Completed, have a nice day");
	}
}
