/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
import java.io.File;

import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;
import net.sf.drftpd.slave.RootBasket;

public class DumpFile {

	public static void main(String[] args) throws Exception {
		for(int i=0; i<args.length; i++) {
			dumpFile(new FileRemoteFile("/", new File(args[i])));
			if(args.length-i != 1) System.out.println();
		}
	}
	public static void dumpFile(RemoteFile file) throws Exception {
		//shell script to generate below code with:
		//for x in canRead canWrite exists getAbsolutePath getCanonicalPath getName getParent getPath isAbsolute isDirectory isFile isHidden lastModified length ; do echo -e "\t\tSystem.out.println(\"$x(): \\\"\"+file.$x()+\"\\\"\");" ; done
		/*
		System.out.println("canRead(): \""+file.canRead()+"\"");
		System.out.println("canWrite(): \""+file.canWrite()+"\"");
		System.out.println("exists(): \""+file.exists()+"\"");
		System.out.println("getAbsolutePath(): \""+file.getAbsolutePath()+"\"");
		try {
			System.out.println("getCanonicalPath(): \""+file.getCanonicalPath()+"\"");
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		*/
		System.out.println("getName(): \""+file.getName()+"\"");
		System.out.println("getParent(): \""+file.getParent()+"\"");
		System.out.println("getPath(): \""+file.getPath()+"\"");
		//System.out.println("isAbsolute(): \""+file.isAbsolute()+"\"");
		System.out.println("isDirectory(): \""+file.isDirectory()+"\"");
		System.out.println("isFile(): \""+file.isFile()+"\"");
		System.out.println("isHidden(): \""+file.isHidden()+"\"");
		System.out.println("lastModified(): \""+file.lastModified()+"\"");
		System.out.println("length(): \""+file.length()+"\"");
	}
}
