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
package org.drftpd.remotefile;

import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import java.io.FileNotFoundException;

import java.util.Stack;


/**
 * @author mog
 * @version $Id: FileUtils.java,v 1.3 2004/09/25 03:48:39 mog Exp $
 */
public class FileUtils {
    /**
     * private constructor, not instantiable
     */
    private FileUtils() {
    }

    public static LinkedRemoteFileInterface getSubdirOfDirectory(
        LinkedRemoteFileInterface baseDir, LinkedRemoteFileInterface dir)
        throws FileNotFoundException {
        LinkedRemoteFileInterface dir2 = dir;

        while (dir != baseDir) {
            dir2 = dir;
            dir = dir.getParentFile();
        }

        return dir2;
    }

    public LinkedRemoteFileInterface getSubDirOfDirectoryLevel(
        LinkedRemoteFileInterface baseDir, LinkedRemoteFileInterface dir,
        int level) throws FileNotFoundException {
        Stack s = new Stack();

        while (s.peek() != baseDir) {
            s.push(((LinkedRemoteFileInterface) s.peek()).getParentFile());
        }

        return (LinkedRemoteFileInterface) s.peek();
    }
}
