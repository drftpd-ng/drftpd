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
package net.sf.drftpd.remotefile;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.RemoteSlave;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.remotefile.RemoteFileInterface;

import org.jdom.Element;

import org.jdom.output.XMLOutputter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id: JDOMRemoteFile.java,v 1.31 2004/11/09 18:59:51 mog Exp $
 */
public class JDOMRemoteFile implements RemoteFileInterface {
    private static final Logger logger = Logger.getLogger(JDOMRemoteFile.class.getName());
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
            } else if (element.getName().equals("file")) {
                try {
                    this.xfertime = Long.parseLong(element.getChildText(
                                "xfertime"));
                } catch (NumberFormatException ex) {
                    this.xfertime = 0L;
                }

                try {
                    this.checkSum = Long.parseLong(element.getChildText(
                                "checksum"), 16);
                } catch (NumberFormatException e) {
                }

                this.length = Long.parseLong(element.getChildText("size"));

                this.slaves = new ArrayList();

                for (Iterator iter = element.getChild("slaves")
                                            .getChildren("slave").iterator();
                        iter.hasNext();) {
                    Element slaveElement = (Element) iter.next();
                    String slaveName = slaveElement.getText();

                    if (slaveName == null) {
                        throw new NullPointerException(slaveElement +
                            " : slaveElement.getText() returned null");
                    }

                    RemoteSlave rslave = (RemoteSlave) this.allSlaves.get(slaveName);

                    if (rslave == null) {
                        logger.log(Level.WARN,
                            slaveName + " not in slavelist, not adding file: " +
                            getName());

                        continue;
                    }

                    // don't add duplicate slaves. shouldn't happen.
                    if (!this.slaves.contains(rslave)) {
                        this.slaves.add(rslave);
                    }
                }
            }

            this.owner = element.getChild("user").getText();
            this.group = element.getChild("group").getText();
            this.lastModified = Long.parseLong(element.getChild("lastModified")
                                                      .getText());
        } catch (NumberFormatException ex) {
            throw new FatalException(this +
                " has missing fields, try switching to files.xml.bak", ex);
        }
    }

    public long getCheckSumCached() {
        return this.checkSum;
    }

    public Collection getFiles() {
        ArrayList listFiles = new ArrayList(files.size());

        for (Iterator i = files.iterator(); i.hasNext();) {
            listFiles.add(new JDOMRemoteFile((Element) i.next(), this.allSlaves));
        }

        return listFiles;
    }

    public String getGroupname() {
        return this.group;
    }

    public RemoteFileInterface getLink() {
        throw new UnsupportedOperationException();
    }

    public String getLinkPath() {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return name;
    }

    public String getParent() {
        throw new UnsupportedOperationException(
            "JDOMRemoteFile.getParent() not implemented");
    }

    public String getPath() {
        throw new UnsupportedOperationException(
            "JDOMRemoteFile.getPath() not implemented");
    }

    public Collection getSlaves() {
        return this.slaves;
    }

    public String getUsername() {
        return this.owner;
    }

    public long getXfertime() {
        return xfertime;
    }

    public boolean isDeleted() {
        return false;
    }

    public boolean isDirectory() {
        return files != null;
    }

    public boolean isFile() {
        return files == null;
    }

    public boolean isLink() {
        return false;
    }

    public long lastModified() {
        return this.lastModified;
    }

    public long length() {
        return this.length;
    }

    public RemoteFileInterface[] listFiles() {
        ArrayList listFiles = new ArrayList();

        for (Iterator i = files.iterator(); i.hasNext();) {
            Element fileElement = (Element) i.next();

            if (fileElement.getName().equals("file") &&
                    (fileElement.getChild("slaves").getChildren().size() == 0)) {
                logger.warn(new XMLOutputter().outputString(fileElement) +
                    " has no slaves! skipping");

                continue;
            }

            listFiles.add(new JDOMRemoteFile(fileElement, this.allSlaves));
        }

        return (RemoteFileInterface[]) listFiles.toArray(new JDOMRemoteFile[0]);
    }

    public String toString() {
        StringBuffer ret = new StringBuffer();
        ret.append("[" + getClass().getName() + "[");

        //ret.append(slaves);
        if (isDirectory()) {
            ret.append("[directory: " + getFiles().size() + "]");
        }

        if (isFile()) {
            ret.append("[file: true]");
        }

        //ret.append("isFile(): " + isFile() + " ");
        ret.append(getName());

        return ret.toString();
    }
}
