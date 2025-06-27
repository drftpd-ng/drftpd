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
package org.drftpd.imdb.master;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.util.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lh
 */
public class IMDBParser {
    private static final Logger logger = LogManager.getLogger(IMDBParser.class);

    private static final String _baseUrl = "https://imdb.com";
    private static final String _searchUrl = "https://imdb.com/find?s=all&q=";

    private String _title;
    private Integer _year;
    private String _language;
    private String _country;
    private String _director;
    private String _genres;
    private String _plot;
    private Integer _rating;
    private Integer _votes;
    private String _url;
    private Integer _runtime;
    private String _searchString;
    private boolean _foundMovie;

    public String getTitle() { return foundMovie() ? _title : "N|A"; }

    public Integer getYear() { return foundMovie() ? _year : null; }

    public String getLanguage() { return foundMovie() ? _language : "N|A"; }

    public String getCountry() { return foundMovie() ? _country : "N|A"; }

    public String getDirector() { return foundMovie() ? _director : "N|A"; }

    public String getGenres() { return foundMovie() ? _genres : "N|A"; }

    public String getPlot() { return foundMovie() ? _plot : "N|A"; }

    public Integer getRating() { return foundMovie() ? _rating : null; }

    public Integer getVotes() { return foundMovie() ? _votes : null; }

    public String getURL() { return foundMovie() ? _url : "N|A"; }

    public Integer getRuntime() { return foundMovie() ? _runtime : null; }

    public boolean foundMovie() { return _foundMovie; }

    public void doSEARCH(String searchString) {
        _searchString = searchString;
        try {
            String urlString = _searchUrl + searchString;

            String data = HttpUtils.retrieveHttpAsString(urlString);

            if (data.indexOf("<b>No Matches.</b>") > 0) {
                _foundMovie = false;
                return;
            }

            int titleIndex = data.indexOf("<a name=\"tt\">");
            if (titleIndex > 0) {
                int start = data.indexOf("/title/tt", titleIndex);
                if (start > 0) {
                    int end = data.indexOf("/", start + "/title/tt".length());
                    _url = data.substring(start, end);
                    _url = _baseUrl + _url;
                }
            }
        } catch (Exception e) {
            logger.error("", e);
            _foundMovie = false;
            return;
        }
        if (_url == null) {
            _foundMovie = false;
            return;
        }
        _foundMovie = getInfo();
    }

    public void doNFO(String url) {
        _url = url;
        _foundMovie = getInfo();
    }

    private boolean getInfo() {
        try {
            String url = _url + "/reference";

            String data = HttpUtils.retrieveHttpAsString(url);

            if (!data.contains("<meta property=\"og:type\" content=\"video.movie\"/>")) {
                logger.warn("Request for IMDB info for a Tv Show, this is not handled by this plugin. URL:{}", url);
                return false;
            }

            _title = parseData(data, "<meta property=\"og:title\" content=\"", "(");
            _language = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Languages</span>", "</ul>").replaceAll("(?<!^)([A-Z])", "|$1");
            _country = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Countries of origin</span>", "</a>").replaceAll("\\s{2,}", "|");
            _genres = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Genres</span>", "</ul>").replaceAll("(?<!^)([A-Z])", "|$1");
            _director = parseData(data, " name-credits--crew-content\">", "</a>", false, true).replaceAll("\\s+?,\\s+?", "|");

            //fallback
            if (_language.equals("N||A")) {
                _language = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Language</span>", "</ul>").replaceAll("(?<!^)([A-Z])", "|$1");
            }

            if (_country.equals("N|A")) {
                _country = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Country of origin</span>", "</a>").replaceAll("\\s{2,}", "|");
            }

            String rating = parseData(data, "<span class=\"ipc-rating-star--rating\">", "</span>");

            if (!rating.equals("N|A") && (rating.length() == 1 || rating.length() == 3) && NumberUtils.isDigits(rating.replaceAll("\\D", ""))) {
                _rating = Integer.valueOf(rating.replaceAll("\\D", ""));
                if (rating.length() == 1) {
                    // Rating an even(single digit) number, multiply by 10
                    _rating = _rating * 10;
                }
                String votes = parseData(data, "<span class=\"ipc-rating-star--voteCount\">", "</span>");
                if (!votes.equals("N|A") && NumberUtils.isDigits(votes.replaceAll("\\D", "")))
                    _votes = Integer.valueOf(votes.replaceAll("\\D", "")) * 1000;
            }
            _plot = parseData(data, "<ul class=\"ipc-metadata-list ipc-metadata-list--dividers-between ipc-metadata-list--compact ipc-metadata-list--base\" role=\"presentation\">", "</span>", true, true);

			String year = parseData(data, "<span class=\"hero__primary-text-suffix\" data-testid=\"hero__primary-text-suffix\">(", ")</span>");
            if (!year.equals("N|A") && NumberUtils.isDigits(year.replaceAll("\\D", ""))) {
                _year = Integer.parseInt(year);
            }
            String runtime = parseData(data, "<span class=\"ipc-metadata-list-item__label ipc-btn--not-interactable\" aria-disabled=\"false\">Runtime</span>", "</li>");
            if (!runtime.equals("N|A") && NumberUtils.isDigits(runtime.replaceAll("\\D", ""))) {
                _runtime = Integer.parseInt(runtime.replaceAll(".*\\((\\d+) min\\).*", "$1"));
            }
        } catch (Exception e) {
            logger.error("", e);
            return false;
        }
        return true;
    }

    public Map<String, Object> getEnv() {
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        env.put("title", getTitle());
        env.put("director", getDirector());
        env.put("genres", getGenres());
        env.put("language", getLanguage());
        env.put("country", getCountry());
        env.put("plot", getPlot());
        env.put("rating", getRating() != null ? getRating() / 10 + "." + getRating() % 10 : "0");
        env.put("votes", getVotes() != null ? getVotes() : "0");
        env.put("year", getYear() != null ? getYear() : "9999");
        env.put("url", getURL());
        env.put("runtime", getRuntime() != null ? getRuntime() : "0");
        env.put("searchstr", _searchString != null ? _searchString : "");
        return env;
    }

    private String parseData(String data, String startText, String endText) {
        return parseData(data, startText, endText, false, false);
    }

    private String parseData(String data, String startText, String endText, boolean beginning, boolean regex) {
        int start, end;
        if (regex) {
            Pattern p = Pattern.compile(startText);
            Matcher m = p.matcher(data);
            if (m.find()) {
                start = m.start();
                if (!beginning) {
                    start = start + m.group().length();
                }
                p = Pattern.compile(endText);
                // Always start end search from end of start match even if beginning flag is set.
                m = p.matcher(data.substring(start));
                if (m.find()) {
                    end = m.start() + start;
                    return HttpUtils.htmlToString(data.substring(start, end)).trim();
                }
            }
        } else {
            start = data.indexOf(startText);
            if (start > 0) {
                if (!beginning) {
                    start = start + startText.length();
                }
                end = data.indexOf(endText, start);
                return HttpUtils.htmlToString(data.substring(start, end)).trim();
            }
        }
        return "N|A";
    }
}
