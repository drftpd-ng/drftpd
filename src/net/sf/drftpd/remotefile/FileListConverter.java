package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 *
 * @version $Id: FileListConverter.java,v 1.7 2004/02/03 01:04:06 mog Exp $
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
		LinkedRemoteFile root = FileListConverter.loadJDOMFileDatabase(SlaveManagerImpl.loadRSlaves(), null);
		MLSTSerialize.serialize(root, new SafeFileWriter("files.mlst"));
		System.out.println("Completed, have a nice day");
	}

	public static LinkedRemoteFile loadJDOMFileDatabase(
		List rslaves,
		ConnectionManager cm)
		throws FileNotFoundException {
		return JDOMSerialize.unserialize(
			cm,
			new FileReader("files.xml"),
			rslaves);
	}
}
