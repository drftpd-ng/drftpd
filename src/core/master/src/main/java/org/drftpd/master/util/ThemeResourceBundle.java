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

import org.apache.commons.collections4.IteratorUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.drftpd.common.util.ConfigLoader.configPath;

class ThemeBundle {
    private final Path path;
    private final ExtendedPropertyResourceBundle bundle;

    public ThemeBundle(Path path, ExtendedPropertyResourceBundle bundle) {
        this.path = path;
        this.bundle = bundle;
    }

    public Path getPath() {
        return path;
    }

    public ExtendedPropertyResourceBundle getBundle() {
        return bundle;
    }
}

public class ThemeResourceBundle extends ResourceBundle {
    private static final Logger logger = LogManager.getLogger(ThemeResourceBundle.class);
    private final String _confDirectory;
    private final Map<String, ThemeBundle> combined = new HashMap<>();

    public ThemeResourceBundle(String confDirectory) {
        _confDirectory = confDirectory;
        loadProperties();
    }

    private void loadProperties() {
        try {
            String configurationPath = configPath(_confDirectory);
            Path targetPath = new File(configurationPath).toPath();
            List<Path> themeFiles;
            try (Stream<Path> pathStream = Files.walk(targetPath)) {
                themeFiles = pathStream.filter(f -> f.getFileName().toString().endsWith(".theme.default"))
                        .collect(Collectors.toList());
            }
            for (Path themeFile : themeFiles) {
                ExtendedPropertyResourceBundle bundle;
                try (FileInputStream theme = new FileInputStream(themeFile.toFile())) {
                    bundle = new ExtendedPropertyResourceBundle(theme);
                }
                List<String> keys = IteratorUtils.toList(bundle.getKeys().asIterator());
                String currentThemeFile = themeFile.getFileName().toString();
                String overrideThemeFileName = currentThemeFile.replace(".default", "");
                File overrideThemeFile = new File(themeFile.getParent().toString() + "/" + overrideThemeFileName);
                if (!overrideThemeFile.exists()) {
                    // Nothing to do here, no override file available
                    combineBundles(themeFile, bundle, keys);
                    continue;
                }
                ExtendedPropertyResourceBundle childBundle;
                try (FileInputStream child = new FileInputStream(overrideThemeFile)) {
                    childBundle = new ExtendedPropertyResourceBundle(child);
                }
                childBundle.setParent(bundle);
                // The override can define keys the .default does not have (e.g. per-type variants)
                List<String> allKeys = IteratorUtils.toList(childBundle.getKeys().asIterator());
                combineBundles(themeFile, childBundle, allKeys);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void combineBundles(Path themeFile, ExtendedPropertyResourceBundle bundle, List<String> keys) {
        for (String key : keys) {
            ThemeBundle existBundle = combined.get(key);
            if (existBundle != null) {
                logger.error("Theme key collision for key {} [{} and {}]", key, existBundle.getPath().toString(), themeFile.toString());
            }
            combined.put(key, new ThemeBundle(themeFile, bundle));
        }
    }

    @Override
    public Object handleGetObject(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
        ThemeBundle bundle = combined.get(key);
        if (bundle == null) {
            logger.error("No theme file available for key {}", key);
            return "No theme available";
        }
        // getObject, not handleGetObject: an override copy has to fall back to its .default parent
        return bundle.getBundle().getObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return Collections.enumeration(combined.keySet());
    }
}