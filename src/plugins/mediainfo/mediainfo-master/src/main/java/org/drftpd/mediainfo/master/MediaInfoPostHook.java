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
package org.drftpd.mediainfo.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.ConfigType;
import org.drftpd.master.commands.dataconnection.DataConnectionHandler;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.mediainfo.common.MediaInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * @author scitz0
 */
public class MediaInfoPostHook {
	private static final Logger logger = LogManager.getLogger(MediaInfoPostHook.class);
    
    private ArrayList<String> _exclSections = new ArrayList<>();
	private ArrayList<String> _extensions = new ArrayList<>();

	public MediaInfoPostHook() {
        loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

    private void loadConf() {
		Properties cfg = ConfigLoader.loadPluginConfig("mediainfo.conf", ConfigType.MASTER);
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

	@CommandHook(commands = "doSTOR", priority = 100, type = HookType.POST)
	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
			// STOR Failed, skip
			return;
		}

		if (MediaInfoUtils.getValidFileExtension(request.getArgument(), _extensions) == null){
			return;
		}

		DirectoryHandle workingDir = request.getCurrentDirectory();
		FileHandle file;
		try {
			file = response.getObject(DataConnectionHandler.TRANSFER_FILE);
		} catch (KeyNotFoundException e) {
			// Couldn't find the uploaded file
			return;
		}

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
		if (_exclSections.contains(sec.getName().toLowerCase())) {
			return;
		}

        MediaInfo mediaInfo;
		MediaInfoVFSData mediaData = new MediaInfoVFSData(file);
		try {
			mediaInfo = mediaData.getMediaInfo();
		} catch (IOException | NoAvailableSlaveException | SlaveUnavailableException e) {
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
