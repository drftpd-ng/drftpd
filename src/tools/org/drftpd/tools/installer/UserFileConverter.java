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

import java.io.*;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author djb61
 * @version $Id$
 */
public class UserFileConverter {

	private String _base;
	private String _installDir;

	public UserFileConverter(String base, String installDir) {
		_base = base;
		_installDir = installDir;
	}

	public void convertUsers() {
		System.out.println("");
		ResourceBundle patterns = ResourceBundle.getBundle(this.getClass().getName());
		if (patterns == null) {
			System.out.println("Skipping user conversion as replacement patterns could not be found");
			return;
		}
		if (_base == null) {
			System.out.println("Skipping user conversion as no path was specified");
			return;
		}
		File userPath = new File(_base+File.separator+"users"+File.separator+"javabeans");
		if (!userPath.exists()) {
			System.out.println("Skipping user conversion as selected dir does not contain a valid users dir");
			return;
		}
		File[] userFiles = userPath.listFiles(new UserFilter());
		if (userFiles.length == 0) {
			System.out.println("Skipping user conversion as selected dir contains 0 user files");
			return;
		}
		
		System.out.println("Converting "+userFiles.length+" user files");
		File outputDir = new File(_installDir+File.separator+"userdata"+File.separator+"users"+File.separator+"javabeans");
		if (!outputDir.exists()) {
			if (!outputDir.mkdirs()) {
				System.out.println("Abandoning conversion as target dir could not be created");
				return;
			}
		}
		for (File origUser : userFiles) {
			convertUser(origUser,outputDir,patterns);
		}
	}

	private void convertUser(File origUser,File targetDir,ResourceBundle patterns) {
		StringBuilder inputContents = new StringBuilder();
		FileReader userReader = null;
		BufferedReader buffInput = null;
		try {
			userReader = new FileReader(origUser);
			buffInput = new BufferedReader(userReader);
			String inputLine = null;
			do {
				inputLine = buffInput.readLine();
				if (inputLine != null) {
					inputContents.append(inputLine+"\n");
				}
			} while (inputLine != null);
		} catch (FileNotFoundException e) {
			System.out.println("Skipping "+origUser.getName()+" as it appears to have been deleted");
			return;
		} catch (IOException e) {
			System.out.println("Skipping "+origUser.getName()+" as an IO error occurred during reading");
			return;
		} finally {
			try {
				buffInput.close();
			} catch (IOException e) {
				// already closed
			}
			try {
				userReader.close();
			} catch (IOException e) {
				// already closed
			}
		}
		String userData = inputContents.toString();
		for (int i = 1;; i++) {
			String pattern;
			try {
				pattern = patterns.getString("pattern."+i);
			} catch (MissingResourceException e) {
				break;
			}
			if (pattern.equals("")) {
				break;
			}
			String replacement;
			try {
				replacement = patterns.getString("replace."+i);
			} catch (MissingResourceException e) {
				break;
			}
			userData = userData.replaceAll(pattern, replacement);
		}
		FileWriter userWriter = null;
		BufferedWriter buffOutput = null;
		try {
			userWriter = new FileWriter(new File(targetDir,origUser.getName()));
			buffOutput = new BufferedWriter(userWriter);
			buffOutput.write(userData,0,userData.length());
			buffOutput.flush();
		} catch (IOException e) {
			System.out.println("Skipping "+origUser.getName()+" as an IO error occurred during writing");
        } finally {
			try {
				buffOutput.close();
			} catch (IOException e) {
				// already closed
			}
			try {
				userWriter.close();
			} catch (IOException e) {
				// already closed
			}
		}
	}

	private static class UserFilter implements FilenameFilter {
		
		public UserFilter() {
			
		}

		public boolean accept(File dir, String filename) {
			return filename.endsWith(".xml");
		}
	}
}
