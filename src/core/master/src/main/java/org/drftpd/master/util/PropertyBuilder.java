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
package org.drftpd.master.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author mog
 * @version $Id$
 */
public class PropertyBuilder {
    private final File _baseFile;

    private final int _prefixLength;

    public PropertyBuilder(File file) {
        _baseFile = file;
        _prefixLength = file.getPath().length() + 1;
    }

    public static void main(String[] args) throws IOException {
        PropertyBuilder pb = new PropertyBuilder(new File(args[0]));
        pb.findAndReadFiles();
    }

    public void findAndReadFiles() throws IOException {
        findAndReadFiles(_baseFile);
    }

    private void findAndReadFiles(File file) throws IOException, java.io.FileNotFoundException {
        File[] files = file.listFiles();

        for (File file2 : files) {
            if (file2.isDirectory()) {
                findAndReadFiles(file2);
            } else if (file2.getName().endsWith(".properties")) {
                String classname = file2.getPath().substring(_prefixLength);
                classname = classname.replaceAll("\\.properties$", "");
                classname = classname.replace(File.separatorChar, '.');

                try (BufferedReader in = new BufferedReader(new FileReader(file2))) {
                    System.out.println("## START: " + classname);

                    String line;

                    while ((line = in.readLine()) != null) {
                        if (line.trim().equals("") || line.startsWith("#")) {
                            System.out.println(line);

                            continue;
                        }

                        System.out.println(classname + "." + line);
                    }
                }
            }
        }
    }
}
