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
package org.drftpd.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public class ConfigLoader {

    public static String configPath(String confDirectory) {
        String configPath = System.getenv("DRFTPD_CONFIG_PATH"); // TODO: Do we even use this? or is this a feature we forgot about?
        return configPath != null ? (configPath + "/" + confDirectory) : confDirectory;
    }

    public static BufferedReader loadTextFile(String fileName) throws IOException {
        String filePathName = configPath("config/themes/text/" + fileName);
        File textFile = new File(filePathName);
        if (textFile.exists()) {
            return Files.newBufferedReader(textFile.toPath());
        }
        File distFile = new File(filePathName + ".dist");
        if (!distFile.exists()) {
            throw new RuntimeException("Cant find text file " + filePathName + ".dist");
        }
        return Files.newBufferedReader(distFile.toPath());
    }

    public static File loadConfigFile(String fileName, boolean isPlugin) {
        String filePathName = (isPlugin ? "config/plugins/" : "config/") + fileName;
        String fullPath = configPath(filePathName);
        File conf = new File(fullPath);
        if (conf.exists()) {
            return conf;
        }
        File distFile = new File(fullPath + ".dist");
        if (!distFile.exists()) {
            throw new RuntimeException("Cant find config file " + filePathName + ".dist");
        }
        return distFile;
    }

    public static Properties loadPropertyConfig(String fileName, boolean isPlugin) {
        File configFile = loadConfigFile(fileName, isPlugin);
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            p.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return p;
    }

    public static Properties loadConfig(String fileName) {
        return loadPropertyConfig(fileName, false);
    }

    public static Properties loadPluginConfig(String fileName) {
        return loadPropertyConfig(fileName, true);
    }

}
