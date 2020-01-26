/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.mediainfo.list;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.GlobalContext;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.mediainfo.MediaInfoUtils;
import org.drftpd.plugins.mediainfo.vfs.MediaInfoVFSData;
import org.drftpd.protocol.mediainfo.common.MediaInfo;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author scitz0
 */
public class MediaInfoList implements AddListElementsInterface {
	private static final Logger logger = LogManager.getLogger(MediaInfoList.class);

	private ArrayList<String> _exclSections = new ArrayList<>();
	private ArrayList<String> _extensions = new ArrayList<>();
	private boolean _mediaBarEnabled;
	private boolean _mediaBarIsDir;

	public void initialize() {
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	private void loadConf() {
        Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("mediainfo.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/mediainfo.conf not found");
			return;
		}
		_exclSections.clear();
		_extensions.clear();
		for (String section : cfg.getProperty("sections.exclude","").split(" ")) {
			section = section.toLowerCase().trim();
			if (!section.isEmpty()) {
				_exclSections.add(section);
			}
		}
		for (String extension : cfg.getProperty("extensions","").split(" ")) {
			extension = extension.toLowerCase().trim();
			if (!extension.isEmpty()) {
				_extensions.add(extension);
			}
		}
		_mediaBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("mediainfo.conf").getProperty("mediabar.enabled", "true").
				equalsIgnoreCase("true");
		_mediaBarIsDir = GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("mediainfo.conf").getProperty("mediabar.directory").equalsIgnoreCase("true");
    }

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {

		ArrayList<String> mediaBarEntries = new ArrayList<>();

		if (!_mediaBarEnabled || dir.isRoot()) {
			return container;
		}

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		if (_exclSections.contains(sec.getName().toLowerCase()))
			return container;

		try {
			for (FileHandle file : dir.getFilesUnchecked()) {
				String extension = MediaInfoUtils.getValidFileExtension(file.getName(),_extensions);
				if (extension != null) {
					// Valid extension, get mediainfo for this file
					mediaBarEntries.addAll(getMediaBarEntries(file,container,extension));
				}
			}
		} catch (FileNotFoundException e) {
			// Cant find dir
			return container;
		} catch (NoMediaInfoAvailableException e) {
			// Cant find info for this mediafile
			return container;
		}

		for (String mediaBarElement : mediaBarEntries) {
			if (mediaBarElement.trim().isEmpty()) {
				continue;
			}
			try {
				container.getElements().add(
						new LightRemoteInode(mediaBarElement, "drftpd", "drftpd", _mediaBarIsDir, dir.lastModified(), 0L));
			} catch (FileNotFoundException e) {
				// dir was deleted during list operation
			}
		}

		return container;
	}

	private ArrayList<String> getMediaBarEntries(FileHandle file, ListElementsContainer container,
												  String ext) throws NoMediaInfoAvailableException {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";

		try {
			MediaInfoVFSData mediaData = new MediaInfoVFSData(file);
			MediaInfo mediaInfo = mediaData.getMediaInfo();

			ArrayList<String> mediaBarEntries = new ArrayList<>();
			ReplacerEnvironment env = new ReplacerEnvironment();
			for (HashMap<String,String> props : mediaInfo.getVideoInfos()) {
				for (Map.Entry<String,String> field : props.entrySet()) {
					String value = field.getValue();
					value = MediaInfoUtils.fixOutput(value);
					env.add("v_"+field.getKey(), value);
				}
				if (!props.containsKey("Language")) {
					env.add("Language", "Unknown");
				}
			}
			for (HashMap<String,String> props : mediaInfo.getAudioInfos()) {
				for (Map.Entry<String,String> field : props.entrySet()) {
					String value = field.getValue();
					value = MediaInfoUtils.fixOutput(value);
					env.add("a_"+field.getKey(), value);
				}
				if (!props.containsKey("Language")) {
					env.add("a_Language", "Unknown");
				}
			}
			StringBuilder subs = new StringBuilder();
			for (HashMap<String,String> props : mediaInfo.getSubInfos()) {
				if (props.containsKey("Language")) {
					subs.append(props.get("Language")).append("_");
				} else {
					// Found sub but with unknown language, add with Unknown as language
					subs.append("Unknown_");
				}
			}
			if (subs.length() != 0) {
				env.add("s_Languages", subs.substring(0,subs.length()-1));
			} else {
				env.add("s_Languages", "NA");
			}

			if (!mediaInfo.getVideoInfos().isEmpty()) {
				mediaBarEntries.add(container.getSession().jprintf(bundle,
						keyPrefix+ext+"bar.video",env,container.getUser()));
			}
			if (!mediaInfo.getAudioInfos().isEmpty()) {
				mediaBarEntries.add(container.getSession().jprintf(bundle,
						keyPrefix+ext+"bar.audio",env,container.getUser()));
			}
			if (subs.length() != 0) {
				mediaBarEntries.add(container.getSession().jprintf(bundle,
						keyPrefix+ext+"bar.sub",env,container.getUser()));
			}

			return mediaBarEntries;
		} catch (FileNotFoundException e) {
			// Error fetching media info, ignore
		} catch (IOException e) {
			// Error fetching media info, ignore
		} catch (NoAvailableSlaveException e) {
			// Error fetching media info, ignore
		} catch (SlaveUnavailableException e) {
			// Error fetching media info, ignore
		}
		throw new NoMediaInfoAvailableException();
	}

	public void unload() {
		// The plugin is unloading so stop asking for events
		AnnotationProcessor.unprocess(this);
	}
}
