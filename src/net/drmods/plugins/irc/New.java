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
package net.drmods.plugins.irc;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.RemoteFileInterface;
import org.drftpd.remotefile.RemoteFileLastModifiedComparator;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sitebot.IRCCommand;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 */
public class New extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Kick.class);
    private int _defaultCount;
    private int _maxCount;
    private String _dateFormat;
    private ArrayList<String>  _excludeSections;
    
    public New(GlobalContext gctx) {
        super(gctx);
		loadConf("conf/drmods.conf");
	}

	public void loadConf(String confFile) {
        Properties cfg = new Properties();
        FileInputStream file;
        try {
            file = new FileInputStream(confFile);
            cfg.load(file);
            file.close();
            String defaultCount = cfg.getProperty("new.default");
            String maxCount = cfg.getProperty("new.max");
            _dateFormat = cfg.getProperty("new.dateformat");
            String excludeSections = cfg.getProperty("new.exclude");
            if (defaultCount == null) {
                throw new RuntimeException("Unspecified value 'new.default' in " + confFile);        
            }
            if (maxCount == null) {
                throw new RuntimeException("Unspecified value 'new.max' in " + confFile);        
            }
            if (excludeSections == null) {
                throw new RuntimeException("Unspecified value 'new.exclude' in " + confFile);        
            }
            if (_dateFormat == null) {
                throw new RuntimeException("Unspecified value 'new.dateformat' in " + confFile);        
            }
            _defaultCount = Integer.parseInt(defaultCount);
            _maxCount = Integer.parseInt(maxCount);
            StringTokenizer st = new StringTokenizer(excludeSections);
            _excludeSections = new ArrayList<String>();
            while (st.hasMoreTokens())
                _excludeSections.add(st.nextToken());
        } catch (Exception e) {
            logger.error("Error reading " + confFile,e);
            throw new RuntimeException(e.getMessage());
        }
	}
    
    public ArrayList<String> doNew(String args, MessageCommand msgc) {
        ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        
        String secname = "*";
        int count = _defaultCount;
        StringTokenizer st = new StringTokenizer(args);
        if (st.countTokens() != 0) {
            String arg1 = st.nextToken();
            try {
                count = Integer.parseInt(arg1);
            } catch (NumberFormatException e) {
                secname = arg1;
                if (st.hasMoreTokens()) {
                    try {
                        count = Integer.parseInt(st.nextToken());
                    } catch (NumberFormatException e1) {}
                }
            }
        }
        
        if (count > _maxCount)
            count = _maxCount;
        
        Collection sections;
        if (secname.equals("*")) {
            sections = getGlobalContext().getSectionManager().getSections();
        } else {
            sections = new ArrayList();
            SectionInterface si = getGlobalContext().getSectionManager().getSection(secname);
            if (si.getName().equals("")) {
                env.add("input", secname);
                out.add(ReplacerUtils.jprintf("badsection", env, New.class));
                return out;
            }
            sections.add(si);
        }

        ArrayList<LinkedRemoteFileInterface> dirs = new ArrayList<LinkedRemoteFileInterface>();
        for (Iterator iter = sections.iterator(); iter.hasNext();) {
		    SectionInterface si = (SectionInterface) iter.next();
		    if (_excludeSections.contains(si.getName()))
		        continue;
		    for (LinkedRemoteFileInterface dir : si.getFile().getDirectories()) {
		        dirs.add(dir);
		    }
		}
        
        SimpleDateFormat dateFormat = new SimpleDateFormat(_dateFormat);
        
        Collections.sort(dirs, new RemoteFileLastModifiedComparator(true));
        int index = 0;
        for (RemoteFileInterface dir : dirs) {
            if (index >= count)
                break;
            env.add("dir",dir.getName());
            env.add("path",dir.getPath());
            env.add("date", dateFormat.format(new Date(dir.lastModified())));
            env.add("owner", dir.getUsername());
            env.add("group", dir.getGroupname());
            env.add("section", getGlobalContext().getSectionManager().lookup(dir.getPath()).getName());
            env.add("size", Bytes.formatBytes(dir.length()));
            env.add("pos", ""+(index+1));
            env.add("files",""+dir.getFiles().size());
            out.add(ReplacerUtils.jprintf("announce", env, New.class));
            index++;
        }
        
        if (out.isEmpty())
            out.add(ReplacerUtils.jprintf("usage", env, New.class));
        
        return out;
    }

}
