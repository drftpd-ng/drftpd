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
package org.drftpd.remotefile;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.AbstractRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile.NonExistingFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.RemoteFileInterface;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferStatus;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Collection;
import java.util.Map;


/**
 * Abstract class for overriding only a subset of LinkedRemoteFile's methods.
 * Can be used for testing.
 * @author mog
 * @version $Id
 */
public abstract class AbstractLinkedRemoteFile
    implements LinkedRemoteFileInterface {
    public AbstractLinkedRemoteFile() {
        super();
    }

    public LinkedRemoteFile addFile(AbstractRemoteFile file) {
        throw new UnsupportedOperationException();
    }

    public void addSlave(RemoteSlave slave) {
        throw new UnsupportedOperationException();
    }

    public int compareTo(Object o) {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile createDirectories(String path) {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile createDirectory(String fileName)
        throws FileExistsException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile createDirectory(String owner, String group,
        String fileName) throws FileExistsException {
        throw new UnsupportedOperationException();
    }

    public void delete() {
        throw new UnsupportedOperationException();
    }

    public void deleteOthers(RemoteSlave slave) {
        throw new UnsupportedOperationException();
    }

    public long dirSize() {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlave(char direction, BaseFtpConnection conn)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForDownload(BaseFtpConnection conn)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public Collection getAvailableSlaves() throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public long getCheckSum() throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public long getCheckSumCached() {
        throw new UnsupportedOperationException();
    }

    public long getCheckSumFromSlave()
        throws NoAvailableSlaveException, IOException {
        throw new UnsupportedOperationException();
    }

    public Collection getDirectories() {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFileInterface getFile(String fileName)
        throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFileInterface getFileDeleted(String fileName)
        throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public Collection getFiles() {
        throw new UnsupportedOperationException();
    }

    public String getGroupname() {
        throw new UnsupportedOperationException();
    }

    public RemoteFileInterface getLink() throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public String getLinkPath() {
        throw new UnsupportedOperationException();
    }

    public Map getMap() {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFileInterface getOldestFile()
        throws ObjectNotFoundException {
        throw new UnsupportedOperationException();
    }

    public String getParent() throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile getParentFile() throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile getParentFileNull() {
        throw new UnsupportedOperationException();
    }

    public String getPath() {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile getRoot() {
        throw new UnsupportedOperationException();
    }

    public SFVFile getSFVFile()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public Collection getSlaves() {
        throw new UnsupportedOperationException();
    }

    public String getUsername() {
        throw new UnsupportedOperationException();
    }

    public long getXferspeed() {
        throw new UnsupportedOperationException();
    }

    public long getXfertime() {
        throw new UnsupportedOperationException();
    }

    public boolean hasFile(String filename) {
        throw new UnsupportedOperationException();
    }

    public boolean hasOfflineSlaves() {
        throw new UnsupportedOperationException();
    }

    public boolean hasSlave(RemoteSlave slave) {
        throw new UnsupportedOperationException();
    }

    public boolean isAvailable() {
        throw new UnsupportedOperationException();
    }

    public boolean isDeleted() {
        throw new UnsupportedOperationException();
    }

    public boolean isDirectory() {
        throw new UnsupportedOperationException();
    }

    public boolean isFile() {
        throw new UnsupportedOperationException();
    }

    public boolean isLink() {
        throw new UnsupportedOperationException();
    }

    public long lastModified() {
        throw new UnsupportedOperationException();
    }

    public long length() {
        throw new UnsupportedOperationException();
    }

    public RemoteFileInterface[] listFiles() {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile lookupFile(String path)
        throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile lookupFile(String path, boolean includeDeleted)
        throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public NonExistingFile lookupNonExistingFile(String path) {
        throw new UnsupportedOperationException();
    }

    public NonExistingFile lookupNonExistingFile(String path,
        boolean includeDeleted) {
        throw new UnsupportedOperationException();
    }

    public String lookupPath(String path) {
        throw new UnsupportedOperationException();
    }

    public SFVFile lookupSFVFile()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile putFile(RemoteFileInterface file) {
        throw new UnsupportedOperationException();
    }

    public TransferStatus receiveFile(Transfer transfer, char type, long offset)
        throws IOException {
        throw new UnsupportedOperationException();
    }

    public void remerge(LinkedRemoteFile mergedir, RemoteSlave rslave) {
        throw new UnsupportedOperationException();
    }

    public boolean removeSlave(RemoteSlave slave) {
        throw new UnsupportedOperationException();
    }

    public LinkedRemoteFile renameTo(String toDirPath, String toName)
        throws IOException, FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public TransferStatus sendFile(Transfer transfer, char type, long offset)
        throws IOException {
        throw new UnsupportedOperationException();
    }

    public void setCheckSum(long checkSum) {
        throw new UnsupportedOperationException();
    }

    public void setGroup(String group) {
        throw new UnsupportedOperationException();
    }

    public void setLastModified(long lastModified) {
        throw new UnsupportedOperationException();
    }

    public void setLength(long length) {
        throw new UnsupportedOperationException();
    }

    public void setOwner(String owner) {
        throw new UnsupportedOperationException();
    }

    public void setXfertime(long xfertime) {
        throw new UnsupportedOperationException();
    }

    public void unmergeDir(RemoteSlave rslave) {
        throw new UnsupportedOperationException();
    }

    public void unmergeFile(RemoteSlave rslave) {
        throw new UnsupportedOperationException();
    }
}
