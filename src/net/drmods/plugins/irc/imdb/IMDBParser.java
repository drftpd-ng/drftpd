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
package net.drmods.plugins.irc.imdb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.drftpd.irc.SiteBot;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author Teflon
 */
public class IMDBParser {
    private String[] _seperators = {".","-","_"};
    private String[] _filters;
    
    private static final String _baseUrl = "http://www.imdb.com";
    private static final String _searchUrl = "http://www.imdb.com/find?tt=on;nm=on;mx=5;q=";
    
    private boolean _foundFilm;
    
    private String _title;
    private String _genre;
    private String _plot;
    private String _votes;
    private String _rating;
    private String _year;
    private String _url;
    
    public IMDBParser(String searchStr, String filters) {
		_filters = filters.split(";");
		_foundFilm = getInfo(searchStr);
    }

    public String getGenre()  { return foundFilm() ? _genre  : "N/A"; }
    public String getPlot()   { return foundFilm() ? _plot   : "N/A"; }
    public String getRating() { return foundFilm() ? _rating : "N/A"; }
    public String getTitle()  { return foundFilm() ? _title  : "N/A"; }
    public String getVotes()  { return foundFilm() ? _votes  : "N/A"; }
    public String getYear()   { return foundFilm() ? _year   : "N/A"; }
    public String getURL()    { return foundFilm() ? _url    : "N/A"; }
    public boolean foundFilm() { return _foundFilm; }
    
    private boolean getInfo(String searchString) {
        try {
            String urlString = _searchUrl + filterTitle(searchString);
            
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            
            if (!(urlConn.getContent() instanceof InputStream))
                return false;
            
            String data = "";
            BufferedReader in = null;
            String line;
            try {
				in = new BufferedReader(new InputStreamReader(urlConn
						.getInputStream()));
				while ((line = in.readLine()) != null) {
					data += line + "\n";
				}
			} finally {
				if (in != null) {
					in.close();
				}
			}
            
            
            if (data.indexOf("<b>No Matches.</b>") > 0)
                return false;
            
            if (data.indexOf("<title>IMDb name and title search") >= 0 ||
                    data.indexOf("<a href=\"/title/tt") >= 0) {
                int start = data.indexOf("/title/tt");
                if (start > 0) {
                    int end = data.indexOf("/",start + "/title/tt".length());
                    _url = data.substring(start,end);
                    if (_url.indexOf("http://") < 0)
                        _url = _baseUrl + _url;
                }
                if (_url == null) 
                    return false;
            } else {
                _url = urlString;
            }
            
            url = new URL(_url);
            urlConn = url.openConnection();
            if (!(urlConn.getContent() instanceof InputStream))
                return false;
            try {
				in = new BufferedReader(new InputStreamReader(urlConn
						.getInputStream()));

				while ((line = in.readLine()) != null)
					data = data + line + "\n";
			} finally {
				in.close();
			}
            
           _title = parseData(data, "<h1><strong class=\"title\">", "<small>");
           _genre = parseData(data, "<b class=\"ch\">Genre:</b>", "<br><br>");
           _genre = _genre.replaceAll("\\(more\\)", "").trim();
           _plot = parseData(data, "<b class=\"ch\">Plot Outline:</b>", "<a href=\"");
           _rating = parseData(data, "<b class=\"ch\">User Rating:</b>", "</b>");
           _rating = _rating.equals("N/A") || _rating.indexOf("/") < 0 ? "N/A" 
                       : _rating.substring(0,_rating.indexOf("/"));
           _votes = parseData(data, "<b class=\"ch\">User Rating:</b>", "<br><br>");
           _votes = _votes.indexOf("(") < 0 || _votes.indexOf("votes") < 0 ? "N/A" 
                       : _votes.substring(_votes.indexOf("(")+1, _votes.indexOf("votes")).trim();
           _year = parseData(data, "<a href=\"/Sections/Years/", "</a>");
           if (_year.length() >= 6)
        	   _year = _year.substring(6);
               
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
 
        return true;
    }
    
    public ReplacerEnvironment getEnv() {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("title", getTitle());
        env.add("genre", getGenre());
        env.add("plot", getPlot());
        env.add("rating", getRating());
        env.add("votes", getVotes());
        env.add("year", getYear());
        env.add("url", getURL());
        return env;
    }
    
    private String filterTitle(String title) {
        String newTitle = title.toLowerCase();
        
        //remove the group name
        if (newTitle.lastIndexOf("-") >= 0)
            newTitle = newTitle.substring(0, newTitle.lastIndexOf("-"));
        
        //remove seperators
        for (int i=0; i < _seperators.length; i++) 
            newTitle = newTitle.replaceAll("\\"+_seperators[i].toLowerCase()," ");
        
        //remove filtered words
        for (int i=0; i < _filters.length; i++) 
            newTitle = newTitle.replaceAll("\\b"+_filters[i].toLowerCase()+"\\b","");            
        
        //remove extra spaces
        while (newTitle.indexOf("  ") > 0) 
            newTitle = newTitle.replaceAll("  "," ");
        
        //convert spaces to +
        newTitle = newTitle.trim().replaceAll("\\s","+");
        
        return newTitle;
    }
    
    private String parseData(String data, String startText, String endText) {
        int start, end;
        start = data.indexOf(startText);
        if (start > 0) {
            start = start + startText.length();
            end = data.indexOf(endText, start);
            return htmlToString(data.substring(start, end)).trim();
        }        
        return "N/A";
    }
    
	private String htmlToString(String input) {
	    String str = input.replaceAll("\n","");
		while(str.indexOf("<")!=-1)
		{
			int startPos = str.indexOf("<");
			int endPos = str.indexOf(">",startPos);
			if (endPos>startPos)
			{
				String beforeTag = str.substring(0,startPos);
				String afterTag = str.substring(endPos+1);
				str = beforeTag + afterTag;
			}
		}

	    String mbChar;
		String mbs = "&#(\\d+);";
	    StringBuffer sb = new StringBuffer();
	    Pattern pat = Pattern.compile(mbs);
	    Matcher mat = pat.matcher(str);

	    while (mat.find()){
	      mbChar = getMbCharStr(mat.group(1));
	      mat.appendReplacement(sb, mbChar);
	    }
	    mat.appendTail(sb);
	    return new String(sb);
	}


	private String getMbCharStr(String digits) { 
	    char[] cha = new char[1];

	    try{
	      int val = Integer.parseInt(digits);
	      char ch = (char)val;
	      cha[0] = ch;
	    }
	    catch(Exception e){
	      System.err.println("Error from getMbCharStr:");
	      e.printStackTrace(System.err);
	    }
	    return new String(cha); 
    }	
}
