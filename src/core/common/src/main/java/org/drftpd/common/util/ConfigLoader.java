package org.drftpd.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

public class ConfigLoader {

    public static File loadConfigFile(String fileName, ConfigType type, boolean isPlugin) {
        String resourceFile;
        boolean useClassloaderConfig = "true".equals(System.getenv("DRFTPD_USE_CLASSLOADER_CONFIG"));
        String filePathName = (isPlugin ? "config/plugins/" : "config/") + fileName;
        if (useClassloaderConfig) {
            String name = type.label + "/" + filePathName;
            ClassLoader loader = ConfigLoader.class.getClassLoader();
            try {
                resourceFile = loader.getResource(name).getFile();
            } catch (Exception e) {
                URL resource = loader.getResource(name + ".dist");
                if (resource == null) throw new RuntimeException("Cant find config file " + name + ".dist");
                resourceFile = resource.getFile();
            }
            return new File(resourceFile);
        } else {
            File conf = new File(filePathName);
            if (conf.exists()) return conf;
            File distFile = new File(filePathName + ".dist");
            if (!distFile.exists()) throw new RuntimeException("Cant find config file " + filePathName + ".dist");
            return distFile;
        }
    }

    public static Properties loadPropertyConfig(String fileName, ConfigType type, boolean isPlugin) {
        File configFile = loadConfigFile(fileName, type, isPlugin);
        Properties p = new Properties();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(configFile);
            p.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignore fail on closure
                }
            }
        }
        return p;
    }

    public static Properties loadConfig(String fileName, ConfigType type) {
        return loadPropertyConfig(fileName, type, false);
    }

    public static Properties loadPluginConfig(String fileName, ConfigType type) {
        return loadPropertyConfig(fileName, type, true);
    }
}
