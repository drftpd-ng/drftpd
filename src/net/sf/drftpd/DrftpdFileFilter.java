package net.sf.drftpd;

import java.io.FileFilter;
import java.io.File;

public class DrftpdFileFilter implements FileFilter {
    public boolean accept(File file) {
	if(file.getName().equals(".dftpd") || file.getName().equals(".placeholder")) {
	    return false;
	}
	return true;
    }
}
