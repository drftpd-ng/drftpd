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
package org.drftpd.plugins.mediainfo;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.ReloadEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.mediainfo.event.MediaInfoEvent;
import org.drftpd.plugins.mediainfo.vfs.MediaInfoVFSData;
import org.drftpd.protocol.mediainfo.common.MediaInfo;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * @author scitz0
 */
public class MediaInfoPostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(MediaInfoPostHook.class);
    
    private ArrayList<String> _exclSections = new ArrayList<>();
	private ArrayList<String> _extensions = new ArrayList<>();

	public void initialize(StandardCommandManager manager) {
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
    }

	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
			// STOR Failed, skip
			return;
		}

		if (MediaInfoUtils.getValidFileExtension(request.getArgument(), _extensions) == null)
			return;

		DirectoryHandle workingDir = request.getCurrentDirectory();
		FileHandle file;
		try {
			file = response.getObject(DataConnectionHandler.TRANSFER_FILE);
		} catch (KeyNotFoundException e) {
			// Couldn't find the uploaded file
			return;
		}

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
		if (_exclSections.contains(sec.getName().toLowerCase()))
			return;

        MediaInfo mediaInfo;
		MediaInfoVFSData mediaData = new MediaInfoVFSData(file);
		try {
			mediaInfo = mediaData.getMediaInfo();
		} catch (FileNotFoundException e) {
			logger.warn(e.getMessage());
			return;
		} catch (IOException e) {
			logger.warn(e.getMessage());
			return;
		} catch (NoAvailableSlaveException e) {
			logger.warn(e.getMessage());
			return;
		} catch (SlaveUnavailableException e) {
			logger.warn(e.getMessage());
			return;
		}

		GlobalContext.getEventService().publishAsync(new MediaInfoEvent(mediaInfo, workingDir));
	}

    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
        loadConf();
	}
	
}
