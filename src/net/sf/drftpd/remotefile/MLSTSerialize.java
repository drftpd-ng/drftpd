/*
 * Created on 2003-sep-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.RemoteSlave;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class MLSTSerialize {
	public static void serialize(LinkedRemoteFile dir, Writer out) throws IOException {
		Collection files  = dir.getMap().values(); // get raw map with deleted flags
		ArrayList dirs = new ArrayList();
		out.write(dir.getPath()+":\r\n");
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			StringBuffer buf = new StringBuffer("+");
			if(file.isFile()) {
				buf.append("unique="+file.getCheckSum()+";");
				buf.append("size="+file.length()+";");
				buf.append("type=file;");
				for (Iterator iterator = file.getSlaves().iterator();
					iterator.hasNext();
					) {
					RemoteSlave rslave = (RemoteSlave) iterator.next();
					assert rslave != null;
					buf.append("x.slave="+rslave.getName()+";");
				}
			}
			if(file.isDirectory()) {
				buf.append("type=dir;");
				dirs.add(file);
			}
			buf.append("modify="+file.lastModified()+";");
			buf.append(" "+file.getName());
			out.write(buf.toString()+"\r\n");
		}
		out.write("\r\n");
		for (Iterator iter = dirs.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			serialize(file, out);
		}
	}
}
