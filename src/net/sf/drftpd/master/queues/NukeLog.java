/*
 * Created on 2003-jul-13
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.queues;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;

import org.jdom.Element;
import org.jdom.output.XMLOutputter;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class NukeLog {
	ArrayList nukes = new ArrayList();
	
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
			element.addContent(nuke.toXML());
		}
		return element;
	}
}
