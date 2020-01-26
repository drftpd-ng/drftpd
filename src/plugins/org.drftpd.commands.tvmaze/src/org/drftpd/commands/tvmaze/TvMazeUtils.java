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

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commands.tvmaze.event.TvMazeEvent;
import org.drftpd.commands.tvmaze.metadata.TvEpisode;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.commands.tvmaze.vfs.TvMazeVFSData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.User;
import org.drftpd.util.HttpUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.lucene.extensions.tvmaze.TvMazeQueryParams;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
public class TvMazeUtils {
	private static final Logger logger = LogManager.getLogger(TvMazeUtils.class);

	private static final String[] _seperators = {".","-","_"};

	public static ReplacerEnvironment getShowEnv(TvMazeInfo tvShow) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		DateTimeFormatter df = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getDateFormat());
		DateTimeFormatter tf = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getTimeFormat());

		env.add("id", tvShow.getID());
		env.add("url", tvShow.getURL());
		env.add("name", tvShow.getName());
		env.add("type", tvShow.getType());
		env.add("language", tvShow.getLanguage());
		env.add("genres", StringUtils.join(tvShow.getGenres(), " | "));
		env.add("status", tvShow.getStatus());
		env.add("runtime", tvShow.getRuntime());
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
		env.add("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(dtf.parseDateTime(tvShow.getPremiered())));
		env.add("network", tvShow.getNetwork());
		env.add("country", tvShow.getCountry());
		env.add("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

		if (tvShow.getPreviousEP() != null) {
			env.add("prevepid", tvShow.getPreviousEP().getID());
			env.add("prevepurl", tvShow.getPreviousEP().getURL());
			env.add("prevepname", tvShow.getPreviousEP().getName());
			env.add("prevepseason", tvShow.getPreviousEP().getSeason());
			env.add("prevepnumber", String.format("%02d", tvShow.getPreviousEP().getNumber()));
			env.add("prevepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getPreviousEP().getAirDate())));
			env.add("prevepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getPreviousEP().getAirDate())));
			env.add("prevepruntime", tvShow.getPreviousEP().getRuntime());
			env.add("prevepsummary", StringUtils.abbreviate(tvShow.getPreviousEP().getSummary(), 250));
			env.add("prevepage", calculateAge(new DateTime(tvShow.getPreviousEP().getAirDate())));
		}
		if (tvShow.getNextEP() != null) {
			env.add("nextepid", tvShow.getNextEP().getID());
			env.add("nextepurl", tvShow.getNextEP().getURL());
			env.add("nextepname", tvShow.getNextEP().getName());
			env.add("nextepseason", tvShow.getNextEP().getSeason());
			env.add("nextepnumber", String.format("%02d", tvShow.getNextEP().getNumber()));
			env.add("nextepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getNextEP().getAirDate())));
			env.add("nextepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvShow.getNextEP().getAirDate())));
			env.add("nextepruntime", tvShow.getNextEP().getRuntime());
			env.add("nextepsummary", StringUtils.abbreviate(tvShow.getNextEP().getSummary(), 250));
			env.add("nextepage", calculateAge(new DateTime(tvShow.getNextEP().getAirDate())));
		}

		return env;
	}

	public static ReplacerEnvironment getEPEnv(TvMazeInfo tvShow, TvEpisode tvEP) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		DateTimeFormatter df = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getDateFormat());
		DateTimeFormatter tf = DateTimeFormat.forPattern(TvMazeConfig.getInstance().getTimeFormat());

		env.add("id", tvShow.getID());
		env.add("url", tvShow.getURL());
		env.add("name", tvShow.getName());
		env.add("type", tvShow.getType());
		env.add("language", tvShow.getLanguage());
		env.add("genres", StringUtils.join(tvShow.getGenres(), " | "));
		env.add("status", tvShow.getStatus());
		env.add("runtime", tvShow.getRuntime());
		DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd");
		env.add("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(dtf.parseDateTime(tvShow.getPremiered())));
		env.add("network", tvShow.getNetwork());
		env.add("country", tvShow.getCountry());
		env.add("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

		env.add("epid", tvEP.getID());
		env.add("epurl", tvEP.getURL());
		env.add("epname", tvEP.getName());
		env.add("epseason", tvEP.getSeason());
		env.add("epnumber", String.format("%02d", tvEP.getNumber()));
		env.add("epairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvEP.getAirDate())));
		env.add("epairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).print(new DateTime(tvEP.getAirDate())));
		env.add("epruntime", tvEP.getRuntime());
		env.add("epsummary", StringUtils.abbreviate(tvEP.getSummary(), 250));
		env.add("epage", calculateAge(new DateTime(tvEP.getAirDate())));

		return env;
	}

	public static TvMazeInfo createTvMazeInfo(JsonObject jObj) throws Exception {
		TvMazeInfo tvmazeInfo = new TvMazeInfo();

		if (jObj.get("id").isJsonPrimitive()) tvmazeInfo.setID(jObj.get("id").getAsInt());
		if (jObj.get("url").isJsonPrimitive()) tvmazeInfo.setURL(jObj.get("url").getAsString());
		if (jObj.get("name").isJsonPrimitive()) tvmazeInfo.setName(jObj.get("name").getAsString());
		if (jObj.get("type").isJsonPrimitive()) tvmazeInfo.setType(jObj.get("type").getAsString());
		if (jObj.get("language").isJsonPrimitive()) tvmazeInfo.setLanguage(jObj.get("language").getAsString());
		if (jObj.get("genres").isJsonArray()) tvmazeInfo.setGenres(
                new Gson().fromJson(jObj.getAsJsonArray("genres"), new TypeToken<String[]>() {}.getType()));
		if (jObj.get("status").isJsonPrimitive()) tvmazeInfo.setStatus(jObj.get("status").getAsString());
		if (jObj.get("runtime").isJsonPrimitive()) tvmazeInfo.setRuntime(jObj.get("runtime").getAsInt());
		if (jObj.get("premiered").isJsonPrimitive()) {
			tvmazeInfo.setPremiered(jObj.get("premiered").getAsString());
		} else {
			tvmazeInfo.setPremiered("1900-01-01");
		}
        JsonObject networkJsonObj = null;
        if (jObj.get("network").isJsonObject()) {
            networkJsonObj = jObj.getAsJsonObject("network");
        } else if (jObj.get("webChannel").isJsonObject()) {
            networkJsonObj = jObj.getAsJsonObject("webChannel");
        }
        if (networkJsonObj != null) {
            if (networkJsonObj.get("name").isJsonPrimitive()) tvmazeInfo.setNetwork(networkJsonObj.get("name").getAsString());
            if (networkJsonObj.get("country").isJsonObject()){
                JsonObject countryJsonObj = networkJsonObj.getAsJsonObject("country");
                if (countryJsonObj.get("name").isJsonPrimitive()) tvmazeInfo.setCountry(countryJsonObj.get("name").getAsString());
            } else {
                tvmazeInfo.setCountry("Unknown Country");
			}
        }
		if (jObj.get("summary").isJsonPrimitive()) tvmazeInfo.setSummary(HttpUtils.htmlToString(jObj.get("summary").getAsString()));
		JsonObject linksObj = jObj.getAsJsonObject("_links");
		if (linksObj != null) {
			JsonObject prevEPObj = linksObj.getAsJsonObject("previousepisode");
			if (prevEPObj != null) {
				// Fetch and parse EP
				if (prevEPObj.get("href").isJsonPrimitive()) {
					String epURL = prevEPObj.get("href").getAsString();
					tvmazeInfo.setPreviousEP(createTvEpisode(fetchEpisodeData(epURL)));
				}
			}
			JsonObject nextEPObj = linksObj.getAsJsonObject("nextepisode");
			if (nextEPObj != null) {
				// Fetch and parse EP
				if (nextEPObj.get("href").isJsonPrimitive()) {
					String epURL = nextEPObj.get("href").getAsString();
					tvmazeInfo.setNextEP(createTvEpisode(fetchEpisodeData(epURL)));
				}
			}
		}

		return tvmazeInfo;
	}

	public static TvMazeInfo createTvMazeInfo(JsonObject jObj, int season, int number) throws Exception{
		TvMazeInfo tvmazeInfo = createTvMazeInfo(jObj);
		ArrayList<TvEpisode> epList = new ArrayList<>();
		JsonObject embeddedObj = jObj.getAsJsonObject("_embedded");
		if (embeddedObj != null) {
			// Add all episodes to a map with sXXeYY as key
			HashMap<String,TvEpisode> episodes = parseEpisodes(embeddedObj);
			if (number >= 0) {
				// Find the single show wanted and add to _epList
				TvEpisode ep = episodes.get("s" + season + "e" + number);
				if (ep != null) epList.add(ep);
			} else if (season >= 0) {
				// All episodes of specified season wanted
				for (TvEpisode ep : episodes.values()) {
					if (ep.getSeason() == season) {
						epList.add(ep);
					}
				}
			}
		}
		if (!epList.isEmpty()) tvmazeInfo.setEPList(epList.toArray(new TvEpisode[epList.size()]));

		return tvmazeInfo;
	}

	public static String getBestMatch(JsonArray jarray, String year, String countrycode) {
		ArrayList<JsonElement> shows = new Gson().fromJson(jarray, new TypeToken<ArrayList<JsonElement>>() {}.getType());
		for (JsonElement show : shows) {
			JsonObject showObj = show.getAsJsonObject().getAsJsonObject("show");
			boolean yearCheck = true;
			boolean countryCheck = true;
			if (!year.isEmpty() && showObj.get("premiered").isJsonPrimitive()) {
				if (!showObj.get("premiered").getAsString().startsWith(year)) {
					yearCheck = false;
				}
			}
			if (!countrycode.isEmpty()) {
				JsonObject networkJsonObj = null;
				if (showObj.get("network").isJsonObject()) {
					networkJsonObj = showObj.getAsJsonObject("network");
				} else if (showObj.get("webChannel").isJsonObject()) {
					networkJsonObj = showObj.getAsJsonObject("webChannel");
				}
				if (networkJsonObj != null) {
					JsonObject countryJsonObj = networkJsonObj.getAsJsonObject("country");
					if (countryJsonObj.get("code").isJsonPrimitive()) {
						if (!countryJsonObj.get("code").getAsString().equals(countrycode)) {
							countryCheck = false;
						}
					}
				}
			}
			if (yearCheck && countryCheck) {
				if (showObj.get("id").isJsonPrimitive()) {
					return showObj.get("id").getAsString();
				}
			}
		}
		return null;
	}

	private static HashMap<String,TvEpisode> parseEpisodes (JsonObject embeddedObj) throws Exception{
		HashMap<String,TvEpisode> episodes = new HashMap<>();
		ArrayList<JsonElement> episodesElement = new Gson().fromJson(embeddedObj.getAsJsonArray("episodes"), new TypeToken<ArrayList<JsonElement>>() {}.getType());
		for (JsonElement episode : episodesElement) {
			TvEpisode ep = createTvEpisode(episode.getAsJsonObject());
			episodes.put("s"+ep.getSeason()+"e"+ep.getNumber(), ep);
		}
		return episodes;
	}

	private static JsonObject fetchEpisodeData(String epURL) throws Exception{
		String data = HttpUtils.retrieveHttpAsString(epURL);
		JsonElement root = JsonParser.parseString(data);
		return root.getAsJsonObject();
	}

	public static TvEpisode createTvEpisode(JsonObject jobj) throws Exception {
		TvEpisode tvEP = new TvEpisode();
		if (jobj.get("id").isJsonPrimitive()) tvEP.setID(jobj.get("id").getAsInt());
		if (jobj.get("url").isJsonPrimitive()) tvEP.setURL(jobj.get("url").getAsString());
		if (jobj.get("name").isJsonPrimitive()) tvEP.setName(jobj.get("name").getAsString());
		if (jobj.get("season").isJsonPrimitive()) tvEP.setSeason(jobj.get("season").getAsInt());
		if (jobj.get("number").isJsonPrimitive()) tvEP.setNumber(jobj.get("number").getAsInt());
		if (jobj.get("airstamp").isJsonPrimitive()) {
			tvEP.setAirDate(jobj.get("airstamp").getAsString());
		} else {
			tvEP.setAirDate("1900-01-01T01:00:00-04:00");
		}
		if (jobj.get("runtime").isJsonPrimitive()) tvEP.setRuntime(jobj.get("runtime").getAsInt());
		if (jobj.get("summary").isJsonPrimitive()) tvEP.setSummary(HttpUtils.htmlToString(jobj.get("summary").getAsString()));
		return tvEP;
	}

	private static String calculateAge(DateTime epDate) {

		Period period;
		if (epDate.isBefore(new DateTime())) {
			period = new Period(epDate, new DateTime());
		} else {
			period = new Period(new DateTime(), epDate);
		}

		PeriodFormatter formatter = new PeriodFormatterBuilder()
				.appendYears().appendSuffix("y")
				.appendMonths().appendSuffix("m")
				.appendWeeks().appendSuffix("w")
				.appendDays().appendSuffix("d ")
				.appendHours().appendSuffix("h")
				.appendMinutes().appendSuffix("m")
				.printZeroNever().toFormatter();

		return formatter.print(period);
	}

	public static String filterTitle(String title) {
		String newTitle = title.toLowerCase();
		//remove filtered words
		for (String filter : TvMazeConfig.getInstance().getFilters()) {
			newTitle = newTitle.replaceAll("\\b"+filter.toLowerCase()+"\\b","");
		}
		//remove seperators
		for (String separator : _seperators) {
			newTitle = newTitle.replaceAll("\\"+separator," ");
		}
		newTitle = newTitle.trim();
		//remove extra spaces
		newTitle = newTitle.replaceAll("\\s+","%20");
		return newTitle;
	}

	public static ArrayList<DirectoryHandle> findReleases(DirectoryHandle sectionDir, User user, String showName, int season, int number) throws FileNotFoundException {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		AdvancedSearchParams params = new AdvancedSearchParams();

		TvMazeQueryParams queryParams;
		try {
			queryParams = params.getExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS);
		} catch (KeyNotFoundException e) {
			queryParams = new TvMazeQueryParams();
			params.addExtensionData(TvMazeQueryParams.TvMazeQUERYPARAMS, queryParams);
		}
		queryParams.setName(showName);
		queryParams.setSeason(season);
		queryParams.setNumber(number);

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

	public static long randomNumber() {
		return (TvMazeConfig.getInstance().getStartDelay() + (new SecureRandom()).nextInt(
				TvMazeConfig.getInstance().getEndDelay()-TvMazeConfig.getInstance().getStartDelay()
				))*1000;
	}

	public static TvMazeInfo getTvMazeInfoFromCache(DirectoryHandle dir) {
		TvMazeVFSData tvmazeData = new TvMazeVFSData(dir);
		return tvmazeData.getTvMazeInfoFromCache();
	}

	public static TvMazeInfo getTvMazeInfo(DirectoryHandle dir) {
		TvMazeVFSData tvmazeData = new TvMazeVFSData(dir);
		try {
			return tvmazeData.getTvMazeInfo();
		} catch (FileNotFoundException e) {
			// Thats strange...
			logger.error("",e);
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

	public static void publishEvent(TvMazeInfo tvmazeInfo, DirectoryHandle dir, SectionInterface section) {
		if (tvmazeInfo != null) {
			// TvMaze show found, announce to IRC
			ReplacerEnvironment env;
			if (tvmazeInfo.getEPList().length == 1) {
				env = getEPEnv(tvmazeInfo, tvmazeInfo.getEPList()[0]);
			} else {
				env = getShowEnv(tvmazeInfo);
			}
			env.add("release", dir.getName());
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			GlobalContext.getEventService().publishAsync(new TvMazeEvent(env, dir));
		}
	}

	public static boolean isRelease(String dirName) {
		Pattern p = Pattern.compile("(\\w+\\.){3,}\\w+-\\w+");
		Matcher m = p.matcher(dirName);
		return m.find();
	}

	public static Comparator<TvEpisode> epNumberComparator = Comparator.comparingInt(TvEpisode::getNumber);

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
