/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
 * @author mog
 *
 * @version $Id$
 */
public class NukeLog {
    ArrayList nukes = new ArrayList();

    public NukeLog() {
    }

    public NukeEvent get(String path) throws ObjectNotFoundException {
        for (Iterator iter = nukes.iterator(); iter.hasNext();) {
            NukeEvent nuke = (NukeEvent) iter.next();

            if (nuke.getPath().equals(path)) {
                return nuke;
            }
        }

        throw new ObjectNotFoundException("No nukelog for: " + path);
    }

    public synchronized void remove(String path) throws ObjectNotFoundException {
        for (Iterator iter = nukes.iterator(); iter.hasNext();) {
            NukeEvent nuke = (NukeEvent) iter.next();

            if (nuke.getPath().equals(path)) {
                iter.remove();
                
                //Removes the <nuke> entry from nukelog.xml
                XMLOutputter outputter = new XMLOutputter("    ", true);

                try {
                    outputter.output(toXML(), new FileOutputStream("nukelog.xml"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return;
            }
        }

        throw new ObjectNotFoundException("No nukelog for: " + path);
    }

    public synchronized void add(NukeEvent nuke) {
        nukes.add(nuke);

        //		try {
        //			ObjOut out = new ObjOut(new FileWriter("nukelog.xml"));
        //			out.writeObject(this);
        //		} catch (IOException e) {
        //			logger.warn("", e);
        //		}
        XMLOutputter outputter = new XMLOutputter("    ", true);

        try {
            outputter.output(toXML(), new FileOutputStream("nukelog.xml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List getAll() {
        return nukes;
    }

    public synchronized Element toXML() {
        Element element = new Element("nukes");

        for (Iterator iter = getAll().iterator(); iter.hasNext();) {
            NukeEvent nuke = (NukeEvent) iter.next();
            element.addContent(nuke.toJDOM());
        }

        return element;
    }
    
    public boolean find_fullpath(String path) {
		for (Iterator iter = nukes.iterator(); iter.hasNext();) {
			NukeEvent nuke = (NukeEvent) iter.next();
			if (nuke.getPath().equals(path))
				return true;
		}
		return false;
	}
}
