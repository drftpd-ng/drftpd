package org.drftpd.plugins.nukefilter;

import java.util.ArrayList;

/**
 * @author phew
 */
public interface NukeFilterConfigInterface {
	
	public void setNukeDelay(Integer delay);
	public Integer getNukeDelay();
	
	public ArrayList<NukeFilterConfigElement> getFilterStringList();
	public ArrayList<NukeFilterConfigElement> getEnforceStringList();
	public ArrayList<NukeFilterConfigElement> getFilterRegexList();
	public ArrayList<NukeFilterConfigElement> getEnforceRegexList();
	public ArrayList<NukeFilterConfigElement> getFilterYearList();
	public ArrayList<NukeFilterConfigElement> getEnforceYearList();
	public ArrayList<NukeFilterConfigElement> getFilterGroupList();
	public ArrayList<NukeFilterConfigElement> getEnforceGroupList();
	
	public void addFilterStringElement(NukeFilterConfigElement element);
	public void addEnforceStringElement(NukeFilterConfigElement element);
	public void addFilterRegexElement(NukeFilterConfigElement element);
	public void addEnforceRegexElement(NukeFilterConfigElement element);
	public void addFilterYearElement(NukeFilterConfigElement element);
	public void addEnforceYearElement(NukeFilterConfigElement element);
	public void addFilterGroupElement(NukeFilterConfigElement element);
	public void addEnforceGroupElement(NukeFilterConfigElement element);
	
	public boolean hasFilterStrings();
	public boolean hasEnforceStrings();
	public boolean hasFilterRegex();
	public boolean hasEnforceRegex();
	public boolean hasFilterYears();
	public boolean hasEnforceYears();
	public boolean hasFilterGroups();
	public boolean hasEnforceGroups();
	
}
