package net.sf.drftpd.remotefile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.master.RemoteSlave;

import org.jdom.Element;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class JDOMRemoteFile extends RemoteFile {

	private static Logger logger =
		Logger.getLogger(JDOMRemoteFile.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}
	protected List files = null;
	protected Collection slaves;
	Hashtable allSlaves;

	private static Hashtable rslavesToHashtable(Collection rslaves) {
		Hashtable map = new Hashtable(rslaves.size());
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			map.put(rslave.getName(), rslave);
		}
		return map;
	}
	/**
	 * Constructor for JDOMRemoteFileTree.
	 */
	public JDOMRemoteFile(Element element, Collection rslaves) {
		this(element, rslavesToHashtable(rslaves));
	}

	public JDOMRemoteFile(Element element, Hashtable allSlaves) {
		this.allSlaves = allSlaves;
		this.name = element.getAttributeValue("name");
		if (element.getName().equals("directory")) {
			this.isDirectory = true;
			this.isFile = false;
			this.files = element.getChild("contents").getChildren();
		}
		if (element.getName().equals("file")) {
			this.isDirectory = false;
			this.isFile = true;
			this.checkSum =
				Long.parseLong(element.getChild("checksum").getText(), 16);
		}
		this.length = Long.parseLong(element.getChild("size").getText());
		this.owner = element.getChild("user").getText();
		this.group = element.getChild("group").getText();
		this.lastModified =
			Long.parseLong(element.getChild("lastModified").getText());

		this.slaves = new ArrayList();
		for (Iterator iter =
			element.getChild("slaves").getChildren("slave").iterator();
			iter.hasNext();
			) {
			Element slaveElement = (Element) iter.next();
			String slaveName = slaveElement.getChildText("name");
			RemoteSlave rslave = (RemoteSlave) this.allSlaves.get(slaveName);
			if (rslave == null) {
				logger.log(
					Level.WARNING,
					slaveName
						+ " not in slavelist, not adding file: "
						+ getName());
				continue;
			}
			this.slaves.add(rslave);
		}
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFileTree#listFiles()
	 */
	public RemoteFile[] listFiles() {
		JDOMRemoteFile listFiles[] = new JDOMRemoteFile[files.size()];
		int i2 = 0;
		for (Iterator i = files.iterator(); i.hasNext();) {
			listFiles[i2++] =
				new JDOMRemoteFile((Element) i.next(), this.allSlaves);
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
		//return name;
		throw new NoSuchMethodError("JDOMRemoteFile.getPath() not implemented");
	}

	public String toString() {
		StringBuffer ret = new StringBuffer();
		ret.append("[" + getClass().getName() + "[");
		//ret.append(slaves);
		if (isDirectory())
			ret.append("[directory: " + listFiles().length + "]");
		if (isFile())
			ret.append("[file: true]");
		//ret.append("isFile(): " + isFile() + " ");
		ret.append(getName());
		return ret.toString();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getFiles()
	 */
	public Collection getFiles() {
		ArrayList listFiles = new ArrayList(files.size());
		for (Iterator i = files.iterator(); i.hasNext();) {
			listFiles.add(
				new JDOMRemoteFile((Element) i.next(), this.allSlaves));
		}
		return listFiles;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return this.slaves;
	}

	protected long length;
	protected long lastModified;
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#length()
	 */
	public long length() {
		return this.length;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return this.lastModified;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#hasFile(java.lang.String)
	 */
	public boolean hasFile(String filename) {
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			JDOMRemoteFile file = (JDOMRemoteFile) iter.next();
			if(file.getName().equals(filename)) return true;
		}
		return false;
	}
}
