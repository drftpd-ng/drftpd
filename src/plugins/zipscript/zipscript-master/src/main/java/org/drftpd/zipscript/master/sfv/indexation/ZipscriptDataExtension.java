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
package org.drftpd.zipscript.master.sfv.indexation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.lucene.document.Document;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.vfs.CaseInsensitiveTreeMap;
import org.drftpd.master.indexation.IndexDataExtensionInterface;
import org.drftpd.master.indexation.LuceneUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.event.ImmutableInodeHandle;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.common.zip.DizStatus;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author scitz0
 * @version $Id$
 */
public class ZipscriptDataExtension implements IndexDataExtensionInterface {

    private static final Logger logger = LogManager.getLogger(ZipscriptDataExtension.class);


    @Override
    public void addData(Document doc, ImmutableInodeHandle inode) {
        SFVInfo sfvInfo = null;
        SFVStatus sfvStatus = null;
        DizInfo dizInfo = null;
        DizStatus dizStatus = null;
        if (inode.isDirectory()) {
            try {
                sfvInfo = inode.getPluginMetaData(SFVInfo.SFVINFO);
                sfvStatus = getSFVStatus(sfvInfo, new DirectoryHandle(inode.getPath()));
            } catch (KeyNotFoundException | IOException e) {
                // Fields will be cleared below
            }
            try {
                dizInfo = inode.getPluginMetaData(DizInfo.DIZINFO);
                dizStatus = getDizStatus(dizInfo, new DirectoryHandle(inode.getPath()));
            } catch (KeyNotFoundException | IOException e) {
                // Fields will be cleared below
            }
        }
        if (sfvStatus == null && dizStatus == null) {
            LuceneUtils.addIntField(doc, "present", -1);
            LuceneUtils.addIntField(doc, "missing", -1);
            LuceneUtils.addIntField(doc, "percent", -1);
        } else if (sfvStatus != null) {
            LuceneUtils.addIntField(doc, "present", sfvStatus.getPresent());
            LuceneUtils.addIntField(doc, "missing", sfvStatus.getMissing());
            LuceneUtils.addIntField(doc, "percent", (sfvStatus.getPresent() * 100) / sfvInfo.getSize());
        } else {
            LuceneUtils.addIntField(doc, "present", dizStatus.getPresent());
            LuceneUtils.addIntField(doc, "missing", dizStatus.getMissing());
            LuceneUtils.addIntField(doc, "percent", (dizStatus.getPresent() * 100) / dizInfo.getTotal());
        }
    }

    private SFVStatus getSFVStatus(SFVInfo sfvInfo, DirectoryHandle dir)
            throws IOException {
        int offline = 0;
        int present = 0;
        CaseInsensitiveTreeMap<String, Long> sfvEntries = sfvInfo.getEntries();
        for (FileHandle file : dir.getFilesUnchecked()) {
            if (file.isFile() && sfvEntries.containsKey(file.getName())) {
                if (!file.isUploading()) {
                    present++;
                }
                if (!file.isAvailable()) {
                    offline++;
                }
            }
        }
        return new SFVStatus(sfvEntries.size(), offline, present);
    }

    private DizStatus getDizStatus(DizInfo dizInfo, DirectoryHandle dir)
            throws IOException {
        int offline = 0;
        int present = 0;
        int transferring = 0;
        for (FileHandle file : dir.getFilesUnchecked()) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
                if (!file.isUploading()) {
                    present++;
                } else {
                    logger.debug("getDizStatus(), dir: {}, Transferring: {}", dir, file);
                    transferring++;
                }
                if (!file.isAvailable()) {
                    offline++;
                }
            }
        }
        logger.debug("getDizStatus(), dir: {}, total: {}, offline: {}, present: {}, transferring: {}", dir, dizInfo.getTotal(), offline, present, transferring);
        return new DizStatus(dizInfo.getTotal(), offline, present);
    }
}
