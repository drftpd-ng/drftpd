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

import org.drftpd.GlobalContext;

/**
 * @author Teflon
 */
public class ZipscriptConfig {
	protected GlobalContext _gctx;
	private String zsConf = "conf/zipscript.conf";
	private boolean _statusBarEnabled;
	private boolean _offlineFilesEnabled;
	private boolean _missingFilesEnabled;
	private boolean _id3Enabled;
	private boolean _raceStatsEnabled;
	private boolean _restrictSfvEnabled;
	private boolean _multiSfvAllowed;

    public ZipscriptConfig(GlobalContext gctx) throws IOException {
        _gctx = gctx;
        Properties cfg = new Properties();
        cfg.load(new FileInputStream(zsConf));
        loadConfig(cfg);
    }

	public void loadConfig(Properties cfg)
    	throws IOException {
	    _statusBarEnabled    = cfg.getProperty("statusbar.enabled") == null ? true : 
	        						cfg.getProperty("statusbar.enabled").equalsIgnoreCase("true");
	    _offlineFilesEnabled = cfg.getProperty("files.offline.enabled") == null ? true : 
									cfg.getProperty("files.offline.enabled").equalsIgnoreCase("true");
	    _missingFilesEnabled = cfg.getProperty("files.missing.enabled") == null ? true : 
									cfg.getProperty("files.missing.enabled").equalsIgnoreCase("true");
	    _id3Enabled          = cfg.getProperty("cwd.id3info.enabled") == null ? true : 
									cfg.getProperty("cwd.id3info.enabled").equalsIgnoreCase("true");
	    _raceStatsEnabled    = cfg.getProperty("cwd.racestats.enabled") == null ? true : 
									cfg.getProperty("cwd.racestats.enabled").equalsIgnoreCase("true");
	    _restrictSfvEnabled  = cfg.getProperty("sfv.restrict.files") == null ? false : 
									cfg.getProperty("sfv.restrict.files").equalsIgnoreCase("true");
	    _multiSfvAllowed     = cfg.getProperty("allow.multi.sfv") == null ? true : 
									cfg.getProperty("allow.multi.sfv").equalsIgnoreCase("true");
	}
	
    public GlobalContext getGlobalContext() {
        return _gctx;
    }
    
    public boolean id3Enabled() {
        return _id3Enabled;
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
}
