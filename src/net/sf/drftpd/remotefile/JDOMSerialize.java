package net.sf.drftpd.remotefile;
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @version $Id: JDOMSerialize.java,v 1.4 2003/12/23 13:38:21 mog Exp $
 */
public class JDOMSerialize {
	private static Logger logger = Logger.getLogger(JDOMSerialize.class);

	public static LinkedRemoteFile unserialize(ConnectionManager cm, Reader in, List rslaves) throws FileNotFoundException {
		LinkedRemoteFile root;
		try {
			Document doc = new SAXBuilder().build(in);
			System.out.flush();
			JDOMRemoteFile xmlroot =
				new JDOMRemoteFile(doc.getRootElement(), rslaves);
			root =
				new LinkedRemoteFile(
					xmlroot,
					cm == null ? null : cm.getConfig());
		} catch (FileNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			logger.log(Level.FATAL, "Error loading \"files.xml\"", ex);
			throw new FatalException(ex);
		}
		return root;
	}
	public static Element serialize(LinkedRemoteFile file) {
		Element element =
			new Element(file.isDirectory() ? "directory" : "file");

		element.setAttribute("name", file.getName());

		element.addContent(new Element("user").setText(file.getUsername()));
		element.addContent(new Element("group").setText(file.getGroupname()));

		element.addContent(
			new Element("lastModified").setText(
				Long.toString(file.lastModified())));

		if (file.isFile()) {
			element.addContent(
				new Element("size").setText(Long.toString(file.length())));

			String checksum = "";
			checksum = Long.toHexString(file.getCheckSumCached());

			element.addContent(new Element("checksum").setText(checksum));

			element.addContent(
				new Element("xfertime").setText(
					Long.toString(file.getXfertime())));

			Element slaves = new Element("slaves");
			for (Iterator i = file.getSlaves().iterator(); i.hasNext();) {
				RemoteSlave rslave = (RemoteSlave) i.next();
				assert rslave != null;
				slaves.addContent(
					new Element("slave").setText(rslave.getName()));
			}
			element.addContent(slaves);

		}

		if (file.isDirectory()) {
			Element contents = new Element("contents");
			for (Iterator i = file.getFiles().iterator(); i.hasNext();) {
				contents.addContent(serialize((LinkedRemoteFile) i.next()));
			}
			element.addContent(contents);
		}

		return element;
	}
	//	public static Element serialize(LinkedRemoteFile file) {
	//		Element fileElement;
	//		if(file.isDirectory()) {
	//			fileElement = new Element("directory");
	//		} else {
	//			fileElement = new Element("file");
	//		}
	//		fileElement.setAttribute("name", file.getName());
	//		
	//		fileElement.addContent(new Element("user").setText(file.getOwner()));
	//		fileElement.addContent(new Element("group").setText(file.getGroup()));
	//		//if(file.isFile()) {
	//			fileElement.addContent(new Element("size").setText(Long.toString(file.length())));
	//		//} 
	//		fileElement.addContent(new Element("lastModified").setText(Long.toString(file.lastModified())));
	//		
	//		if(file.isDirectory()) {
	//			Element contents = new Element("contents");
	//			for(Iterator i = file.iterateFiles() ; i.hasNext(); ) {
	//				contents.addContent(serialize((LinkedRemoteFile)i.next()));
	//			}
	//			fileElement.addContent(contents);
	//		} else {
	//			String checksum = "";
	//			checksum = Long.toHexString(file.getCheckSum());
	//
	//			fileElement.addContent(new Element("checksum").setText(checksum));
	//		}
	//		
	//		Element slaves = new Element("slaves");
	//		for(Iterator i = file.getSlaves().iterator(); i.hasNext(); ) {
	//			RemoteSlave rslave = (RemoteSlave)i.next();
	//			slaves.addContent(new Element("slave").setAttribute("name", rslave.getName()));
	//		}
	//		fileElement.addContent(slaves);
	//		
	//		return fileElement;
	//	}
}
