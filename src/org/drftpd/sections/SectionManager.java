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
package org.drftpd.sections;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;

/**
 * @author mog
 * @version $Id: SectionManager.java,v 1.1 2004/02/16 22:39:43 mog Exp $
 */
public class SectionManager {
	private static final Class[] CONSTRUCTOR_SIG =
		new Class[] { SectionManager.class, int.class, Properties.class };
	private PlainSection _emptySection;
	private ConnectionManager _mgr;
	private Hashtable _sections = new Hashtable();
	
	public SectionManager(ConnectionManager mgr) {
		_mgr = mgr;
		reload();
	}

	public ConnectionManager getConnectionManager() {
		return _mgr;
	}

	public Section lookup(String string) {
		int matchlen = 0;
		Section match = _emptySection;

		for (Iterator iter = _sections.values().iterator(); iter.hasNext();) {
			Section section = (Section) iter.next();
			if (string.startsWith(section.getPath())
				&& matchlen < section.getPath().length()) {
				match = section;
				matchlen = section.getPath().length();
			}
		}
		return match;
	}

	private void reload() {
		_emptySection = new PlainSection(this, "", "/");
		Properties p = new Properties();
		try {
			p.load(new FileInputStream("conf/sections.conf"));
		} catch (IOException e) {
			throw new FatalException(e);
		}

		for (int i = 1;; i++) {
			String name = p.getProperty(i + ".name");
			if (name == null)
				break;
			String type = p.getProperty(i + ".type", "plain");
			try {
				Class clazz =
					Class.forName(
						"org.drftpd.sections."
							+ type.substring(0, 1).toUpperCase()
							+ type.substring(1)
							+ "Section");
				Section section =
					(Section) clazz.getDeclaredConstructor(
						CONSTRUCTOR_SIG).newInstance(
						new Object[] { this, new Integer(i), p });
				_sections.put(name, section);
			} catch (Exception e1) {
				throw new FatalException(
					"Unknown section type: " + i + ".type = " + type,
					e1);
			}
		}
	}
}
