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
package org.drftpd.plugins.sitebot.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.NullOutputWriter;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

/**
 * @author djb61
 * @version $Id$
 */
public class AnnounceConfig {

	private static final Logger logger = LogManager.getLogger(AnnounceConfig.class);

	private HashMap<String,ArrayList<AnnounceWriter>> _pathWriters =
            new HashMap<>();

	private HashMap<String,ArrayList<AnnounceWriter>> _sectionWriters =
            new HashMap<>();

	private HashMap<String,AnnounceWriter> _simpleWriters =
            new HashMap<>();

	private ArrayList<String> _eventTypes;

	private SiteBot _bot;

	private String _confDir;

	public AnnounceConfig(String confDir, ArrayList<String> eventTypes, SiteBot bot) {
		_confDir = confDir;
		_eventTypes = eventTypes;
		_bot = bot;
		loadConfig(GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin(confDir+"/ircannounce.conf"));
	}

	private synchronized void loadConfig(Properties cfg) {
		ArrayList<String> clonedEvents = new ArrayList<>(_eventTypes);
		HashMap<String,ArrayList<AnnounceWriter>> pathWriters =
                new HashMap<>();
		HashMap<String,ArrayList<AnnounceWriter>> sectionWriters =
                new HashMap<>();
		HashMap<String,AnnounceWriter> simpleWriters =
                new HashMap<>();
		for (String type : clonedEvents) {
			// First check for any path settings for this type
			ArrayList<AnnounceWriter> pWriters = new ArrayList<>();
			for (int i = 1;; i++) {
				String pathPattern = cfg.getProperty(type+".path."+i);
				if (pathPattern == null) {
					break;
				}
				String destination = cfg.getProperty(type+".path."+i+".destination");
				if (destination == null || destination.equals("")) {
					continue;
				}
				String displayName = cfg.getProperty(type+".path."+i+".displayname");
				ArrayList<OutputWriter> writers = parseDestinations(destination);
				if (writers.size() == 0) {
					continue;
				}
				boolean useRegex = Boolean.parseBoolean(cfg.getProperty(type+".path."+i+".regex", "false"));
				PathMatcher matcher;
				try {
					matcher = new PathMatcher(pathPattern, useRegex);
				} catch (PatternSyntaxException e) {
                    logger.warn("Bad entry {}.{}.path in sitebot announce conf", type, i);
					continue;
				}
				pWriters.add(new AnnounceWriter(matcher,writers,displayName));
			}
			if (pWriters.size() > 0) {
				pathWriters.put(type, pWriters);
			}

			// Next check for any section settings for this type
			ArrayList<AnnounceWriter> sWriters = new ArrayList<>();
			for (int i = 1;; i++) {
				String sectionName = cfg.getProperty(type+".section."+i);
				if (sectionName == null) {
					break;
				}
				String destination = cfg.getProperty(type+".section."+i+".destination");
				if (destination == null || destination.equals("")) {
					continue;
				}
				ArrayList<OutputWriter> writers = parseDestinations(destination);
				if (writers.size() == 0) {
					continue;
				}
				sWriters.add(new AnnounceWriter(null,writers,sectionName));
			}
			if (sWriters.size() > 0) {
				sectionWriters.put(type, sWriters);
			}

			// Finally check for any pathless settings for this type
			String destination = cfg.getProperty(type+".destination");
			if (destination == null || destination.equals("")) {
				continue;
			}
			ArrayList<OutputWriter> simpWriters = parseDestinations(destination);
			if (simpWriters.size() > 0) {
				simpleWriters.put(type, new AnnounceWriter(null,simpWriters,null));
			}
		}
		_pathWriters = pathWriters;
		_sectionWriters = sectionWriters;
		_simpleWriters = simpleWriters;
	}

	private ArrayList<OutputWriter> parseDestinations(String destination) {
		ArrayList<OutputWriter> writers = new ArrayList<>();
		StringTokenizer channels = new StringTokenizer(destination);
		while (channels.hasMoreTokens()) {
			String token = channels.nextToken();
			if (token.equalsIgnoreCase("null")) {
				writers.add(new NullOutputWriter());
				break;
			}
			if (token.equalsIgnoreCase("public")) {
				writers.addAll(0,_bot.getWriters().values());
				break;
			}
			OutputWriter writer = _bot.getWriters().get(token);
			if (writer != null) {
				writers.add(writer);
			}
		}
		return writers;
	}

	public AnnounceWriter getSimpleWriter(String type) {
		AnnounceWriter writer = _simpleWriters.get(type);
		if (writer == null) {
			// Try default type
			writer = _simpleWriters.get("default");
		}
		return writer;
	}

	public AnnounceWriter getPathWriter(String type, InodeHandle path) {
		ArrayList<AnnounceWriter> aWriters;
		// First check path filters for this type
		aWriters = _pathWriters.get(type);
		if (aWriters != null) {
			for (AnnounceWriter writer : aWriters) {
				if (writer.pathMatches(path)) {
					return writer;
				}
			}
		}
		// Check section filters for this type
		aWriters = _sectionWriters.get(type);
		if (aWriters != null) {
			for (AnnounceWriter writer : aWriters) {
				if (writer.sectionMatches(path.isDirectory() ? (DirectoryHandle)path : path.getParent())) {
					return writer;
				}
			}
		}
		// Check path filters for default type
		aWriters = _pathWriters.get("default");
		if (aWriters != null) {
			for (AnnounceWriter writer : aWriters) {
				if (writer.pathMatches(path)) {
					return writer;
				}
			}
		}
		// Check section filters for default type
		aWriters = _sectionWriters.get("default");
		if (aWriters != null) {
			for (AnnounceWriter writer : aWriters) {
				if (writer.sectionMatches(path.isDirectory() ? (DirectoryHandle)path : path.getParent())) {
					return writer;
				}
			}
		}
		// Still no match, then this event should be ignored
		// return a null writer
		return null;
	}

	public void reload() {
		loadConfig(GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin(_confDir+"/ircannounce.conf"));
	}

	public SiteBot getBot() {
		return _bot;
	}
}
