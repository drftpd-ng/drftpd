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
package se.mog.io;


/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * @version $Id: WindowsFileSystem.java,v 1.5 2004/08/03 20:14:11 zubov Exp $
 */
class WindowsFileSystem extends FileSystem {
    public File[] listMounts() {
        java.io.File[] roots = java.io.File.listRoots();
        File[] ret = new File[roots.length];

        for (int i = 0; i < roots.length; i++) {
            ret[i] = new File(roots[i]);
        }

        return ret;
    }

    public static void main(String[] args) {
        File[] mounts = new WindowsFileSystem().listMounts();

        for (int i = 0; i < mounts.length; i++) {
            System.out.println(mounts[i]);
        }
    }

    public native DiskFreeSpace getDiskFreeSpace(File file);
}
