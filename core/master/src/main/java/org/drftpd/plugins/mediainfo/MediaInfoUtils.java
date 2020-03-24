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
package org.drftpd.plugins.mediainfo;

import java.util.ArrayList;

/**
 * Some medianfo misc methods.
 * @author scitz0
 */
public class MediaInfoUtils {

	public static String getValidFileExtension(String fileName, ArrayList<String> extensions) {
		String extension = getFileExtension(fileName);
		if (extension == null || !extensions.contains(extension)) {
			// Not a valid extension
			return null;
		}
		return extension;
	}

	public static String getFileExtension(String fileName) {
		if (fileName.indexOf('.') == -1) {
			// No extension on file
			return null;
		} else {
			return fileName.substring(fileName.lastIndexOf('.')+1).toLowerCase();
		}
	}

	public static String fixOutput(String value) {
		value = value.replaceAll("pixels", "");
		value = value.replaceAll("channels", "ch");
		value = value.replaceAll(" ", "");
		return value;
	}

}
