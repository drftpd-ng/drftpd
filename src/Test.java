import java.io.File;

import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

public class Test {
	public static void main(String args[]) throws Exception {
		new LinkedRemoteFile(null, new FileRemoteFile("/home/mog/dc", new File("/home/mog/dc")));
		
	}
}
