package net.sf.drftpd.remotefile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.RemoteSlave;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class JDOMRemoteFile implements RemoteFileInterface {

	private static Logger logger =
		Logger.getLogger(JDOMRemoteFile.class.getName());

	Hashtable allSlaves;
	private long checkSum;
	protected List files = null;
	private String group;
	protected long lastModified;

	protected long length;

	//	protected String path;
	protected String name;
	private String owner;
	protected Collection slaves;
	private long xfertime;
	
	/**
	 * Constructor for JDOMRemoteFileTree.
	 */
	public JDOMRemoteFile(Element element, Collection rslaves) {
		this(element, RemoteSlave.rslavesToHashtable(rslaves));
	}

	public JDOMRemoteFile(Element element, Hashtable allSlaves) {
		try {
		this.allSlaves = allSlaves;
		this.name = element.getAttributeValue("name");
		if (element.getName().equals("directory")) {
			this.files = element.getChild("contents").getChildren();
		}
		else if (element.getName().equals("file")) {
			try {
			this.xfertime = Long.parseLong(element.getChildText("xfertime"));
			} catch(NumberFormatException ex) {
				this.xfertime = 0L;
			}
			try {
				this.checkSum =
				Long.parseLong(element.getChildText("checksum"), 16);
			} catch(NumberFormatException e) {
			}
			this.length = Long.parseLong(element.getChildText("size"));

			this.slaves = new ArrayList();
			for (Iterator iter =
				element.getChild("slaves").getChildren("slave").iterator();
				iter.hasNext();
				) {
				Element slaveElement = (Element) iter.next();
				String slaveName = slaveElement.getText();
				if(slaveName == null) throw new NullPointerException(slaveElement+" : slaveElement.getText() returned null");
				RemoteSlave rslave = (RemoteSlave) this.allSlaves.get(slaveName);
				if (rslave == null) {
					logger.log(
						Level.WARN,
						slaveName
							+ " not in slavelist, not adding file: "
							+ getName());
					continue;
				}
				// don't add duplicate slaves. shouldn't happen.
				if(!this.slaves.contains(rslave)) this.slaves.add(rslave);
			}
		}
		this.owner = element.getChild("user").getText();
		this.group = element.getChild("group").getText();
		this.lastModified =
			Long.parseLong(element.getChild("lastModified").getText());

		} catch(NumberFormatException ex) {
			throw new FatalException(this+" has missing fields, try switching to files.xml.bak", ex);
		}
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFileInterface#getCheckSum()
	 */
	public long getCheckSumCached() {
		return this.checkSum;
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
	 * @see net.sf.drftpd.remotefile.RemoteFileInterface#getGroupname()
	 */
	public String getGroupname() {
		return this.group;
	}
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
		throw new UnsupportedOperationException("JDOMRemoteFile.getParent() not implemented");
	}

	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#getPath()
	 */
	public String getPath() {
		//return name;
		throw new UnsupportedOperationException("JDOMRemoteFile.getPath() not implemented");
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getSlaves()
	 */
	public Collection getSlaves() {
		return this.slaves;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFileInterface#getUsername()
	 */
	public String getUsername() {
		return this.owner;
	}
//	/* (non-Javadoc)
//	 * @see net.sf.drftpd.remotefile.RemoteFile#hasFile(java.lang.String)
//	 */
//	public boolean hasFile(String filename) {
//		for (Iterator iter = files.iterator(); iter.hasNext();) {
//			JDOMRemoteFile file = (JDOMRemoteFile) iter.next();
//			if(file.getName().equals(filename)) return true;
//		}
//		return false;
//	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#getXfertime()
	 */
	public long getXfertime() {
		return xfertime;
	}

	public boolean isDirectory() {
		return files != null;
	}
	public boolean isFile() {
		return files == null;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.remotefile.RemoteFile#lastModified()
	 */
	public long lastModified() {
		return this.lastModified;
	}
	/**
	 * @see net.sf.drftpd.remotefile.RemoteFile#length()
	 */
	public long length() {
		return this.length;
	}

	public RemoteFileInterface[] listFiles() {
		//JDOMRemoteFile listFiles[] = new JDOMRemoteFile[files.size()];
		ArrayList listFiles = new ArrayList();
		for (Iterator i = files.iterator(); i.hasNext();) {
			Element fileElement = (Element)i.next();
			
			if(fileElement.getName().equals("file") && fileElement.getChild("slaves").getChildren().size() == 0) {
				System.out.println(new XMLOutputter().outputString(fileElement)+" has no slaves! skipping");
				continue;
			} 
			listFiles.add(new JDOMRemoteFile(fileElement, this.allSlaves));
		}
		return (RemoteFileInterface[])listFiles.toArray(new JDOMRemoteFile[0]);
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

	public boolean isDeleted() {
		return false;
	}

}
