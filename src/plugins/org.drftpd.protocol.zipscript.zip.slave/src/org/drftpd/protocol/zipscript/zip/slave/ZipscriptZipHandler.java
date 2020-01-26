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

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseDizInfo;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseZipCRCInfo;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;

/**
 * Handler for Zip requests.
 *
 * @author djb61
 * @version $Id$
 */
public class ZipscriptZipHandler extends AbstractHandler {
	private static final Logger logger = LogManager.getLogger(ZipscriptZipHandler.class);

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

	private URI getZipURI(Slave slave, String path) throws FileNotFoundException, URISyntaxException {
		return new URI("jar:file", slave.getRoots().getFile(path).toURI().getPath(), null);
	}

	private boolean checkZipFile(Slave slave, String path) {
		URI zipURI;
		try {
			zipURI = getZipURI(slave, path);
		} catch (FileNotFoundException e) {
			return false;
		} catch (URISyntaxException e) {
			return false;
		}

		boolean integrityOk = true;

		try (FileSystem zipFs = FileSystems.newFileSystem(zipURI, Collections.emptyMap())) {
			AtomicInteger files = new AtomicInteger();
			for(Path root : zipFs.getRootDirectories()) {
				Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
							throws IOException {
						if (Files.isRegularFile(file)) {
							calculateChecksum(file);
							files.incrementAndGet();
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
			if (files.get() == 0) {
				throw new IOException("Zip file empty");
			}
		} catch (Throwable t) {
			integrityOk = false;
            logger.debug("Error validating integrity of {} : {}", path, t.getMessage());
		}

		return integrityOk;
	}

	private void calculateChecksum(Path file) throws IOException {
		final byte[] buff = new byte[16384];
		try (CheckedInputStream in = new CheckedInputStream(new BufferedInputStream(
				Files.newInputStream(file)), new CRC32())) {
			while (in.read(buff) != -1) {
				// do nothing, we are only checking for crc
			}
		} catch (IOException e) {
			throw new IOException("CRC check failed for " + file);
		}
	}

	private DizInfo getDizInfo(Slave slave, String path) {
		DizInfo dizInfo = new DizInfo();
		URI zipURI;
		try {
			zipURI = getZipURI(slave, path);
		} catch (FileNotFoundException e) {
			return dizInfo;
		} catch (URISyntaxException e) {
			return dizInfo;
		}

		try (FileSystem zipFs = FileSystems.newFileSystem(zipURI, Collections.emptyMap())) {
			final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("regex:(?i).*file_id.diz");
			for(Path root : zipFs.getRootDirectories()) {
				Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
							throws IOException {
						if (Files.isRegularFile(file) && matcher.matches(file)) {
							String dizString = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
							int total = getDizTotal(dizString);
							if (total > 0) {
								dizInfo.setValid(true);
								dizInfo.setTotal(total);
								dizString = Base64.getMimeEncoder().encodeToString(dizString.getBytes(StandardCharsets.ISO_8859_1));
								dizInfo.setString(dizString);
								return FileVisitResult.TERMINATE;
							}
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
		} catch (Throwable t) {
            logger.debug("Error getting diz info from {} : {}", path, t.getMessage());
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
			total = Integer.valueOf(m.group(1).replaceAll("[oOxX]", "0"));
		}
		
		return total;
	}
}
