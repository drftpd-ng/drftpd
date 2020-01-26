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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.tools.installer.auto.AutoInstaller;
import org.drftpd.tools.installer.console.ConsoleInstaller;
import org.drftpd.tools.installer.swing.SwingInstaller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * @author djb61
 * @version $Id$
 */
public class Wrapper {

	private static final Logger logger = LogManager.getLogger(Wrapper.class);

	public static void main(String[] args) {
		PluginParser parser = null;
		try {
			parser = new PluginParser();
		} catch (PluginParseException e) {
			logger.fatal("Exception whilst parsing plugins: ",e);
			System.exit(0);
		}

		ConfigReader cr = new ConfigReader();
		InstallerConfig config = cr.getConfig();

		if (args.length > 0 && args[0].equalsIgnoreCase("-a")) {
			AutoInstaller installer = new AutoInstaller(parser.getRegistry(), config, false);
		} else if (userChoice("Automatically build using saved build.conf settings")) {
			if (config.getPluginSelections().isEmpty()) {
				// Failed to load build.conf, exit with error
				System.out.println("\n---> Error loading build.conf! <---\n\n" +
						"Create a valid build configuration before running the automated build or\n" +
						"launch and build using the console/gui installer once first\n" +
						"to generate a valid build.conf file.");
				return;
			}

			ArrayList<String> oldPlugins = PluginTools.getMissingPlugins(parser.getRegistry(), config);
			ArrayList<String> newPlugins = PluginTools.getUnconfiguredPlugins(parser.getRegistry(), config);
			if (!oldPlugins.isEmpty() || !newPlugins.isEmpty()) {
				System.out.println("\n---> Plugins installed does not match plugins configured <---");
				if (!oldPlugins.isEmpty()) {
					System.out.println("\n# Configured plugins not available any more in src #");
					for (String pId : oldPlugins) {
						System.out.println(pId);
					}
				}
				if (!newPlugins.isEmpty()) {
					System.out.println("\n# Installed plugins not configured #");
					for (String pId : newPlugins) {
						System.out.println(pId);
					}
				}
				if (!userChoice("\nContinue with build anyway")) {
					System.out.println("\nBuild aborted!");
					return;
				}
			}
			boolean cleanOnly = userChoice("Only clean, no build");
			AutoInstaller installer = new AutoInstaller(parser.getRegistry(), config, cleanOnly);
		} else {
			if (userChoice("Launch installer in GUI mode")) {
				SwingInstaller installer = new SwingInstaller(parser.getRegistry(), config);
				installer.setVisible(true);
			} else {
				ConsoleInstaller installer = new ConsoleInstaller(parser.getRegistry(), config);
				installer.show();
			}
		}
	}

	private static boolean userChoice(String question) {
		InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		String input = "";
		do {
			System.out.print(question + " (y/n): ");
			try {
				input = br.readLine();
			} catch (IOException e) {
				// Not an issue we'll just retry
			}
		} while (input != null && !input.equalsIgnoreCase("y") && !input.equalsIgnoreCase("n"));
		return input.equalsIgnoreCase("y");
	}
}
