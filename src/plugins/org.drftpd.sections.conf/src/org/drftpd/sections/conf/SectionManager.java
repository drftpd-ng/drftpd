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
package org.drftpd.sections.conf;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.FatalException;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @version $Id$
 */
public class SectionManager implements SectionManagerInterface {
	
	private static final Class<?>[] CONSTRUCTOR_SIG = new Class<?>[] { int.class, Properties.class };
	private static final PlainSection EMPTYSECTION = new PlainSection("", GlobalContext.getGlobalContext().getRoot());

	private HashMap<String, SectionInterface> _sections;

	private boolean _mkdirs = false;
	
	public SectionManager() {
		reload();
	}

	public SectionInterface getSection(String string) {
		SectionInterface s = _sections.get(string);

		if (s != null) {
			return s;
		}

		return EMPTYSECTION;
	}

	public Collection<SectionInterface> getSections() {
		return Collections.unmodifiableCollection(_sections.values());
	}
	
	public Map<String, SectionInterface> getSectionsMap() {
		return Collections.unmodifiableMap(_sections);
	}

	private SectionInterface lookup(String string) {
		int matchlen = 0;
		SectionInterface match = EMPTYSECTION;

		for (Iterator<SectionInterface> iter = _sections.values().iterator(); iter
				.hasNext();) {
			SectionInterface section = iter.next();

			if (string.startsWith(section.getBaseDirectory().getPath())
					&& (matchlen < section.getCurrentDirectory().getPath()
							.length())) {
				match = section;
				matchlen = section.getCurrentDirectory().getPath().length();
			}
		}
		return match;
	}
	public void reload() {
		Properties p = new Properties();
		HashMap<String, SectionInterface> sections = new HashMap<String, SectionInterface>();
		FileInputStream stream = null;
        try {
        	stream = new FileInputStream("conf/sections.conf");
			p.load(stream);
		} catch (IOException e) {
			throw new FatalException(e);
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
				}
			}
		}
		
		_mkdirs = p.getProperty("make.section.dirs", "false").equals("true");

		for (int i = 1;; i++) {
				String name = p.getProperty(i + ".name");
				if (name == null)
					break;
				String type = p.getProperty(i + ".type", "plain").trim();
				try {
					Class<?> clazz = Class.forName("org.drftpd.sections.conf."
							+ type.substring(0, 1).toUpperCase()
							+ type.substring(1) + "Section");
					ConfigurableSectionInterface section = (ConfigurableSectionInterface) clazz
							.getDeclaredConstructor(CONSTRUCTOR_SIG)
							.newInstance(
									new Object[] { Integer.valueOf(i), p });
					sections.put(name, section);
					if (_mkdirs) {
						section.createSectionDir();
					}
				} catch (Exception e) {
					throw new FatalException("Unknown section type: " + i
						+ ".type = " + type, e);
				}
			}
		_sections = sections;
	}

	public SectionInterface lookup(DirectoryHandle directory) {
		return lookup(directory.getPath());
	}
}
