package net.sf.drftpd.remotefile;
import java.util.Iterator;

import net.sf.drftpd.master.RemoteSlave;

import org.jdom.Element;

public class XMLSerialize {

	public static Element serialize(LinkedRemoteFile file) {
		Element element =
			new Element(file.isDirectory() ? "directory" : "file");
		//		if (file.isDirectory()) {
		//			element = new Element("directory");
		//		} else {
		//			element = new Element("file");
		//		}
		element.setAttribute("name", file.getName());

		element.addContent(new Element("user").setText(file.getOwner()));
		element.addContent(new Element("group").setText(file.getGroup()));

		element.addContent(
			new Element("size").setText(Long.toString(file.length())));

		element.addContent(
			new Element("lastModified").setText(
				Long.toString(file.lastModified())));

		if (file.isDirectory()) {
			Element contents = new Element("contents");
			for (Iterator i = file.getFiles().iterator(); i.hasNext();) {
				contents.addContent(serialize((LinkedRemoteFile) i.next()));
			}
			element.addContent(contents);
		}
		if(file.isFile()) {
			String checksum = "";
			checksum = Long.toHexString(file.getCheckSum(false));

			element.addContent(new Element("checksum").setText(checksum));
			
			element.addContent(new Element("xfertime").setText(Long.toString(file.getXfertime())));
		}

		Element slaves = new Element("slaves");
		for (Iterator i = file.getSlaves().iterator(); i.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) i.next();
			slaves.addContent(new Element("slave").setText(rslave.getName()));
		}
		element.addContent(slaves);

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
