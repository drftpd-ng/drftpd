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
import org.drftpd.plugins.sitebot.SiteBot;

import org.apache.log4j.Logger;

import org.drftpd.util.HttpUtils;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author lh
 */
public class IMDBParser {
	private static final Logger logger = Logger.getLogger(IMDBParser.class); 
  
	private static final String _baseUrl = "http://akas.imdb.com";
	private static final String _searchUrl = "http://akas.imdb.com/find?s=all&q=";
	
	private boolean _foundMovie;
	
	private String _title;
	private String _director;
	private String _genre;
	private String _plot;
	private Integer _votes;
	private Integer _rating;
	private Integer _year;
	private String _url;
	private Integer _screens;
	private String _limited;
	private String _searchString;
	
	public String getGenre()   	{ return foundMovie() ? _genre   	: "N|A"; }
	public String getDirector() { return foundMovie() ? _director 	: "N|A"; }
	public String getPlot()		{ return foundMovie() ? _plot		: "N|A"; }
	public Integer getRating() 	{ return foundMovie() ? _rating  	:  null; }
	public String getTitle()   	{ return foundMovie() ? _title   	: "N|A"; }
	public Integer getVotes()  	{ return foundMovie() ? _votes   	:  null; }
	public Integer getYear()   	{ return foundMovie() ? _year		:  null; }
	public String getURL()	 	{ return foundMovie() ? _url	 	: "N|A"; }
	public Integer getScreens()	{ return foundMovie() ? _screens 	:  null; }
	public String getLimited() 	{ return foundMovie() ? _limited 	: "";	}
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

			_title = parseData(data, "<div id=\"tn15title\">", "<span>");
			_genre = parseData(data, "<h5>Genre:</h5>", "</div>").replaceAll("See more","").trim().replaceAll("\\s+","");
			_director = parseData(data, "<h5>Director:</h5>", "</div>");
			if (_director.equals("N|A")) {
				_director = parseData(data, "Directors:", "</div>").replaceAll("\\s{2,}","|");
			}
			String rating = parseData(data, "<div class=\"starbar-meta\">", "</b>").replaceAll("/10","");
			if (!rating.equals("N|A") &&
					NumberUtils.isDigits(rating.replaceAll("\\D","")) &&
					!rating.contains("(awaiting 5 votes)")) {
				_rating = Integer.valueOf(rating.replaceAll("\\D",""));
				String votes = parseData(data, "<a href=\"ratings\" class=\"tn15more\">", " votes</a>");
				if (!votes.equals("N|A") && NumberUtils.isDigits(votes.replaceAll("\\D","")))
					_votes = Integer.valueOf(votes.replaceAll("\\D",""));
			}
			_plot = parseData(data, "<h5>Plot:</h5>", "<a class=\"tn15more inline\"").replaceAll("\\s\\|","");
			String year = parseData(data, "<a href=\"/year/", "</a>", true).replaceAll("\\D","");
			if (year.length() == 4) {
				_year = Integer.valueOf(year);
			}

			_limited = "";
			try {
				url = _url+"/business";
				data = HttpUtils.retrieveHttpAsString(url);
				String screens = parseData(data, "<h5>Opening Weekend</h5>", "<br/>");
				if (!screens.equals("N|A") && screens.contains(" Screens)") && screens.lastIndexOf(") (") >= 0) {
					int start = screens.lastIndexOf(") (") + 3;
					int end = screens.indexOf(" Screens)");
					if (start < end) {
						screens = screens.substring(start, end).replaceAll("\\D", "").trim();
						if (!screens.isEmpty()) {
							_screens = Integer.valueOf(screens);
							if (_screens < 600) {
								_limited = " (Limited)";
							}
						}
					}
				}
			} catch (Exception e) {
				logger.warn("", e);
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
		env.add("genre", getGenre());
		env.add("plot", getPlot());
		env.add("rating", getRating() != null ? getRating()/10+"."+getRating()%10 : "0");
		env.add("votes", getVotes() != null ? getVotes() : "0");
		env.add("year", getYear() != null ? getYear() : "9999");
		env.add("url", getURL());
		env.add("screens", getScreens() != null ? getScreens() : "0");
		env.add("limited", getLimited());
		env.add("searchstr", _searchString != null ? _searchString : "");
		return env;
	}

	private String parseData(String data, String startText, String endText) {
		return parseData(data, startText, endText, false);
	}

	private String parseData(String data, String startText, String endText, boolean beginning) {
		int start, end;
		start = data.indexOf(startText);
		if (start > 0) {
			if (!beginning) {
				start = start + startText.length();
			}
			end = data.indexOf(endText, start);
			return HttpUtils.htmlToString(data.substring(start, end)).trim();
		}
		return "N|A";
	}
}
