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
package org.drftpd.commands.imdb;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commands.imdb.event.IMDBEvent;
import org.drftpd.commands.imdb.vfs.IMDBVFSDataNFO;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.lucene.extensions.imdb.IMDBQueryParams;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
public class IMDBUtils {
	private static final Logger logger = LogManager.getLogger(IMDBUtils.class);

	private static final String[] _seperators = {".","-","_"};

	public static void setInfo(IMDBInfo imdbInfo, IMDBParser imdbParser) {
		imdbInfo.setTitle(imdbParser.getTitle());
		imdbInfo.setYear(imdbParser.getYear());
		imdbInfo.setLanguage(imdbParser.getLanguage());
		imdbInfo.setCountry(imdbParser.getCountry());
		imdbInfo.setDirector(imdbParser.getDirector());
		imdbInfo.setGenres(imdbParser.getGenres());
		imdbInfo.setPlot(imdbParser.getPlot());
		imdbInfo.setRating(imdbParser.getRating());
		imdbInfo.setVotes(imdbParser.getVotes());
		imdbInfo.setRuntime(imdbParser.getRuntime());
		imdbInfo.setMovieFound(imdbParser.foundMovie());
	}

	public static ReplacerEnvironment getEnv(IMDBInfo imdbInfo) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("title", imdbInfo.getTitle());
		env.add("year", imdbInfo.getYear() != null ? imdbInfo.getYear() : "-");
		env.add("language", imdbInfo.getLanguage());
		env.add("country", imdbInfo.getCountry());
		env.add("director", imdbInfo.getDirector());
		env.add("genres", imdbInfo.getGenres());
		env.add("plot", imdbInfo.getPlot());
		env.add("rating", imdbInfo.getRating() != null ? imdbInfo.getRating()/10+"."+imdbInfo.getRating()%10 : "-");
		env.add("votes", imdbInfo.getVotes() != null ? imdbInfo.getVotes() : "-");
		env.add("url", imdbInfo.getURL());
		env.add("runtime", imdbInfo.getRuntime() != null ? imdbInfo.getRuntime() : "-");
		return env;
	}

	public static long randomNumber() {
		return (IMDBConfig.getInstance().getStartDelay() + (new SecureRandom()).nextInt(
				IMDBConfig.getInstance().getEndDelay()-IMDBConfig.getInstance().getStartDelay()
				))*1000;
	}

	public static IMDBInfo getIMDBInfo(DirectoryHandle dir, boolean parse) {
		IMDBInfo imdbInfo;
		IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO(dir);
		try {
			imdbInfo = imdbData.getIMDBInfo();
			if (parse) {
				addMetadata(imdbInfo, dir);
			}
			return imdbInfo;
		} catch (FileNotFoundException e) {
			// Just continue
		} catch (IOException e) {
			// To bad...
			logger.error("",e);
		} catch (NoAvailableSlaveException e) {
			// Not much to do...
		} catch (SlaveUnavailableException e) {
			// Not much to do...
		}
		return null;
	}

	public static void publishEvent(IMDBInfo imdbInfo, DirectoryHandle dir, SectionInterface section) {
		if (imdbInfo == null) {
			return;
		}
		if (imdbInfo.getMovieFound()) {
			//Announce
			ReplacerEnvironment env = getEnv(imdbInfo);
			env.add("release", dir.getName());
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			GlobalContext.getEventService().publishAsync(new IMDBEvent(env, dir));
		}
	}

	public static void addMetadata(IMDBInfo imdbInfo, DirectoryHandle dir) {
		if (imdbInfo == null) {
			return;
		}
		populateIMDBInfo(imdbInfo);
		if (imdbInfo.getMovieFound()) {
			try {
				dir.addPluginMetaData(IMDBInfo.IMDBINFO, imdbInfo);
			} catch (FileNotFoundException e) {
				logger.error("Failed to add IMDB metadata",e);
			}
		}
	}

	public static void populateIMDBInfo(IMDBInfo imdbInfo) {
		if (!imdbInfo.getMovieFound()) {
			IMDBParser imdbParser = new IMDBParser();
			imdbParser.doNFO(imdbInfo.getURL());
			setInfo(imdbInfo, imdbParser);
		}
	}

	public static String filterTitle(String title) {
		String newTitle = title.toLowerCase();
		//remove filtered words
		for (String filter : IMDBConfig.getInstance().getFilters()) {
			newTitle = newTitle.replaceAll("\\b"+filter.toLowerCase()+"\\b","");
		}
		//remove seperators
		for (String separator : _seperators) {
			newTitle = newTitle.replaceAll("\\"+separator," ");
		}
		newTitle = newTitle.trim();
		// Escape HTML
		try {
			newTitle = URLEncoder.encode(newTitle, "UTF-8");
		} catch (UnsupportedEncodingException ignored) {
			// Can be safely ignored because UTF-8 is always supported
		}
		return newTitle;
	}

	public static Map<String,String> getNFOFiles(DirectoryHandle dir) throws IndexException {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();

		AdvancedSearchParams params = new AdvancedSearchParams();
		params.setEndsWith(".nfo");
		params.setInodeType(AdvancedSearchParams.InodeType.FILE);
		params.setLimit(0);

		return ie.advancedFind(dir, params);
	}

	public static boolean isRelease(String dirName) {
		Pattern p = Pattern.compile("(\\w+\\.){3,}\\w+-\\w+");
		Matcher m = p.matcher(dirName);
		return m.find();
	}

	public static ArrayList<DirectoryHandle> findReleases(DirectoryHandle sectionDir, User user, String title, int year) throws FileNotFoundException {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		AdvancedSearchParams params = new AdvancedSearchParams();

		IMDBQueryParams queryParams;
		try {
			queryParams = params.getExtensionData(IMDBQueryParams.IMDBQUERYPARAMS);
		} catch (KeyNotFoundException e) {
			queryParams = new IMDBQueryParams();
			params.addExtensionData(IMDBQueryParams.IMDBQUERYPARAMS, queryParams);
		}
		queryParams.setTitle(title);
		queryParams.setMinYear(year);
		queryParams.setMaxYear(year);

		params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		params.setSortField("lastmodified");
		params.setSortOrder(true);

		try {
			inodes = ie.advancedFind(sectionDir, params);
		} catch (IndexException e) {
			throw new FileNotFoundException("Index Exception: "+e.getMessage());
		}

		ArrayList<DirectoryHandle> releases = new ArrayList<>();

		for (Map.Entry<String,String> item : inodes.entrySet()) {
			try {
				DirectoryHandle inode = new DirectoryHandle(VirtualFileSystem.fixPath(item.getKey()));
				if (!inode.isHidden(user)) {
					releases.add(inode);
				}
			} catch (FileNotFoundException e) {
				// This is ok, could be multiple nukes fired and
				// that is has not yet been reflected in index due to async event.
			}
		}

		return releases;
	}

	public static boolean containSection (SectionInterface section, ArrayList<SectionInterface> sectionList) {
		boolean containsSection = false;
		for (SectionInterface sec : sectionList) {
			if (section.getName().equals(sec.getName())) {
				containsSection = true;
				break;
			}
		}
		return containsSection;
	}
}
