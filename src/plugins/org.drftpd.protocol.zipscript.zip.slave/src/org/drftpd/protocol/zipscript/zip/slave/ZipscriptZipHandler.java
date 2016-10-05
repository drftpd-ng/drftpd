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
package org.drftpd.protocol.zipscript.zip.slave;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drftpd.util.Base64;

import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseDizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseZipCRCInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;

import de.schlichtherle.truezip.file.TArchiveDetector;
import de.schlichtherle.truezip.file.TFile;
import de.schlichtherle.truezip.file.TFileInputStream;
import de.schlichtherle.truezip.fs.FsSyncException;
import de.schlichtherle.truezip.fs.archive.zip.CheckedZipDriver;
import de.schlichtherle.truezip.socket.sl.IOPoolLocator;

/**
 * Handler for Zip requests.
 *
 * @author djb61
 * @version $Id$
 */
public class ZipscriptZipHandler extends AbstractHandler {
	
	public ZipscriptZipHandler(SlaveProtocolCentral central) {
		super(central);
	}
	
	public AsyncResponse handleZipCRC(AsyncCommandArgument ac) {
		return new AsyncResponseZipCRCInfo(ac.getIndex(),
				checkZipFile(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
	}
	
	public AsyncResponse handleZipDizInfo(AsyncCommandArgument ac) {
		return new AsyncResponseDizInfo(ac.getIndex(),
				getDizInfo(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
	}
	
	//Recursive check of TFile
	private void checkFiles(TFile[] zipEntries) throws IOException {
		InputStream entryStream = null;
		for (TFile zipEntry : zipEntries) {
			if (zipEntry.isDirectory()) {
				checkFiles(zipEntry.listFiles());
			} else {
				try {
					entryStream = new TFileInputStream(zipEntry);
					byte[] buff = new byte[65536];
					//noinspection StatementWithEmptyBody
					while (entryStream.read(buff) != -1) {
						// do nothing, we are only checking for crc
					}
				} catch (IOException e) {
					throw new IOException(e);
				} finally {
					if (entryStream != null) {
						entryStream.close();
					}
				}
			}
		}
	}
	
	private boolean checkZipFile(Slave slave, String path) {
		boolean integrityOk = true;
		TFile zipFile = null;
		try {
			zipFile = new TFile(slave.getRoots().getFile(path),
					new TArchiveDetector("zip", new CheckedZipDriver(IOPoolLocator.SINGLETON)));
			TFile[] zipEntries = zipFile.listFiles(TArchiveDetector.NULL);
			if ((zipEntries == null) || (zipEntries.length == 0)) {
				integrityOk = false;
			} else {
				checkFiles(zipEntries);
			}
		} catch (IOException e) {
			integrityOk = false;
		} finally {
			if (zipFile != null) {
				try {
					TFile.umount(zipFile, true);
				} catch (FsSyncException e) {
					// Already closed
				}
			}
		}
		
		return integrityOk;
	}
	
	private DizInfo getDizInfo(Slave slave, String path) {
		DizInfo dizInfo = new DizInfo();
		TFile zipFile = null;
		try {
			InputStream entryStream = null;
			zipFile = new TFile(slave.getRoots().getFile(path),
					new TArchiveDetector("zip", new CheckedZipDriver(IOPoolLocator.SINGLETON)));
			TFile[] zipEntries = zipFile.listFiles();
			if (zipEntries != null) {
				for (TFile entry : zipEntries) {
					if (entry.getName().toLowerCase().equals("file_id.diz")) {
						try {
							entryStream = new TFileInputStream(entry);
							byte[] buff = new byte[65536];
							StringBuilder dizBuffer = new StringBuilder();
							int bytesRead = 0;
							while (bytesRead != -1) {
								bytesRead = entryStream.read(buff);
								if (bytesRead != -1) {
									String dizBlock = new String(buff, 0, bytesRead, "8859_1");
									dizBuffer.append(dizBlock);
								}
							}
							String dizString = dizBuffer.toString();
							int total = getDizTotal(dizString);
							if (total > 0) {
								dizInfo.setValid(true);
								dizInfo.setTotal(total);
								dizString = Base64.encodeToString(dizBuffer.toString().getBytes("8859_1"), Base64.RFC2045);
								dizInfo.setString(dizString);
							}
							break;
						} catch (IOException e) {
							// Something wrong with the .diz entry in this file, just return with no diz info
						} finally {
							if (entryStream != null) {
								entryStream.close();
							}
						}
					}
				}
			}
		} catch (IOException e) {
			// Unable to read zip just ignore
		} finally {
			if (zipFile != null) {
				try {
					TFile.umount(zipFile, true);
				} catch (FsSyncException e) {
					// Already closed
				}
			}
		}
		
		return dizInfo;
	}
	
	private int getDizTotal(String dizString) {
		int total = 0;
		String regex = "[\\[\\(\\<\\:\\s](?:\\s)?[0-9oOxX\\*]*(?:\\s)?/(?:\\s)?([0-9oOxX]*[0-9oO])(?:\\s)?[\\]\\)\\>\\s]";
		Pattern p = Pattern.compile(regex);
		
		// Compare the diz file to the pattern compiled above
		Matcher m = p.matcher(dizString);
		
		if (m.find()) {
			total = new Integer(m.group(1).replaceAll("[oOxX]", "0"));
		}
		
		return total;
	}
}
