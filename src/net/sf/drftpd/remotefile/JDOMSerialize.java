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
import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.List;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * @author mog
 * @version $Id: JDOMSerialize.java,v 1.7 2004/02/10 00:03:15 mog Exp $
 */
public class JDOMSerialize {
	private static final Logger logger = Logger.getLogger(JDOMSerialize.class);

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
		throw new UnsupportedOperationException("JDOMSerialize is deprecated");
	}
}
