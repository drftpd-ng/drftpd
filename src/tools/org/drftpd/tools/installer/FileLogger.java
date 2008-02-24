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
package org.drftpd.tools.installer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class FileLogger {

	private BufferedWriter _bw;
	private FileWriter _fw;

	public void init() throws IOException {
		_fw = new FileWriter("build.log");
		_bw = new BufferedWriter(_fw);
	}

	public void writeLog(String message) {
		try {
			_bw.write(message);
			_bw.write("\n");
			_bw.flush();
		} catch (IOException e) {
			// ignore for the moment
		}
	}

	public void cleanup() {
		try {
			_bw.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			_fw.close();
		} catch (IOException e) {
			// already closed
		}
	}
}
