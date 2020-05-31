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
package org.drftpd.master.stats;

/**
 * Stats interface.<br>
 * Simple interface, JavaBeans-ready, that helps storing some Stats data like
 * number of downloaded/uploaded files, amount of transfered data
 * etc...
 *
 * @author fr0w
 */
public interface StatsInterface {
    /**
     * @return the amount of data downloaded.
     */
    long getDownloadedBytes();

    /**
     * Set the amount of data downloaded.
     *
     * @param bytes
     */
    void setDownloadedBytes(long bytes);

    /**
     * @return how many times it was/has downloaded.
     */
    int getDownloadedFiles();

    /**
     * Set how many times it was/has downloaded.
     *
     * @param files
     */
    void setDownloadedFiles(int files);

    /**
     * @return duration of downloads.
     */
    long getDownloadedTime();

    /**
     * Set the duration of the downloads.
     *
     * @param millis
     */
    void setDownloadedTime(long millis);

    /**
     * @return the amount of data uplaoded.
     */
    long getUploadedBytes();

    /**
     * Set the amount of data uploaded.
     *
     * @param bytes
     */
    void setUploadedBytes(long bytes);

    /**
     * @return how many times it was/has uploaded.
     */
    int getUploadedFiles();

    /**
     * Set how many times it was/has uploaded.
     *
     * @param files
     */
    void setUploadedFiles(int files);

    /**
     * @return duration of uploads.
     */
    long getUploadedTime();

    /**
     * Set the duration of the uploads.
     *
     * @param millis
     */
    void setUploadedTime(long millis);
}
