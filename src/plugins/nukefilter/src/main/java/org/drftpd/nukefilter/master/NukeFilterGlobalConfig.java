package org.drftpd.nukefilter.master;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.sections.SectionInterface;

import java.util.ArrayList;

/**
 * @author phew
 */
public class NukeFilterGlobalConfig implements NukeFilterConfigInterface {

    private boolean enabled;
    private Integer nukeDelay;
    private Integer enforceYearNukex;
    private Integer enforceGroupNukex;
    private final ArrayList<SectionInterface> exemptSections;
    private final ArrayList<NukeFilterConfigElement> filterString;
    private final ArrayList<NukeFilterConfigElement> enforceString;
    private final ArrayList<NukeFilterConfigElement> filterRegex;
    private final ArrayList<NukeFilterConfigElement> enforceRegex;
    private final ArrayList<NukeFilterConfigElement> filterYear;
    private final ArrayList<NukeFilterConfigElement> enforceYear;
    private final ArrayList<NukeFilterConfigElement> filterGroup;
    private final ArrayList<NukeFilterConfigElement> enforceGroup;

    public NukeFilterGlobalConfig() {
        enabled = true;
        nukeDelay = 120;
        enforceYearNukex = 3;
        enforceGroupNukex = 3;
        exemptSections = new ArrayList<>();
        filterString = new ArrayList<>();
        enforceString = new ArrayList<>();
        filterRegex = new ArrayList<>();
        enforceRegex = new ArrayList<>();
        filterYear = new ArrayList<>();
        enforceYear = new ArrayList<>();
        filterGroup = new ArrayList<>();
        enforceGroup = new ArrayList<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public Integer getNukeDelay() {
        return nukeDelay;
    }

    public void setNukeDelay(Integer delay) {
        nukeDelay = delay;
    }

    public ArrayList<SectionInterface> getExemptSections() {
        return exemptSections;
    }

    public void setExemptSections(String sections) {
        for (String exempt : sections.split(";")) {
            exemptSections.add(
                    GlobalContext.getGlobalContext().getSectionManager().getSection(exempt));
        }
    }

    public ArrayList<NukeFilterConfigElement> getFilterStringList() {
        return filterString;
    }

    public ArrayList<NukeFilterConfigElement> getEnforceStringList() {
        return enforceString;
    }

    public ArrayList<NukeFilterConfigElement> getFilterRegexList() {
        return filterRegex;
    }

    public ArrayList<NukeFilterConfigElement> getEnforceRegexList() {
        return enforceRegex;
    }

    public ArrayList<NukeFilterConfigElement> getFilterYearList() {
        return filterYear;
    }

    public ArrayList<NukeFilterConfigElement> getEnforceYearList() {
        return enforceYear;
    }

    public ArrayList<NukeFilterConfigElement> getFilterGroupList() {
        return filterGroup;
    }

    public ArrayList<NukeFilterConfigElement> getEnforceGroupList() {
        return enforceGroup;
    }

    public void addFilterStringElement(NukeFilterConfigElement element) {
        filterString.add(element);
    }

    public void addEnforceStringElement(NukeFilterConfigElement element) {
        enforceString.add(element);
    }

    public void addFilterRegexElement(NukeFilterConfigElement element) {
        filterRegex.add(element);
    }

    public void addEnforceRegexElement(NukeFilterConfigElement element) {
        enforceRegex.add(element);
    }

    public void addFilterYearElement(NukeFilterConfigElement element) {
        filterYear.add(element);
    }

    public void addEnforceYearElement(NukeFilterConfigElement element) {
        enforceYear.add(element);
    }

    public void addFilterGroupElement(NukeFilterConfigElement element) {
        filterGroup.add(element);
    }

    public void addEnforceGroupElement(NukeFilterConfigElement element) {
        enforceGroup.add(element);
    }

    public boolean hasFilterStrings() {
        return !filterString.isEmpty();
    }

    public boolean hasEnforceStrings() {
        return !enforceString.isEmpty();
    }

    public boolean hasFilterRegex() {
        return !filterRegex.isEmpty();
    }

    public boolean hasEnforceRegex() {
        return !enforceRegex.isEmpty();
    }

    public boolean hasFilterYears() {
        return !filterYear.isEmpty();
    }

    public boolean hasEnforceYears() {
        return !enforceYear.isEmpty();
    }

    public boolean hasFilterGroups() {
        return !filterGroup.isEmpty();
    }

    public boolean hasEnforceGroups() {
        return !enforceGroup.isEmpty();
    }

    public Integer getEnforceGroupNukex() {
        return enforceGroupNukex;
    }

    public void setEnforceGroupNukex(Integer nukex) {
        enforceGroupNukex = nukex;
    }

    public Integer getEnforceYearNukex() {
        return enforceYearNukex;
    }

    public void setEnforceYearNukex(Integer nukex) {
        enforceYearNukex = nukex;
    }

}
