package net.sf.drftpd.remotefile;

import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.InvalidDirectoryException;
import org.jdom.Element;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class JDOMRemoteFile extends RemoteFile {

	protected List files = null;
	
	/**
	 * Constructor for JDOMRemoteFileTree.
	 */
	public JDOMRemoteFile(String path, Element element) {
		name = element.getAttributeValue("name");
		//System.out.println("JDOMRemoteFileTree("+path+", "+name+")");
		if(element.getName().equals("directory")) {
			isDirectory = true;
			isFile = false;
			files = element.getChild("contents").getChildren();
		}
		if(element.getName().equals("file")) {
			isDirectory = false;
			isFile = true;
			checkSum = Long.parseLong(element.getChild("checksum").getText(), 16);
			length = Long.parseLong(element.getChild("size").getText());
		}
//		this.path = path;
		user = element.getChild("user").getText();
		group = element.getChild("group").getText();
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		JDOMRemoteFile listFiles[] = new JDOMRemoteFile[files.size()];
		int i2=0;
		for(Iterator i = files.iterator(); i.hasNext(); ) {
//			listFiles[i2++] = new JDOMRemoteFile(path+name+"/", (Element)i.next());
			listFiles[i2++] = new JDOMRemoteFile(null, (Element)i.next());
		}
		return listFiles;
	}

//	protected String path;
	protected String name;
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getName()
	 */
	public String getName() {
		return name;
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getParent()
	 */
	public String getParent() {
		throw new NoSuchMethodError("JDOMRemoteFile.getParent() not implemented");
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getPath()
	 */
	public String getPath() {
		return name;
		//throw new NoSuchMethodError("JDOMRemoteFile.getPath() not implemented");
	}
}
