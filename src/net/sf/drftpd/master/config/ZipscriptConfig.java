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
package net.sf.drftpd.master.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;

import com.Ostermiller.util.StringTokenizer;

/**
 * @author Teflon
 * @version $Id$
 */
public class ZipscriptConfig {
	private static final Logger logger = Logger
			.getLogger(ZipscriptConfig.class);

	protected GlobalContext _gctx;

	private String zsConf = "conf/zipscript.conf";

	private boolean _statusBarEnabled;

	private boolean _offlineFilesEnabled;

	private boolean _missingFilesEnabled;

	private boolean _id3Enabled;

	private boolean _dizEnabled;

	private boolean _raceStatsEnabled;

	private boolean _restrictSfvEnabled;

	private boolean _multiSfvAllowed;

	private boolean _SfvFirstRequired;

	private boolean _SfvFirstAllowNoExt;

	private String _AllowedExts;

	private String _SfvFirstUsers;

	public ZipscriptConfig(GlobalContext gctx) throws IOException {
		_gctx = gctx;
		Properties cfg = new Properties();
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(zsConf);
			cfg.load(stream);
			loadConfig(cfg);
		} finally {
			if(stream != null) {
				stream.close();
			}
		}
	}

	public void loadConfig(Properties cfg) throws IOException {
		_statusBarEnabled = cfg.getProperty("statusbar.enabled") == null ? true
				: cfg.getProperty("statusbar.enabled").equalsIgnoreCase("true");
		_offlineFilesEnabled = cfg.getProperty("files.offline.enabled") == null ? true
				: cfg.getProperty("files.offline.enabled").equalsIgnoreCase(
						"true");
		_missingFilesEnabled = cfg.getProperty("files.missing.enabled") == null ? true
				: cfg.getProperty("files.missing.enabled").equalsIgnoreCase(
						"true");
		_id3Enabled = cfg.getProperty("cwd.id3info.enabled") == null ? true
				: cfg.getProperty("cwd.id3info.enabled").equalsIgnoreCase(
						"true");
		_dizEnabled = cfg.getProperty("cwd.dizinfo.enabled") == null ? true
				: cfg.getProperty("cwd.dizinfo.enabled").equalsIgnoreCase(
						"true");
		_raceStatsEnabled = cfg.getProperty("cwd.racestats.enabled") == null ? true
				: cfg.getProperty("cwd.racestats.enabled").equalsIgnoreCase(
						"true");
		_restrictSfvEnabled = cfg.getProperty("sfv.restrict.files") == null ? false
				: cfg.getProperty("sfv.restrict.files")
						.equalsIgnoreCase("true");
		_multiSfvAllowed = cfg.getProperty("allow.multi.sfv") == null ? true
				: cfg.getProperty("allow.multi.sfv").equalsIgnoreCase("true");
		_SfvFirstRequired = cfg.getProperty("sfvfirst.required") == null ? true
				: cfg.getProperty("sfvfirst.required").equalsIgnoreCase("true");
		_SfvFirstAllowNoExt = cfg.getProperty("sfvfirst.allownoext") == null ? true
				: cfg.getProperty("sfvfirst.allownoext").equalsIgnoreCase(
						"true");
		_AllowedExts = cfg.getProperty("allowedexts") == null ? "sfv" : cfg
				.getProperty("allowedexts").toLowerCase().trim()
				+ " sfv";
		_SfvFirstUsers = cfg.getProperty("sfvfirst.users") == null ? "*" : cfg
				.getProperty("sfvfirst.users");

		// Locals
		String SfvFirstPathIgnore = cfg.getProperty("sfvfirst.pathignore") == null ? "*"
				: cfg.getProperty("sfvfirst.pathignore");
		String SfvFirstPathCheck = cfg.getProperty("sfvfirst.pathcheck") == null ? "*"
				: cfg.getProperty("sfvfirst.pathcheck");

		// SFV First PathPermissions
		if (_SfvFirstRequired) {
			try {
				// this one gets perms defined in sfvfirst.users
				StringTokenizer st = new StringTokenizer(SfvFirstPathCheck, " ");
				while (st.hasMoreTokens()) {
					_gctx.getConfig().addPathPermission(
							"sfvfirst.pathcheck",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer(
											_SfvFirstUsers, " "))));
				}
				st = new StringTokenizer(SfvFirstPathIgnore, " ");
				while (st.hasMoreTokens()) {
					_gctx.getConfig().addPathPermission(
							"sfvfirst.pathignore",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer("*", " "))));
				}
			} catch (MalformedPatternException e) {
				logger.warn("Exception when reading " + zsConf, e);
			}
		}
	}

	public GlobalContext getGlobalContext() {
		return _gctx;
	}

	public boolean id3Enabled() {
		return _id3Enabled;
	}

	public boolean dizEnabled() {
		return _dizEnabled;
	}

	public boolean missingFilesEnabled() {
		return _missingFilesEnabled;
	}

	public boolean multiSfvAllowed() {
		return _multiSfvAllowed;
	}

	public boolean offlineFilesEnabled() {
		return _offlineFilesEnabled;
	}

	public boolean raceStatsEnabled() {
		return _raceStatsEnabled;
	}

	public boolean restrictSfvEnabled() {
		return _restrictSfvEnabled;
	}

	public boolean statusBarEnabled() {
		return _statusBarEnabled;
	}

	public boolean checkAllowedExtension(String file) {
		if (_SfvFirstAllowNoExt && !file.contains(".")) {
			return true;
		}
		StringTokenizer st = new StringTokenizer(_AllowedExts, " ");
		while (st.hasMoreElements()) {
			String ext = "." + st.nextElement().toString().toLowerCase();
			if (file.toLowerCase().endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	public boolean checkSfvFirstEnforcedPath(DirectoryHandle dir, User user) {
		if (_SfvFirstRequired
				&& _gctx.getConfig().checkPathPermission("sfvfirst.pathcheck",
						user, dir)
				&& !_gctx.getConfig().checkPathPermission(
						"sfvfirst.pathignore", user, dir)) {
			return true;
		}
		return false;
	}
}
