package net.sf.drftpd.master;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import net.sf.drftpd.RemoteFile;

import java.util.Hashtable;
import java.util.LinkedList;

public class CreateRemoteFiles {
    private static final int EXIT_FAILURE=1;

    public static void main(String args[]) {
	new File(System.getProperty("user.home")+"/dftpd").mkdir();
	serializeDir(new File("."));
    }

    public static void serializeDir(File dir) {
	File files[] = dir.listFiles();
	//Hashtable hashtable = new Hashtable(files.length);

	File odir = new File(System.getProperty("user.home")+"/dftpd/"+dir.getPath());
	odir.mkdir();
	File ofile = new File(odir.getPath()+"/dftpd-mog1");
	try {
	    FileOutputStream ostream = new FileOutputStream(ofile);
	    ObjectOutputStream p = new ObjectOutputStream(ostream);

	    for (int i=0; i<files.length; i++) {
		RemoteFile remotefile = new RemoteFile(files[i]);
		//hashtable.put(remotefile.getName(), remotefile);
		p.writeObject(remotefile);
		if(files[i].isDirectory()) {
		    serializeDir(files[i]);
		}
	    }
	    p.flush();
	    ostream.close();
	} catch(IOException e) {
	    e.printStackTrace();
	}
    }
}
