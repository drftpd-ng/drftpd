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
package org.drftpd.commands.find;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author scitz0
 * @version $Id$
 */
public class FindUtils {

	/**
	 * Parses the given string using delimiter specified and returns a <tt>HashSet</tt> of
	 * {@link org.drftpd.master.RemoteSlave Remotslave}.
	 *
	 * @param slaveNames
	 * 	      String containing the slave names.
	 *
	 * @param delimiter
	 *        Delimiter to use for splitting the string into an array of slave names.
	 *
	 * @return A <tt>HashSet</tt> containing a <tt>RemoteSlave</tt> for each slave name
	 *         parsed from the slaveNames string.
	 * 
	 */
	public static HashSet<RemoteSlave> parseSlaves(String slaveNames, String delimiter) {
		return parseSlaves(slaveNames.split(delimiter));
	}

	/**
	 * Gets a <tt>RemoteSlave</tt> object for every slave name in the supplied array and
	 * returns them as a <tt>HashSet</tt>.
	 *
	 * @param slaves
	 *        String array holding slave names to process.
	 *
	 * @return A <tt>HashSet</tt> containing a <tt>RemoteSlave</tt> for each slave name
	 *         processed in the string array.
	 */
	public static HashSet<RemoteSlave> parseSlaves(String[] slaves) {
		List<String> destSlaveNames = Arrays.asList(slaves);
		HashSet<RemoteSlave> destSlaves = new HashSet<>();
		for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
			if (destSlaveNames.contains(rslave.getName()))
				destSlaves.add(rslave);
		}
		return destSlaves;
	}

	/**
	 * Separates min and max from the given string using delimiter specified.
	 *
	 * @param arg
	 *        The string containing the range.
	 *
	 * @param delimiter
	 *        Delimiter to use for splitting the string into min and max range.
	 *
	 * @return A <tt>String</tt> array containing min value a position 0 and max at position 1.
	 *         If delimiter is missing both min and max is given the same value.
	 *         If min or max range is left empty null is returned on that position in the array.
	 */
	public static String[] getRange(String arg, String delimiter) {
		String[] range = new String[2];
		int i = arg.indexOf(delimiter);
		if (i == -1) {
			range[0] = arg;
			range[1] = arg;
		} else {
			String min = arg.substring(0,i);
			String max = arg.substring(i+delimiter.length());
			range[0] = min.isEmpty() ? null : min;
			range[1] = max.isEmpty() ? null : max;
		}
		return range;
	}

	/**
	 * Gets a String representing the array split by the delimiter given.
	 *
	 * @param args
	 *        Array holding the strings to join.
	 *
	 * @param delimiter
	 *        Delimiter to use when joining the strings.
	 *
	 * @return A String of all elements in the array.
	 */
	public static String getStringFromArray(String[] args, String delimiter) {
		StringBuilder sb = new StringBuilder();
		for (String arg : args) {
			sb.append(delimiter).append(arg);
		}
		return sb.toString().substring(delimiter.length());
	}
}
