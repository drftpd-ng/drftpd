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
package org.drftpd.tools.installer;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.listener.Log4jListener;
import org.apache.tools.ant.taskdefs.SubAnt;
import org.apache.tools.ant.types.FileList;
import org.java.plugin.registry.PluginRegistry;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginBuilder {

	private static final Logger logger = Logger.getLogger(PluginBuilder.class);
	private SubAnt _antBuilder = new SubAnt();

	public PluginBuilder(ArrayList<PluginData> toBuild, PluginRegistry registry, String installDir) {
		// Sort selected plugins into correct order for building
		PluginTools.reorder(toBuild,registry);
		// Create a list of build files for the selected plugins
		StringBuilder buildFiles = new StringBuilder();
		Iterator<PluginData> iter = toBuild.iterator();
		while (iter.hasNext()) {
			File pluginFile = null;
			try {
				pluginFile = new File(iter.next().getDescriptor().getLocation().toURI());
				buildFiles.append(pluginFile.getParent().substring(System.getProperty("user.dir").length()+1)+File.separator+"build.xml");
				if (iter.hasNext()) {
					buildFiles.append(",");
				}
			} catch (URISyntaxException e) {
				logger.warn("Error loading plugin buildfile",e);
			}
		}

		// Set list of build files in the ant builder
		FileList fileList = new FileList();
		fileList.setFiles(buildFiles.toString());
		_antBuilder.addFilelist(fileList);

		// Create an ant Project and initialize default tasks/types
		Project builderProject = new Project();
		builderProject.init();

		// Read custom project wide config data and configure ant Project to use it
		File setupFile = new File(System.getProperty("user.dir")+File.separator+"setup.xml");
		ProjectHelper.configureProject(builderProject,setupFile);

		// Add a log4j listener to the builder to allow logging of output
		Log4jListener buildListener = new Log4jListener();
		builderProject.addBuildListener(buildListener);

		// Set installation dir if required
		if (!installDir.equals("")) {
			builderProject.setProperty("installdir", installDir);
		} else {
			builderProject.setProperty("installdir", System.getProperty("user.dir"));
		}

		// Final setup of ant builder
		_antBuilder.setProject(builderProject);
		_antBuilder.setInheritall(true);
		_antBuilder.setFailonerror(true);
	}

	public void buildPlugins() {
		_antBuilder.execute();
	}
}
