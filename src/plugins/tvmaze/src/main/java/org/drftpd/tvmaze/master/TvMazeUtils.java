/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.tvmaze.master;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.IndexEngineInterface;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.util.HttpUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.tvmaze.master.event.TvMazeEvent;
import org.drftpd.tvmaze.master.index.TvMazeQueryParams;
import org.drftpd.tvmaze.master.metadata.TvEpisode;
import org.drftpd.tvmaze.master.metadata.TvMazeInfo;
import org.drftpd.tvmaze.master.vfs.TvMazeVFSData;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.security.SecureRandom;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

import java.time.format.DateTimeFormatter;

import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author scitz0
 */
public class TvMazeUtils {
    private static final Logger logger = LogManager.getLogger(TvMazeUtils.class);

    private static final String[] _seperators = {".", "-", "_"};
    public static Comparator<TvEpisode> epNumberComparator = Comparator.comparingInt(TvEpisode::getNumber);

    public static Map<String, Object> getShowEnv(TvMazeInfo tvShow) {
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(TvMazeConfig.getInstance().getDateFormat());
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(TvMazeConfig.getInstance().getTimeFormat());

        env.put("id", tvShow.getID());
        env.put("tvurl", tvShow.getURL());
        env.put("tvname", tvShow.getName());
        env.put("type", tvShow.getType());
        env.put("language", tvShow.getLanguage());
        env.put("genres", StringUtils.join(tvShow.getGenres(), " | "));
        env.put("status", tvShow.getStatus());
        env.put("runtime", tvShow.getRuntime());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        env.put("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).format(dtf.parse(tvShow.getPremiered())));
        env.put("network", tvShow.getNetwork());
        env.put("country", tvShow.getCountry());
        env.put("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

        if (tvShow.getPreviousEP() != null) {
            env.put("prevepid", tvShow.getPreviousEP().getID());
            env.put("prevepurl", tvShow.getPreviousEP().getURL());
            env.put("prevepname", tvShow.getPreviousEP().getName());
            env.put("prevepseason", tvShow.getPreviousEP().getSeason());
            env.put("prevepnumber", String.format("%02d", tvShow.getPreviousEP().getNumber()));
            env.put("prevepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvShow.getPreviousEP().getAirDate())));
            env.put("prevepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvShow.getPreviousEP().getAirDate())));
            env.put("prevepruntime", tvShow.getPreviousEP().getRuntime());
            env.put("prevepsummary", StringUtils.abbreviate(tvShow.getPreviousEP().getSummary(), 250));
            env.put("prevepage", calculateAge(ZonedDateTime.parse(tvShow.getPreviousEP().getAirDate())));
        }
        if (tvShow.getNextEP() != null) {
            env.put("nextepid", tvShow.getNextEP().getID());
            env.put("nextepurl", tvShow.getNextEP().getURL());
            env.put("nextepname", tvShow.getNextEP().getName());
            env.put("nextepseason", tvShow.getNextEP().getSeason());
            env.put("nextepnumber", String.format("%02d", tvShow.getNextEP().getNumber()));
            env.put("nextepairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvShow.getNextEP().getAirDate())));
            env.put("nextepairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvShow.getNextEP().getAirDate())));
            env.put("nextepruntime", tvShow.getNextEP().getRuntime());
            env.put("nextepsummary", StringUtils.abbreviate(tvShow.getNextEP().getSummary(), 250));
            env.put("nextepage", calculateAge(ZonedDateTime.parse(tvShow.getNextEP().getAirDate())));
        }

        return env;
    }

    public static Map<String, Object> getEPEnv(TvMazeInfo tvShow, TvEpisode tvEP) {
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(TvMazeConfig.getInstance().getDateFormat());
        DateTimeFormatter tf = DateTimeFormatter.ofPattern(TvMazeConfig.getInstance().getTimeFormat());

        env.put("id", tvShow.getID());
        env.put("tvurl", tvShow.getURL());
        env.put("tvname", tvShow.getName());
        env.put("type", tvShow.getType());
        env.put("language", tvShow.getLanguage());
        env.put("genres", StringUtils.join(tvShow.getGenres(), " | "));
        env.put("status", tvShow.getStatus());
        env.put("runtime", tvShow.getRuntime());
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        env.put("premiered", df.withZone(TvMazeConfig.getInstance().getTimezone()).format(dtf.parse(tvShow.getPremiered())));
        env.put("network", tvShow.getNetwork());
        env.put("country", tvShow.getCountry());
        env.put("summary", StringUtils.abbreviate(tvShow.getSummary(), 250));

        env.put("epid", tvEP.getID());
        env.put("epurl", tvEP.getURL());
        env.put("epname", tvEP.getName());
        env.put("epseason", tvEP.getSeason());
        env.put("epnumber", String.format("%02d", tvEP.getNumber()));
        env.put("epairdate", df.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvEP.getAirDate())));
        env.put("epairtime", tf.withZone(TvMazeConfig.getInstance().getTimezone()).format(OffsetDateTime.parse(tvEP.getAirDate())));
        env.put("epruntime", tvEP.getRuntime());
        env.put("epsummary", StringUtils.abbreviate(tvEP.getSummary(), 250));
        env.put("epage", calculateAge(ZonedDateTime.parse(tvEP.getAirDate())));

        return env;
    }

    public static TvMazeInfo createTvMazeInfo(JsonObject jObj) throws Exception, JsonSyntaxException {
        TvMazeInfo tvmazeInfo = new TvMazeInfo();

        if (jObj.get("id").isJsonPrimitive()) tvmazeInfo.setID(jObj.get("id").getAsInt());
        if (jObj.get("url").isJsonPrimitive()) tvmazeInfo.setURL(jObj.get("url").getAsString());
        if (jObj.get("name").isJsonPrimitive()) tvmazeInfo.setName(jObj.get("name").getAsString());
        if (jObj.get("type").isJsonPrimitive()) tvmazeInfo.setType(jObj.get("type").getAsString());
        if (jObj.get("language").isJsonPrimitive()) tvmazeInfo.setLanguage(jObj.get("language").getAsString());
        if (jObj.get("genres").isJsonArray()) tvmazeInfo.setGenres(
                new Gson().fromJson(jObj.getAsJsonArray("genres"), new TypeToken<String[]>() {
                }.getType()));
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
            if (networkJsonObj.get("name").isJsonPrimitive())
                tvmazeInfo.setNetwork(networkJsonObj.get("name").getAsString());
            if (networkJsonObj.get("country").isJsonObject()) {
                JsonObject countryJsonObj = networkJsonObj.getAsJsonObject("country");
                if (countryJsonObj.get("name").isJsonPrimitive())
                    tvmazeInfo.setCountry(countryJsonObj.get("name").getAsString());
            } else {
                tvmazeInfo.setCountry("Unknown Country");
            }
        }
        if (jObj.get("summary").isJsonPrimitive())
            tvmazeInfo.setSummary(HttpUtils.htmlToString(jObj.get("summary").getAsString()));
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

    public static TvMazeInfo createTvMazeInfo(JsonObject jObj, int season, int number) throws Exception {
        TvMazeInfo tvmazeInfo = createTvMazeInfo(jObj);
        ArrayList<TvEpisode> epList = new ArrayList<>();
        JsonObject embeddedObj = jObj.getAsJsonObject("_embedded");
        if (embeddedObj != null) {
            // Add all episodes to a map with sXXeYY as key
            HashMap<String, TvEpisode> episodes = parseEpisodes(embeddedObj);
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
        if (!epList.isEmpty()) tvmazeInfo.setEPList(epList.toArray(new TvEpisode[0]));

        return tvmazeInfo;
    }

    public static String getBestMatch(JsonArray jarray, String year, String countrycode) {
        ArrayList<JsonElement> shows = new Gson().fromJson(jarray, new TypeToken<ArrayList<JsonElement>>() {
        }.getType());
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

    private static HashMap<String, TvEpisode> parseEpisodes(JsonObject embeddedObj) {
        HashMap<String, TvEpisode> episodes = new HashMap<>();
        ArrayList<JsonElement> episodesElement = new Gson().fromJson(embeddedObj.getAsJsonArray("episodes"), new TypeToken<ArrayList<JsonElement>>() {}.getType());
        for (JsonElement episode : episodesElement) {
            TvEpisode ep = createTvEpisode(episode.getAsJsonObject());
            episodes.put("s" + ep.getSeason() + "e" + ep.getNumber(), ep);
        }
        return episodes;
    }

    private static JsonObject fetchEpisodeData(String epURL) throws org.apache.hc.core5.http.HttpException, IOException, JsonSyntaxException {
        String data = HttpUtils.retrieveHttpAsString(epURL);
        JsonElement root = JsonParser.parseString(data);
        return root.getAsJsonObject();
    }

    public static TvEpisode createTvEpisode(JsonObject jobj) {
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

    private static String calculateAge(ZonedDateTime epDate) {
        // Get now
        ZonedDateTime now = ZonedDateTime.now();

        // Get the (positive) time between now and epData
        ZonedDateTime t1 = now;
        ZonedDateTime t2 = epDate;
        if (epDate.isBefore(now)) {
            t1 = epDate;
            t2 = now;
        }

        String age = "";
        long years = ChronoUnit.YEARS.between(t1, t2);
        if (years > 0) {
            age += years+"y";
            t2 = t2.minusYears(years);
        }

        long months = ChronoUnit.MONTHS.between(t1, t2);
        if (months > 0) {
            age += months+"m";
            t2 = t2.minusMonths(months);
        }

        long weeks = ChronoUnit.WEEKS.between(t1, t2);
        if (weeks > 0) {
            age += weeks+"w";
            t2 = t2.minusWeeks(weeks);
        }

        long days = ChronoUnit.DAYS.between(t1, t2);
        if (days > 0) {
            age += days+"d";
            t2 = t2.minusDays(days);
        }
        boolean spaceadded = false;

        long hours = ChronoUnit.HOURS.between(t1, t2);
        if (hours > 0) {
            age += " "+hours+"h";
            spaceadded = true;
            t2 = t2.minusHours(hours);
        }

        long minutes = ChronoUnit.MINUTES.between(t1, t2);
        if (minutes > 0) {
            if (!spaceadded) { age += " ";  }
            age += minutes+"m";
        }
        if (age.length() == 0) { age = "0"; }

        return age;
    }

    public static String filterTitle(String title) {
        String newTitle = title.toLowerCase();
        //remove filtered words
        for (String filter : TvMazeConfig.getInstance().getFilters()) {
            newTitle = newTitle.replaceAll("\\b" + filter.toLowerCase() + "\\b", "");
        }
        //remove seperators
        for (String separator : _seperators) {
            newTitle = newTitle.replaceAll("\\" + separator, " ");
        }
        newTitle = newTitle.trim();
        //remove extra spaces
        newTitle = newTitle.replaceAll("\\s+", "%20");
        return newTitle;
    }

    public static ArrayList<DirectoryHandle> findReleases(String caller, DirectoryHandle sectionDir, User user, String showName, int season, int number) throws FileNotFoundException {
        IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
        Map<String, String> inodes;

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
            inodes = ie.advancedFind(sectionDir, params, caller);
        } catch (IndexException e) {
            throw new FileNotFoundException("Index Exception: " + e.getMessage());
        }

        ArrayList<DirectoryHandle> releases = new ArrayList<>();

        for (Map.Entry<String, String> item : inodes.entrySet()) {
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
                TvMazeConfig.getInstance().getEndDelay() - TvMazeConfig.getInstance().getStartDelay()
        )) * 1000;
    }

    public static TvMazeInfo getTvMazeInfoFromCache(DirectoryHandle dir) {
        TvMazeVFSData tvmazeData = new TvMazeVFSData(dir);
        return tvmazeData.getTvMazeInfoFromCache();
    }

    public static TvMazeInfo getTvMazeInfo(DirectoryHandle dir) {
        TvMazeVFSData tvmazeData = new TvMazeVFSData(dir);
        try {
            return tvmazeData.getTvMazeInfo();
        } catch (IOException e) {
            // Thats strange and to bad ...
            logger.error("", e);
        } catch (NoAvailableSlaveException | SlaveUnavailableException e) {
            // Not much to do...
        }
        return null;
    }

    public static void publishEvent(TvMazeInfo tvmazeInfo, DirectoryHandle dir, SectionInterface section) {
        if (tvmazeInfo != null) {
            // TvMaze show found, announce to IRC
            Map<String, Object> env;
            if (tvmazeInfo.getEPList().length == 1) {
                env = getEPEnv(tvmazeInfo, tvmazeInfo.getEPList()[0]);
            } else {
                env = getShowEnv(tvmazeInfo);
            }
            env.put("release", dir.getName());
            env.put("section", section.getName());
            env.put("sectioncolor", section.getColor());
            GlobalContext.getEventService().publishAsync(new TvMazeEvent(env, dir));
        }
    }

    public static boolean isRelease(String dirName) {
        Pattern p = Pattern.compile("(\\w+\\.){3,}\\w+-\\w+");
        Matcher m = p.matcher(dirName);
        return m.find();
    }

    public static boolean containSection(SectionInterface section, ArrayList<SectionInterface> sectionList) {
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
