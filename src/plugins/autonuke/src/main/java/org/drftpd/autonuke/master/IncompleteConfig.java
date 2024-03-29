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
package org.drftpd.autonuke.master;

import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;

import java.util.Properties;

/**
 * @author scitz0
 */
public class IncompleteConfig extends Config {
    int _min_percent;

    public IncompleteConfig(int i, Properties p) {
        super(i, p);
        _min_percent = Integer.parseInt(PropertyHelper.getProperty(p, i + ".min.percent", "0"));
    }

    /**
     * Boolean to return incomplete status (SFV/DIZ)
     * Minimum percent optional
     *
     * @param configData Object holding return data
     * @param dir        Directory currently being handled
     * @return Return false if dir should be nuked, else true
     */
    public boolean process(ConfigData configData, DirectoryHandle dir) {
        // Does the dir contain a diz file?
        ZipscriptVFSDataZip dizData = new ZipscriptVFSDataZip(dir);
        try {
            DizStatus dizStatus = dizData.getDizStatus();
            int total = dizStatus.getPresent() + dizStatus.getMissing();
            configData.addReturnData(Integer.toString(dizStatus.getMissing()));
            configData.addReturnData(Integer.toString((dizStatus.getMissing() * 100) / total));
            configData.addReturnData(Integer.toString(dizStatus.getPresent()));
            configData.addReturnData(Integer.toString(total));
            configData.addReturnData(Integer.toString((dizStatus.getPresent() * 100) / total));
            if (total > 0) {
                if (_min_percent == 0) {
                    return dizStatus.isFinished();
                }
                int totalPercent = (dizStatus.getPresent() * 100) / total;
                return totalPercent == 100 || totalPercent > _min_percent;
            }
        } catch (Exception e) {
            // No dizStatus found, try sfv instead
        }

        ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
        try {
            SFVStatus sfvStatus = sfvData.getSFVStatus();
            int total = sfvStatus.getPresent() + sfvStatus.getMissing();
            configData.addReturnData(Integer.toString(sfvStatus.getMissing()));
            configData.addReturnData(Integer.toString((sfvStatus.getMissing() * 100) / total));
            configData.addReturnData(Integer.toString(sfvStatus.getPresent()));
            configData.addReturnData(Integer.toString(total));
            configData.addReturnData(Integer.toString((sfvStatus.getPresent() * 100) / total));
            if (total > 0) {
                if (_min_percent == 0) {
                    return sfvStatus.isFinished();
                }
                int totalPercent = (sfvStatus.getPresent() * 100) / total;
                return totalPercent == 100 || totalPercent > _min_percent;
            }
        } catch (Exception e) {
            // No sfvStatus found either, can't check incomplete status :(
        }

        return true;
    }

}
