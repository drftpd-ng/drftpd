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
            Stream<Path> pathStream = Files.walk(targetPath);
            List<Path> themeFiles = pathStream.filter(f -> f.getFileName().toString().endsWith(".theme.default"))
                    .collect(Collectors.toList());
            for (Path themeFile : themeFiles) {
                FileInputStream theme = new FileInputStream(themeFile.toFile());
                ExtendedPropertyResourceBundle bundle = new ExtendedPropertyResourceBundle(theme);
                List<String> keys = IteratorUtils.toList(bundle.getKeys().asIterator());
                try {
                    String currentThemeFile = themeFile.getFileName().toString();
                    String overrideThemeFileName = currentThemeFile.replace(".default", "");
                    String overrideThemeFile = themeFile.getParent().toString() + "/" + overrideThemeFileName;

                    FileInputStream child = new FileInputStream(new File(overrideThemeFile));
                    ExtendedPropertyResourceBundle childBundle = new ExtendedPropertyResourceBundle(child);
                    childBundle.setParent(bundle);
                    combineBundles(themeFile, childBundle, keys);
                } catch (FileNotFoundException e) {
                    // Nothing to do here, no override file available
                    combineBundles(themeFile, bundle, keys);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void combineBundles(Path themeFile, ExtendedPropertyResourceBundle bundle, List<String> keys) {
        for (String key : keys) {
            ThemeBundle existBundle = combined.get(key);
            if (existBundle != null) {
                logger.error("Theme key collision for key " + key
                        + " [" + existBundle.getPath().toString() + " and " + themeFile.toString() + "]");
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
            logger.error("No theme file available for key " + key);
            return "No theme available";
        }
        return bundle.getBundle().handleGetObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return Collections.enumeration(combined.keySet());
    }
}