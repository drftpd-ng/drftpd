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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.util.HttpUtils;
import org.tanesha.replacer.ReplacerEnvironment;

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
	
	public String getTitle()   	{ return foundMovie() ? _title   	: "N|A"; }
	public Integer getYear()   	{ return foundMovie() ? _year		:  null; }
	public String getLanguage()	{ return foundMovie() ? _language	: "N|A"; }
	public String getCountry()	{ return foundMovie() ? _country	: "N|A"; }
	public String getDirector()	{ return foundMovie() ? _director 	: "N|A"; }
	public String getGenres()	{ return foundMovie() ? _genres   	: "N|A"; }
	public String getPlot()		{ return foundMovie() ? _plot		: "N|A"; }
	public Integer getRating() 	{ return foundMovie() ? _rating  	:  null; }
	public Integer getVotes()  	{ return foundMovie() ? _votes   	:  null; }
	public String getURL()	 	{ return foundMovie() ? _url	 	: "N|A"; }
	public Integer getRuntime()	{ return foundMovie() ? _runtime 	:  null; }
	public boolean foundMovie()	{ return _foundMovie; }
	
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
					int end = data.indexOf("/",start + "/title/tt".length());
					_url = data.substring(start,end);
					_url = _baseUrl + _url;
				}
			}
		} catch (Exception e) {
			logger.error("",e);
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
			String url = _url+"/reference";

			String data = HttpUtils.retrieveHttpAsString(url);

			if (!data.contains("<meta property='og:type' content=\"video.movie\" />")) {
                logger.warn("Request for IMDB info for a Tv Show, this is not handled by this plugin. URL:{}", url);
				return false;
			}

			_title = parseData(data, "<meta property='og:title' content=\"", "(");
			_language = parseData(data, "<td class=\"ipl-zebra-list__label\">Language</td>", "</td>").replaceAll("\\s{2,}","|");
			_country = parseData(data, "<td class=\"ipl-zebra-list__label\">Country</td>", "</td>").replaceAll("\\s{2,}","|");
			_genres = parseData(data, "<td class=\"ipl-zebra-list__label\">Genres</td>", "</td>").replaceAll("\\s{2,}","|");
			_director = parseData(data, "<div class=\"titlereference-overview-section\">\\n\\s+Directors?:", "</div>", false, true).replaceAll("\\s+?,\\s+?","|");
			String rating = parseData(data, "<span class=\"ipl-rating-star__rating\">", "</span>");
			if (!rating.equals("N|A") && (rating.length() == 1 || rating.length() == 3) && NumberUtils.isDigits(rating.replaceAll("\\D",""))) {
				_rating = Integer.valueOf(rating.replaceAll("\\D",""));
				if (rating.length() == 1) {
					// Rating an even(single digit) number, multiply by 10
					_rating = _rating*10;
				}
				String votes = parseData(data, "<span class=\"ipl-rating-star__total-votes\">", "</span>");
				if (!votes.equals("N|A") && NumberUtils.isDigits(votes.replaceAll("\\D","")))
					_votes = Integer.valueOf(votes.replaceAll("\\D",""));
			}
			_plot = parseData(data, "<section class=\"titlereference-section-overview\">", "</div>", true, true);
			Pattern p = Pattern.compile("<a href=\"/title/tt\\d+/releaseinfo\">\\d{2} [a-zA-Z]{3} (\\d{4})");
			Matcher m = p.matcher(data);
			if (m.find() && NumberUtils.isDigits(m.group(1))) {
				_year = Integer.valueOf(m.group(1));
			}
			String runtime = parseData(data, "<td class=\"ipl-zebra-list__label\">Runtime</td>", "</td>");
			if (!runtime.equals("N|A") && NumberUtils.isDigits(runtime.replaceAll("\\D",""))) {
				_runtime = Integer.valueOf(runtime.replaceAll("\\D", ""));
			}
		} catch (Exception e) {
			logger.error("",e);
			return false;
		}
		return true;
	}
	
	public ReplacerEnvironment getEnv() {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("title", getTitle());
		env.add("director", getDirector());
		env.add("genres", getGenres());
		env.add("language", getLanguage());
		env.add("country", getCountry());
		env.add("plot", getPlot());
		env.add("rating", getRating() != null ? getRating()/10+"."+getRating()%10 : "0");
		env.add("votes", getVotes() != null ? getVotes() : "0");
		env.add("year", getYear() != null ? getYear() : "9999");
		env.add("url", getURL());
		env.add("runtime", getRuntime() != null ? getRuntime() : "0");
		env.add("searchstr", _searchString != null ? _searchString : "");
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
