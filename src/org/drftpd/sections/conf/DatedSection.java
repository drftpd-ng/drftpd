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

import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.remotefile.FileUtils;

import org.drftpd.sections.SectionInterface;

import java.io.FileNotFoundException;

import java.text.SimpleDateFormat;

import java.util.Collection;
import java.util.Date;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: DatedSection.java,v 1.10 2004/09/25 03:48:40 mog Exp $
 */
public class DatedSection implements SectionInterface {
    private String _basePath;
    private SimpleDateFormat _dateFormat;
    private SectionManager _mgr;
    private String _name;

    public DatedSection(SectionManager mgr, int i, Properties p) {
        _mgr = mgr;
        _name = FtpConfig.getProperty(p, i + ".name");
        _basePath = FtpConfig.getProperty(p, i + ".path");

        if (!_basePath.endsWith("/")) {
            _basePath += "/";
        }

        _dateFormat = new SimpleDateFormat(FtpConfig.getProperty(p, i +
                    ".dated"));
        getBaseFile();
    }

    //TODO schedule nightly/weekly/etc. creation of dated dirs
    public LinkedRemoteFile getBaseFile() {
        try {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .lookupFile(_basePath);
        } catch (FileNotFoundException e) {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .createDirectories(_basePath);
        }
    }

    public LinkedRemoteFileInterface getFile() {
        String dateDir = _dateFormat.format(new Date());

        try {
            return getBaseFile().lookupFile(dateDir);
        } catch (FileNotFoundException e) {
            return getBaseFile().createDirectories(dateDir);
        }
    }

    public Collection getFiles() {
        return getBaseFile().getDirectories();
    }

    public LinkedRemoteFileInterface getFirstDirInSection(
        LinkedRemoteFileInterface dir) {
        try {
            return FileUtils.getSubdirOfDirectory(getFile(), dir);
        } catch (FileNotFoundException e) {
            return dir;
        }
    }

    public String getName() {
        return _name;
    }

    public String getPath() {
        return getFile().getPath();
    }
}
