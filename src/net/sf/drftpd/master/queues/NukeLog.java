package net.sf.drftpd.master.queues;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 * @author mog
 *
 * @version $Id: NukeLog.java,v 1.8 2003/12/23 13:38:20 mog Exp $
 */
public class NukeLog implements Serializable {
	private static Logger logger = Logger.getLogger(NukeLog.class);

	ArrayList nukes = new ArrayList();
	public NukeLog() {
	}
	
	public NukeEvent get(String path) throws ObjectNotFoundException {
		for (Iterator iter = nukes.iterator(); iter.hasNext();) {
			NukeEvent nuke= (NukeEvent) iter.next();
			if(nuke.getPath().equals(path)) return nuke;
		}
		throw new ObjectNotFoundException("No nukelog for: "+path);
	}
	
	public void remove(String path) throws ObjectNotFoundException {
		for (Iterator iter = nukes.iterator(); iter.hasNext();) {
			NukeEvent nuke= (NukeEvent) iter.next();
			if(nuke.getPath().equals(path)) {
				iter.remove();
				return;
			} 
		}
		throw new ObjectNotFoundException("No nukelog for: "+path);
	}
	
	public void add(NukeEvent nuke) {
		nukes.add(nuke);
//		try {
//			ObjOut out = new ObjOut(new FileWriter("nukelog.xml"));
//			out.writeObject(this);
//		} catch (IOException e) {
//			logger.warn("", e);
//		}
		XMLOutputter outputter = new XMLOutputter("    ", true);
		try {
			outputter.output(this.toXML(), new FileOutputStream("nukelog.xml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public List getAll() {
		return nukes;
	}
	public Element toXML() {
		Element element = new Element("nukes");
		for (Iterator iter = getAll().iterator(); iter.hasNext();) {
			NukeEvent nuke = (NukeEvent) iter.next();
			element.addContent(nuke.toJDOM());
		}
		return element;
	}
}
