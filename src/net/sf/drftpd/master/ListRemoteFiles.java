package net.sf.drftpd.master;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.EOFException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import net.sf.drftpd.RemoteFile;

public class ListRemoteFiles {
    public static void main(String args[]) {
	FileInputStream istream=null;
	ObjectInputStream p=null;
	Writer out=null;
	try {
	    istream = new FileInputStream(new File("dftpd-mog1"));
	    p = new ObjectInputStream(istream);
	    out = (Writer)new OutputStreamWriter(System.out);

	    while(true) {
		RemoteFile file = (RemoteFile)p.readObject();

		try {
		    //UnixDirectoryHelper.printLine(file, out);
		    out.flush();
		} catch(IOException ex) {
		    ex.printStackTrace();
		}
	    }
	} catch(EOFException ex) {
	    try {
			istream.close();
			out.flush();
	    } catch(IOException ex2) {
	    }
	    return;
	} catch(FileNotFoundException e) {
	    System.err.println("ERROR: Could not find dftpd-mog1 in current directory");
	    System.exit(1);
	} catch(IOException e) {
	    e.printStackTrace();
	} catch(ClassNotFoundException e) {
	    e.printStackTrace();
	}
    }
}
