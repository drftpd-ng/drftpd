package net.sf.drftpd.remotefile;

import java.io.IOException;

import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 *
 * @version $Id: FileListConverter.java,v 1.6 2004/01/20 06:59:01 mog Exp $
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
		MLSTSerialize.serialize(root, new SafeFileWriter("files.mlst"));
		System.out.println("Completed, have a nice day");
	}
}
