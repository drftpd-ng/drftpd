/*
 * Created on 2003-jul-13
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.queues;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

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
	Vector nukes = new Vector();
	
	public NukeEvent get(String path) throws FileNotFoundException {
		for (Iterator iter = nukes.iterator(); iter.hasNext();) {
			NukeEvent nuke= (NukeEvent) iter.next();
			if(nuke.getDirectory().equals(path)) return nuke;
		}
		throw new FileNotFoundException("No nukelog for: "+path);
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
	public Vector getAll() {
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
