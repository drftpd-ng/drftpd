/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
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
package org.drftpd.tools.installer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.FileUtils;
import org.java.plugin.ObjectFactory;
import org.java.plugin.PathResolver;
import org.java.plugin.registry.Identity;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.util.IoUtil;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author djb61
 * @author Uses code from JPF by Dmitry Olshansky - http://jpf.sourceforge.net
 * @version $Id$
 */
public class PluginParser {

	private static final FileUtils fileUtils = FileUtils.getFileUtils();
	private static final Logger logger = LogManager.getLogger(PluginParser.class);

	private FileSet _fileSet = new FileSet();
	private File _baseDir;
	private PluginRegistry _registry;
	private PathResolver _pathResolver;

	public PluginParser() throws PluginParseException {
		_baseDir = new File(System.getProperty("user.dir"));
		_fileSet.setDir(_baseDir);
		// We don't need an ant Project for anything but it is required to use FileSet without getting NPE
		// so pass an unconfigured empty Project
		_fileSet.setProject(new Project());
		_fileSet.setIncludes(System.getProperty("includes"));
		initRegistry(true);
	}

	private void initRegistry(boolean usePathResolver) throws PluginParseException {
		ObjectFactory objectFactory = ObjectFactory.newInstance();
		_registry = objectFactory.createRegistry();
		File[] manifestFiles = getIncludedFiles();
		List<URL> manifestUrls = new LinkedList<>();
		final Map<String, URL> foldersMap = new HashMap<>();
        for (File manifestFile : manifestFiles) {
            try {
                URL manifestUrl = getManifestURL(manifestFile);
                if (manifestUrl == null) {
                    logger.debug("Skipped file: {}", manifestFile);
                    continue;
                }
                manifestUrls.add(manifestUrl);
                logger.debug("Added URL: {}", manifestUrl);
                if (usePathResolver) {
                    if ("jar".equals(manifestUrl.getProtocol())) {
                        foldersMap.put(manifestUrl.toExternalForm(),
                                IoUtil.file2url(manifestFile));
                    } else {
                        foldersMap.put(manifestUrl.toExternalForm(),
                                IoUtil.file2url(manifestFile.getParentFile()));
                    }
                }
            } catch (MalformedURLException mue) {
                throw new PluginParseException("can't create URL for file "
                        + manifestFile);
            }
        }
		final Map<String, Identity> processedPlugins;
		try {
			processedPlugins = _registry.register(
					manifestUrls.toArray(new URL[manifestUrls.size()]));
		} catch (Exception e) {
			throw new PluginParseException("can't register URLs");
		}
        logger.debug("Registry initialized, registered manifests: {} of {}", processedPlugins.size(), manifestUrls.size());
		if (usePathResolver) {
			_pathResolver = objectFactory.createPathResolver();
			for (Entry<String, Identity> entry : processedPlugins.entrySet()) {
				_pathResolver.registerContext(entry.getValue(),
						foldersMap.get(entry.getKey()));
			}
			logger.debug("Path resolver initialized");
		}
	}

	private File[] getIncludedFiles() {
		Set<File> result = new HashSet<>();
		for (String file
				: _fileSet.getDirectoryScanner().getIncludedFiles()) {
			if (file != null) {
				result.add(fileUtils.resolveFile(
						_fileSet.getDir(), file));
			}
		}
		return result.toArray(new File[result.size()]);
	}

	private URL getManifestURL(final File file) throws MalformedURLException {
		if(file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
			URL url = new URL("jar:" + IoUtil.file2url(file).toExternalForm()
					+ "!/plugin.xml");
			if (IoUtil.isResourceExists(url)) {
				return url;
			}
			url = new URL("jar:" + IoUtil.file2url(file).toExternalForm()
					+ "!/plugin-fragment.xml");
			if (IoUtil.isResourceExists(url)) {
				return url;
			}
			url = new URL("jar:" + IoUtil.file2url(file).toExternalForm()
					+ "!/META-INF/plugin.xml");
			if (IoUtil.isResourceExists(url)) {
				return url;
			}
			url = new URL("jar:" + IoUtil.file2url(file).toExternalForm()
					+ "!/META-INF/plugin-fragment.xml");
			if (IoUtil.isResourceExists(url)) {
				return url;
			}
			return null;
		}
		return IoUtil.file2url(file);
	}

	public PluginRegistry getRegistry() {
		return _registry;
	}
}
