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
package org.drftpd.commands.tvmaze;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.event.VirtualFileSystemInodeCreatedEvent;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author scitz0
 */
public class TvMazeConfig {
	private static TvMazeConfig ourInstance;

	private static final Logger logger = LogManager.getLogger(TvMaze.class);

	private ArrayList<SectionInterface> _rSections = new ArrayList<>();
	private ArrayList<SectionInterface> _sHDSections = new ArrayList<>();
	private ArrayList<SectionInterface> _sSDSections = new ArrayList<>();
	private ArrayList<String> _filters = new ArrayList<>();
	private String _date, _time, _exclude;
	private DateTimeZone _dtz;
	private int _startDelay, _endDelay;
	private boolean _bar_enabled, _bar_directory, _sRelease;

	private TvMazeThread _tvmazeThread = new TvMazeThread();
	private ConcurrentLinkedQueue<DirectoryHandle> _parseQueue = new ConcurrentLinkedQueue<>();

	public static TvMazeConfig getInstance() {
		if (ourInstance == null)
			// it's ok, we can call this constructor
			ourInstance = new TvMazeConfig();
		return ourInstance;
	}

	private TvMazeConfig() {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadConfig();
		_tvmazeThread.start();
	}

	private void loadConfig() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("tvmaze.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/tvmaze.conf not found");
			return;
		}
		_filters.clear();
		for (int i = 1;; i++) {
			String filter = cfg.getProperty("filter."+i);
			if (filter == null) break;
			_filters.add(filter);
		}
		_date = cfg.getProperty("date.show","yyyy-MM-dd");
		_time = cfg.getProperty("time.show","EEEE, HH:mm");
		_dtz = cfg.getProperty("timezone") == null ? DateTimeZone.getDefault() : DateTimeZone.forID(cfg.getProperty("timezone"));
		_exclude = cfg.getProperty("exclude","");
		addSectionsFromConf(cfg, "race.section.", _rSections);
		addSectionsFromConf(cfg, "search.sd.section.", _sSDSections);
		addSectionsFromConf(cfg, "search.hd.section.", _sHDSections);
		_sRelease = cfg.getProperty("search.release", "false").equalsIgnoreCase("true");
		_startDelay = Integer.parseInt(cfg.getProperty("delay.start","5"));
		_endDelay = Integer.parseInt(cfg.getProperty("delay.end","10"));
		if(_startDelay >= _endDelay) {
			logger.warn("Start delay >= End delay, setting default values 5-10");
			_startDelay = 0;
			_endDelay = 5;
		}
		_bar_enabled = cfg.getProperty("tvmazebar.enabled", "true").equalsIgnoreCase("true");
		_bar_directory = cfg.getProperty("tvmazebar.directory", "true").equalsIgnoreCase("true");
	}

	private void addSectionsFromConf(Properties cfg, String prop, ArrayList<SectionInterface> sections) {
		sections.clear();
		for (int i = 1;; i++) {
			String section = cfg.getProperty(prop+i);
			if (section == null) break;
			SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().getSection(section);
			if (!sec.getBaseDirectory().getPath().equals(GlobalContext.getGlobalContext().getRoot().getPath())) {
				sections.add(sec);
			}
		}
	}

	public ArrayList<String> getFilters() {
		return _filters;
	}

	public ArrayList<SectionInterface> getRaceSections() {
		return _rSections;
	}

	public ArrayList<SectionInterface> getHDSections() {
		return _sHDSections;
	}

	public ArrayList<SectionInterface> getSDSections() {
		return _sSDSections;
	}

	public boolean searchRelease() {
		return _sRelease;
	}

	public String getDateFormat() {
		return _date;
	}

	public String getTimeFormat() {
		return _time;
	}

	public DateTimeZone getTimezone() {
		return _dtz;
	}

	public String getExclude() { return _exclude; }

	public int getStartDelay() {
		return _startDelay;
	}

	public int getEndDelay() {
		return _endDelay;
	}

	public boolean barEnabled() {
		return _bar_enabled;
	}

	public boolean barAsDirectory() {
		return _bar_directory;
	}

	public TvMazeThread getTvMazeThread() {
		return _tvmazeThread;
	}

	public DirectoryHandle getDirToProcess() {
		return _parseQueue.poll();
	}

	public int getQueueSize() {
		return _parseQueue.size();
	}

	public void addDirToProcessQueue(DirectoryHandle dir) {
		_parseQueue.add(dir);
	}

	/**
	 * Method called whenever an inode is created.
	 * Spawns a {@link TvMazeThread} if all criteria are met to not stall running thread
	 * while getting the info from TvMaze.
	 * Depends on {@link VirtualFileSystemInodeCreatedEvent} <code>type</code> property.
	 * @param event
	 */
	@EventSubscriber
	public void inodeCreated(VirtualFileSystemInodeCreatedEvent event) {
		if (!event.getInode().isDirectory()) return;

		DirectoryHandle dir = (DirectoryHandle)event.getInode();

		if (!TvMazeUtils.isRelease(dir.getName())) return;

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		if (!TvMazeUtils.containSection(sec, getRaceSections())) return;

		if (dir.getName().matches(getExclude())) return;

        logger.debug("Dir added to process queue for TvMaze data: {}", dir.getPath());

		// Add dir to process queue
		addDirToProcessQueue(dir);
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConfig();
	}
}
