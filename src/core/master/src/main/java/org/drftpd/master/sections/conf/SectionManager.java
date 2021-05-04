/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.sections.conf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sections.SectionManagerInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.reflections.Reflections;

import java.util.*;

/**
 * @author mog
 * @version $Id$
 */
public class SectionManager implements SectionManagerInterface {
    private static final Logger logger = LogManager.getLogger(SectionManager.class);

    private static final PlainSection EMPTYSECTION = new PlainSection("", GlobalContext.getGlobalContext().getRoot());

    private HashMap<String, SectionInterface> _sections;

    private boolean _mkdirs = false;

    private CaseInsensitiveHashMap<String, Class<? extends ConfigurableSectionInterface>> _typesMap;

    public SectionManager() {
        logger.debug("Loading conf section manager");
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

        for (SectionInterface section : _sections.values()) {
            if (string.startsWith(section.getBaseDirectory().getPath())
                    && (matchlen < section.getCurrentDirectory().getPath().length())) {
                match = section;
                matchlen = section.getCurrentDirectory().getPath().length();
            }
        }
        return match;
    }

    /*
     * Load the different Section Types specified in plugin.xml
     */
    private void initTypes() {
        CaseInsensitiveHashMap<String, Class<? extends ConfigurableSectionInterface>> typesMap = new CaseInsensitiveHashMap<>();

        Set<Class<? extends ConfigurableSectionInterface>> sectionsConf = new Reflections("org.drftpd")
                .getSubTypesOf(ConfigurableSectionInterface.class);
        for (Class<? extends ConfigurableSectionInterface> aClass : sectionsConf) {
            String sectionName = aClass.getSimpleName().replace("Section", "");
            typesMap.put(sectionName, aClass);
        }
        _typesMap = typesMap;
    }

    public void reload() {
        initTypes();
        Properties p = ConfigLoader.loadConfig("sections.conf");
        HashMap<String, SectionInterface> sections = new HashMap<>();
        _mkdirs = p.getProperty("make.section.dirs", "false").equals("true");

        for (int i = 1; ; i++) {
            String name = p.getProperty(i + ".name");
            if (name == null)
                break;
            String type = p.getProperty(i + ".type", "plain").trim();

            Class<?>[] SIG = {int.class, Properties.class};
            boolean notloaded = false;
            if (!_typesMap.containsKey(type)) {
                // Section Type does not exist
                logger.error("Section Type: {} wasn't loaded.", type);
                notloaded = true;
            } else {
                try {
                    Class<? extends ConfigurableSectionInterface> clazz = _typesMap.get(type);
                    ConfigurableSectionInterface section = clazz.getConstructor(SIG).newInstance(i, p);
                    sections.put(name, section);
                    if (_mkdirs) {
                        section.createSectionDir();
                    }
                } catch (Exception e) {
                    throw new FatalException("Unable To Load Section type: " + i + ".type = " + type);
                }
            }

            if (notloaded) {
                throw new FatalException("Unknown section type: " + i + ".type = " + type);
            }
        }
        _sections = sections;
    }

    public SectionInterface lookup(DirectoryHandle directory) {
        return lookup(directory.getPath());
    }
}
