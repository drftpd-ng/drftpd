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

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.BasicConfigurator;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.List;


/**
 * @author mog
 * @version $Id: FileListConverter.java,v 1.12 2004/08/03 20:14:02 zubov Exp $
 */
public class FileListConverter {
    public static void main(String[] args) throws IOException {
        BasicConfigurator.configure();

        if (args.length != 0) {
            System.out.println("Converts from files.xml to files.mlst");

            return;
        }

        System.out.println("Converting files.xml to files.mlst");
        System.out.println(
            "This might take a while for large filelists and/or slow servers, have patience...");

        LinkedRemoteFileInterface root = FileListConverter.loadJDOMFileDatabase( /* only null so it compiles */
                null, null);
        MLSTSerialize.serialize(root, new SafeFileWriter("files.mlst"));
        System.out.println("Completed, have a nice day");
    }

    public static LinkedRemoteFileInterface loadJDOMFileDatabase(List rslaves,
        ConnectionManager cm) throws FileNotFoundException {
        return JDOMSerialize.unserialize(cm, new FileReader("files.xml"),
            rslaves);
    }
}
