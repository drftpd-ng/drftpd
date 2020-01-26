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
package org.drftpd.commands.nuke.list;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.Bytes;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class NukeList implements AddListElementsInterface {
    private static final Logger logger = LogManager.getLogger(NukeList.class);

	public void initialize() { }

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {
        try {
            ResourceBundle bundle = container.getCommandManager().getResourceBundle();
            String keyPrefix = this.getClass().getName()+".";

			NukeData nukeData = dir.getPluginMetaData(NukeData.NUKEDATA);
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("reason", nukeData.getReason());
			env.add("amount", Bytes.formatBytes(nukeData.getAmount()));
			env.add("multiplier", ""+nukeData.getMultiplier());
			env.add("nuker", nukeData.getUser());
			String reasonBarName = container.getSession().jprintf(bundle,
					keyPrefix+"nuke.reason", env, container.getUser());
			try {
				container.getElements().add(
						new LightRemoteInode(reasonBarName, "drftpd", "drftpd", true, dir.lastModified(), 0L));
			} catch (FileNotFoundException e) {
				// dir was deleted during list operation
			}
        } catch (KeyNotFoundException ex) {
            // Dir not nuked, just continue
        } catch (FileNotFoundException ex) {
            logger.error("Could not find directory: {}", dir.getPath(), ex);
        }
		return container;
	}

    public void unload() {  
        AnnotationProcessor.unprocess(this);
    }
}
