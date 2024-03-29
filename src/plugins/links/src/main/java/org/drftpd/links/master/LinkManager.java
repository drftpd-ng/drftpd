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
package org.drftpd.links.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.drftpd.master.vfs.event.VirtualFileSystemInodeDeletedEvent;
import org.drftpd.master.vfs.event.VirtualFileSystemRenameEvent;
import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 * @version $Id: LinkManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class LinkManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(LinkManager.class);

    private CaseInsensitiveHashMap<String, Class<? extends LinkType>> _typesMap;

    private ArrayList<LinkType> _links;

    /*
     * Get the LinkManager Plugin
     */
    public static LinkManager getLinkManager() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof LinkManager) {
                return (LinkManager) plugin;
            }
        }
        throw new RuntimeException("LinkManager plugin is not loaded.");
    }

    @Override
    public void startPlugin() {
        AnnotationProcessor.process(this);
        loadConf();
    }

    @Override
    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        loadConf();
    }

    /*
     * Returns the archive type corresponding with the .conf file
     * and which Archive number the loop is on
     */
    private LinkType getLinkType(int count, String type, Properties props) {
        LinkType linkType = null;
        Class<?>[] SIG = {Properties.class, int.class, String.class};

        if (!_typesMap.containsKey(type)) {
            // if we can't find one filter that will be enough to break the whole chain.
            logger.error("Link Type: {} wasn't loaded.", type);

        } else {
            try {
                Class<? extends LinkType> clazz = _typesMap.get(type);
                linkType = clazz.getConstructor(SIG).newInstance(props, count, type.toLowerCase());
            } catch (Exception e) {
                logger.error("Unable to load LinkType for section {}.type={}", count, type, e);
            }
        }
        return linkType;
    }

    private void initTypes() {
        CaseInsensitiveHashMap<String, Class<? extends LinkType>> typesMap = new CaseInsensitiveHashMap<>();

        Set<Class<? extends LinkType>> LinkTypes = new Reflections("org.drftpd").getSubTypesOf(LinkType.class);
        for (Class<? extends LinkType> linkType : LinkTypes) {
            logger.debug("Loading link type - {}", linkType.getSimpleName());
            typesMap.put(linkType.getSimpleName(), linkType);
        }
        _typesMap = typesMap;
    }

    private void loadLinks() {
        ArrayList<LinkType> links = new ArrayList<>();
        Properties props = ConfigLoader.loadPluginConfig("links.conf");
        int count = 1;
        String type;
        while ((type = PropertyHelper.getProperty(props, count + ".type", null)) != null) {
            LinkType linkType = getLinkType(count, type, props);
            if (linkType != null) {
                logger.debug("Loaded link configuration item {} with type {}", count, type);
                links.add(linkType);
            } else {
                logger.warn("link configuration item {} with type {} not found, ignoring", count, type);
            }
            count++;
        }
        logger.info("Loaded {} link configuration items", links.size());
        _links = links;
    }

    public void loadConf() {
        initTypes();
        loadLinks();
    }

    /*
     * Returns a copy of all the current links types
     */
    public ArrayList<LinkType> getLinks() {
        return new ArrayList<>(_links);
    }

    /*
     * Used for deleting links on wipe/rmd
     */
    @EventSubscriber
    public void onVirtualFileSystemDeleteEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
        if (vfsevent.getInode().isDirectory()) {
            logger.debug("Caught VirtualFileSystemInodeDeletedEvent for directory {}, Checking links", vfsevent.getInode());
            for (LinkType link : getLinks()) {
                if ((link.getDeleteOnContains("wipe")) || (link.getDeleteOnContains("rmd"))) {
                    logger.debug("Checking [{}] for delete action for {}", link, vfsevent.getInode());
                    link.doDeleteLink(new DirectoryHandle(vfsevent.getInode().getPath()));
                }
            }
        }
    }

    /*
     * Used for changing links after Rename
     */
    @EventSubscriber
    public void onVirtualFileSystemRenameEvent(VirtualFileSystemRenameEvent vfsevent) {
        ImmutableInodeHandle fromInode = vfsevent.getSource();
        if ((fromInode == null) || (!fromInode.isDirectory())) {
            // INode is not a directory
            return;
        }

        InodeHandle toInode = vfsevent.getInode();
        if ((toInode == null) || (!toInode.isDirectory())) {
            // INode is not a directory
            return;
        }

        DirectoryHandle fromDir = new DirectoryHandle(fromInode.getPath());
        DirectoryHandle toDir = (DirectoryHandle) toInode;

        for (LinkType link : getLinks()) {
            link.doRename(toDir, fromDir);
        }
    }
}


