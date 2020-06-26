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
package org.drftpd.master.sitebot.announce;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.DirectoryFtpEvent;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.*;

public class StoreAnnouncer extends AbstractAnnouncer {

    private static final Logger logger = LogManager.getLogger(StoreAnnouncer.class);

    private AnnounceConfig _config;

    private ResourceBundle _bundle;

    private List<String> _storeGroups;

    public void initialise(AnnounceConfig config, ResourceBundle bundle) {
        _config = config;
        _bundle = bundle;

        _storeGroups = new ArrayList<>();
        loadConf();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public void stop() {
        AnnotationProcessor.unprocess(this);
    }

    @EventSubscriber
    public void onReloadEvent() {
        logger.info("Received reload event, reloading");
        loadConf();
    }

    private void loadConf() {
        Properties cfg = ConfigLoader.loadPluginConfig(getConfDir() + "irc.announce.conf");
        _storeGroups.clear();
        for (int i = 1; ; i++) {
            String storeGroupPattern = cfg.getProperty("store.path." + i);
            if (storeGroupPattern == null) {
                break;
            }
            _storeGroups.add(storeGroupPattern);
        }
    }

    public String[] getEventTypes() {
        return new String[]{"store"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onDirectoryFtpEvent(DirectoryFtpEvent dirEvent) {
        if ("STOR".equals(dirEvent.getCommand())) {
            outputDirectorySTOR((TransferEvent) dirEvent);
        }
    }

    private void outputDirectorySTOR(TransferEvent event) {
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);

        for (String storeGroupPattern : _storeGroups) {
            if (event.getTransferFile().getName().toLowerCase().matches(storeGroupPattern)) {
                // There is a store.path.x that match this file, lets get PathWriter
                AnnounceWriter writer = _config.getPathWriter("store", event.getTransferFile());
                if (writer != null) {
                    fillEnvSection(env, event, writer);
                    sayOutput(ReplacerUtils.jprintf("store", env, _bundle), writer);
                }
            }
        }
    }

    private void fillEnvSection(Map<String, Object> env, TransferEvent event, AnnounceWriter writer) {
        DirectoryHandle dir = event.getDirectory();
        FileHandle file = event.getTransferFile();
        env.put("user", event.getUser().getName());
        env.put("group", event.getUser().getGroup());
        env.put("section", writer.getSectionName(dir));
        env.put("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getColor());
        env.put("path", writer.getPath(dir));
        env.put("file", file.getName());
        String ext = getFileExtension(file);
        env.put("ext", ext);
        env.put("extLowerCase", ext.toLowerCase());
        env.put("extUpperCase", ext.toUpperCase());
        try {
            env.put("size", Bytes.formatBytes(file.getSize()));
            long xferSpeed = 0L;
            if (file.getXfertime() > 0) {
                xferSpeed = file.getSize() / file.getXfertime();
            }
            env.put("speed", Bytes.formatBytes(xferSpeed * 1000) + "/s");
        } catch (FileNotFoundException e) {
            // The file no longer exists, just fail out of the method
        }
    }

    private String getFileExtension(FileHandle file) {
        String fileName = file.getName();
        if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        } else {
            return "";
        }
    }
}
