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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.drftpd.tools.installer.console.ConsoleInstaller;
import org.drftpd.tools.installer.swing.SwingInstaller;

/**
 * @author djb61
 * @version $Id$
 */
public class Wrapper {

	private static final Logger logger = Logger.getLogger(Wrapper.class);

	public static void main(String[] args) {
		PluginParser parser = null;
		try {
			parser = new PluginParser();
		} catch (PluginParseException e) {
			logger.fatal("Exception whilst parsing plugins: ",e);
			System.exit(0);
		}
		String input = new String("");
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		ConfigReader cr = new ConfigReader();
		InstallerConfig config = cr.getConfig();

		do {
			System.out.print("Launch installer in GUI mode (y/n): ");
			try {
				input = br.readLine();
			} catch (IOException e) {
				// Not an issue we'll just retry
			}
		} while (!input.equalsIgnoreCase("y") && !input.equalsIgnoreCase("n"));
		if (input.equalsIgnoreCase("y")) {
			SwingInstaller installer = new SwingInstaller(parser.getRegistry(),config);
			installer.setVisible(true);
		} else if (input.equalsIgnoreCase("n")) {
			ConsoleInstaller installer = new ConsoleInstaller(parser.getRegistry(),config);
			installer.show();
		}
	}
}
