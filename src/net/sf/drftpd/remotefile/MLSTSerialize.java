/*
 * DrFTPD, Distributed File Transfer Protocol Daemon
 * Copyright (C) 2003  Morgan Christiansson
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.sf.drftpd.remotefile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.drftpd.Checksum;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;

public class MLSTSerialize {
	private static Logger logger = Logger.getLogger(MLSTSerialize.class);

	public static final SimpleDateFormat timeval =
		new SimpleDateFormat("yyyyMMddHHmmss.SSS");

	public static void serialize(LinkedRemoteFile dir, PrintStream out) {
		out.println(dir.getPath() + ":");
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			out.println(toMLST(file));
		}
		out.println();
		for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.isDirectory())
				serialize(file, out);
		}
	}
	public static String toMLST(RemoteFileInterface file) {
		StringBuffer ret = new StringBuffer();
		if (file.isFile())
			ret.append("type=file;");
		if (file.isDirectory())
			ret.append("type=dir;");

		if (file.getCheckSumCached() != 0)
			ret.append(
				"x.crc32="
					+ Checksum.formatChecksum(file.getCheckSumCached())
					+ ";");

		ret.append("size=" + file.length() + ";");
		ret.append(
			"modify="
				+ timeval.format(new Date(file.lastModified()))
				+ ";");

		ret.append("unix.owner=" + file.getUsername() + ";");
		ret.append("unix.group=" + file.getGroupname() + ";");
		if (file.isFile()) {
			Iterator iter = file.getSlaves().iterator();
			if (iter.hasNext()) {
				ret.append("x.slaves=");
				ret.append(((RemoteSlave) iter.next()).getName());
				while (iter.hasNext()) {
					ret.append("," + ((RemoteSlave) iter.next()).getName());
				}
				ret.append(";");
			}
		}
		if (file.isDeleted())
			ret.append("x.deleted=true;");
		if (file.getXfertime() != 0)
			ret.append("x.xfertime=" + file.getXfertime() + ";");
		ret.append(" " + file.getName());
		return ret.toString();
	}

	private static void unserialize(
		BufferedReader in,
		LinkedRemoteFile dir,
		Hashtable allRslaves,
		String path)
		throws IOException {

		for (String line = in.readLine();; line = in.readLine()) {
			if (line == null)
				throw new CorruptFileListException("Unexpected EOF");
			if (line.equals(""))
				return;
			int pos = line.indexOf(' ');
			String filename = line.substring(pos + 1);
			StaticRemoteFile file = new StaticRemoteFile(filename);
			StringTokenizer st =
				new StringTokenizer(line.substring(0, pos), ";");
			while (st.hasMoreElements()) {
				String entry = st.nextToken();
				pos = entry.indexOf('=');
				String k = entry.substring(0, pos);
				String v = entry.substring(pos + 1);
				if ("type".equals(k)) {
					assert v.equals("file") || v.equals("dir") : v;
					file.setIsFile("file".equals(v));
					file.setIsDirectory("dir".equals(v));
				} else if ("modified".equals(k)) {
					try {
						file.setLastModified(
							timeval.parse(v).getTime());
					} catch (ParseException e) {
						throw new CorruptFileListException(e);
					}
				} else if ("x.crc32".equals(k)) {
					file.setChecksum(Long.parseLong(v, 16));
				} else if ("unix.owner".equals(k)) {
					file.setUsername(v);
				} else if ("unix.group".equals(k)) {
					file.setGroupname(v);
				} else if ("x.deleted".equals(k)) {
					file.setDeleted(true);
				} else if ("size".equals(k)) {
					file.setLength(Long.parseLong(v));
				} else if ("x.slaves".equals(k)) {

					ArrayList rslaves = new ArrayList();
					for (StringTokenizer st2 = new StringTokenizer(v, ",");
						st.hasMoreTokens();
						) {
						RemoteSlave rslave =
							(RemoteSlave) allRslaves.get(st2.nextToken());
						if (rslave == null)
							throw new NullPointerException();
						rslaves.add(rslave);
					}
					file.setRSlaves(rslaves);
				} else if("x.xfertime".equals(k)) {
					file.setXfertime(Long.parseLong(v));
				}
			}
			dir.addFile(file);
		}
	}
	public static LinkedRemoteFile unserialize(
		FtpConfig conf,
		BufferedReader in,
		List rslaves)
		throws IOException, CorruptFileListException {
		LinkedRemoteFile root = new LinkedRemoteFile(conf);

		for (String line = in.readLine(); line != null; line = in.readLine()) {

			if (!line.endsWith(":"))
				throw new CorruptFileListException("expecting path, not " + line);

			String path = line.substring(0, line.length() - 1);
			Object ret[] = root.lookupNonExistingFile(path);
			LinkedRemoteFile dir;
			dir = (LinkedRemoteFile) ret[0];
			if (ret[1] != null) {
				throw new CorruptFileListException(path + " doesn't exist");
				//				 StringTokenizer st = new StringTokenizer((String)ret[1], "/");
				//				 while(st.hasMoreTokens()) {
				//				 	dir.createDirectory()
				//				 }
			}

			unserialize(
				in,
				dir,
				JDOMRemoteFile.rslavesToHashtable(rslaves),
				path);
		}
		return root;
	}
}
