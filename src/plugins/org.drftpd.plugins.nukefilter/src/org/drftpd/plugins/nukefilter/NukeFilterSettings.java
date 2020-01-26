package org.drftpd.plugins.nukefilter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;

import java.util.HashMap;
import java.util.Properties;

/**
 * @author phew
 */
public class NukeFilterSettings {
	private static final Logger logger = LogManager.getLogger(NukeFilterSettings.class);
	
	private NukeFilterGlobalConfig nfgc;
	private HashMap<String, NukeFilterSectionConfig> nfscMap;
	private NukeFilterNukeConfig nfnc;
	
	/*
	 * The part reading the configuration files from nukefilter.conf
	 * can be done better, though i'm too lazy to proper it right 
	 * now, maybe will do some clean up in future versions.
	 */
	
	public NukeFilterSettings() {
		//this is not really necessary hence its done in reloadConfigs() method
		nfgc = new NukeFilterGlobalConfig(); 
		nfscMap = new HashMap<>();
		nfnc = new NukeFilterNukeConfig();
	}

	/**
	 * This method reloads both, section specific and global NukeFilter configurations.
	 */
	public void reloadConfigs() {
		//destroy previous configs
		nfgc = new NukeFilterGlobalConfig();
		nfscMap = new HashMap<>();
		nfnc = new NukeFilterNukeConfig();
		//grab config file
		Properties props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("nukefilter.conf");
		if(props == null) {
			logger.fatal("conf/plugins/nukefilter.conf not found");
			return;
		}
		/*
		 * load nuker settings
		 */
		//user to run the nukes
		String nuker = PropertyHelper.getProperty(props, "nuke.nuker", "drftpd").trim();
		nfnc.setNuker(nuker);
		//String[] containing regex defining exempt directory names
		String exempts = PropertyHelper.getProperty(props, "nuke.exempt.dirnames.regex", "").trim();
		nfnc.setExempts(exempts);
		/*
		 * load global filter configuration
		 */
		//set nuke filter enabled
		String nukeFilterEnabled = PropertyHelper.getProperty(props, "global.enabled", "true").trim();
		nfgc.setEnabled(Boolean.parseBoolean(nukeFilterEnabled));
		//set nuke delay
		String nukeDelay = PropertyHelper.getProperty(props, "global.nuke.delay", "").trim();
		try {
			nfgc.setNukeDelay(Integer.parseInt(nukeDelay));
		} catch(NumberFormatException e) {
			logger.warn("invalid global.nuke.delay value specified, " +
					"defaulting to '120s'");
			nfgc.setNukeDelay(120);
		}
		//set global enforce year nukex
		String enforceYearNukex = PropertyHelper.getProperty(props, 
				"global.enforce.year.nukex", "").trim();
		try {
			nfgc.setEnforceYearNukex(Integer.parseInt(enforceYearNukex));
		} catch(NumberFormatException e) {
			nfgc.setEnforceYearNukex(3);
			logger.warn("improper formatted global.enforce.year.nukex given, " +
					"defaulting nukex to 3");
		}
		String enforceGroupNukex = PropertyHelper.getProperty(props, 
				"global.enforce.group.nukex", "").trim();
		try {
			nfgc.setEnforceGroupNukex(Integer.parseInt(enforceGroupNukex));
		} catch(NumberFormatException e) {
			nfgc.setEnforceGroupNukex(3);
			logger.warn("improper formatted global.enforce.group.nukex given, " +
					"defaulting nukex to 3");
		}
		//set exempt sections
		String exemptSections = PropertyHelper.getProperty(props, "global.exempt.sections", "").trim();
		nfgc.setExemptSections(exemptSections);
		//set filter strings
		String filterString = PropertyHelper.getProperty(props, "global.filter.string", "").trim();
		if(!filterString.equals("")) {
			for(String strings : filterString.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addFilterStringElement(element);
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.filter.string nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addFilterStringElement(element);
				} else {
                    logger.warn("improper formatted global.filter.string given, skipping one element: '{}'", strings);
				}
			}
		}
		//set enforced strings
		String enforceString = PropertyHelper.getProperty(props, "global.enforce.string", "").trim();
		if(!enforceString.equals("")) {
			for(String strings : enforceString.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addEnforceStringElement(element);
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.enforce.string nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addEnforceStringElement(element);
				} else {
                    logger.warn("improper formatted global.enforce.string given, skipping one element: '{}'", strings);
				}
			}
		}
		//set filter regex
		String filterRegex = PropertyHelper.getProperty(props, "global.filter.regex", "").trim();
		if(!filterRegex.equals("")) {
			for(String strings : filterRegex.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addFilterRegexElement(element);	
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.filter.regex nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addFilterRegexElement(element);
				} else {
                    logger.warn("improper formatted global.filter.regex given, skipping one element: '{}'", strings);
				}
			}
		}
		//set enforce regex
		String enforceRegex = PropertyHelper.getProperty(props, "global.enforce.regex", "").trim();
		if(!enforceRegex.equals("")) {
			for(String strings : enforceRegex.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addEnforceRegexElement(element);
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.enforce.regex nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addEnforceRegexElement(element);
				} else {
                    logger.warn("improper formatted global.enforce.regex given, skipping one element: '{}'", strings);
				}
			}
		}
		//set filter year
		String filterYear = PropertyHelper.getProperty(props, "global.filter.year", "").trim();
		if(!filterYear.equals("")) {
			for(String strings : filterYear.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addFilterYearElement(element);
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.filter.year nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addFilterYearElement(element);
				} else {
                    logger.warn("improper formatted global.filter.year given, skipping one element: '{}'", strings);
				}
			}
		}
		//set enforce year
		String enforceYear = PropertyHelper.getProperty(props, "global.enforce.year", "").trim();
		if(!enforceYear.equals("")) {
			for(String strings : enforceYear.split(";")) {
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				element.setElement(strings);
				nfgc.addEnforceYearElement(element);
			}
		}
		//set filter group
		String filterGroup = PropertyHelper.getProperty(props, "global.filter.group", "").trim();
		if(!filterGroup.equals("")) {
			for(String strings : filterGroup.split(";")) {
				String[] elements = strings.split("~");
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				if(elements.length == 2) {
					try {
						element.setElement(elements[0]);
						element.setNukex(Integer.parseInt(elements[1]));
						nfgc.addFilterGroupElement(element);
					} catch(NumberFormatException e) {
                        logger.warn("improper formatted global.filter.group nukex given, skipping one element: '{}'", strings);
					}
				} else if(elements.length == 1) {
					element.setElement(elements[0]);
					element.setNukex(3);
					nfgc.addFilterGroupElement(element);
				} else {
                    logger.warn("improper formatted global.filter.group given, skipping one element: '{}'", strings);
				}
			}
		}
		//set enforce group
		String enforceGroup = PropertyHelper.getProperty(props, "global.enforce.group", "").trim();
		if(!enforceGroup.equals("")) {
			for(String strings : enforceGroup.split(";")) {
				NukeFilterConfigElement element = new NukeFilterConfigElement();
				element.setElement(strings);
				nfgc.addEnforceGroupElement(element);
			}
		}
		/*
		 * load section specific filter configuration
		 */
		for(int i = 1;; i++) {
			String sectionName = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".section", null);
			String sectionNukeDelay = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".nuke.delay", "").trim();
			String sectionFilterString = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".filter.string", "").trim();
			String sectionEnforceString = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.string", "").trim();
			String sectionFilterRegex = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".filter.regex", "").trim();
			String sectionEnforceRegex = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.regex", "").trim();
			String sectionFilterYear = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".filter.year", "").trim();
			String sectionEnforceYear = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.year", "").trim();
			String sectionEnforceYearNukex = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.year.nukex", "");
			String sectionFilterGroup = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".filter.group", "").trim();
			String sectionEnforceGroup = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.group", "").trim();
			String sectionEnforceGroupNukex = PropertyHelper.getProperty(props, 
					String.valueOf(i)+".enforce.group.nukex", "");
			if(sectionName == null) break;
			NukeFilterSectionConfig nfsc = new NukeFilterSectionConfig();
			try {
				nfsc.setNukeDelay(Integer.parseInt(sectionNukeDelay));
			} catch(NumberFormatException e) {
                logger.warn("invalid {}.nuke.delay value specified, defaulting to '120s'", String.valueOf(i));
				nfsc.setNukeDelay(120);
			}
			//set filter string
			if(!sectionFilterString.equals("")) {
				for(String strings : sectionFilterString.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addFilterStringElement(element);
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted {}.filter.string nukex given, skipping one element: '{}'", String.valueOf(i), strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addFilterStringElement(element);
					} else {
                        logger.warn("improper formatted {}.filter.string given, skipping one element: '{}'", String.valueOf(i), strings);
					}
				}
			}
			//set enforce string
			if(!sectionEnforceString.equals("")) {
				for(String strings : sectionEnforceString.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addEnforceStringElement(element);
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted {}.enforce.string nukex given, skipping one element: '{}'", String.valueOf(i), strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addEnforceStringElement(element);
					} else {
                        logger.warn("improper formatted {}.enforce.string given, skipping one element: '{}'", String.valueOf(i), strings);
					}
				}
			}
			//set filter regex
			if(!sectionFilterRegex.equals("")) {
				for(String strings : sectionFilterRegex.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addFilterRegexElement(element);	
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted {}.filter.regex nukex given, skipping one element: '{}'", String.valueOf(i), strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addFilterRegexElement(element);
					} else {
                        logger.warn("improper formatted {}.filter.regex given, skipping one element: '{}'", String.valueOf(i), strings);
					}
				}
			}
			//set enforce regex
			if(!sectionEnforceRegex.equals("")) {
				for(String strings : sectionEnforceRegex.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addEnforceRegexElement(element);
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted {}.enforce.regex nukex given, skipping one element: '{}'", String.valueOf(i), strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addEnforceRegexElement(element);
					} else {
                        logger.warn("improper formatted {}.enforce.regex given, skipping one element: '{}'", String.valueOf(i), strings);
					}
				}
			}
			//set filter year
			if(!sectionFilterYear.equals("")) {
				for(String strings : sectionFilterYear.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addFilterYearElement(element);
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted {}.filter.year nukex given, skipping one element: '{}'", String.valueOf(i), strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addFilterYearElement(element);
					} else {
                        logger.warn("improper formatted {}.filter.year given, skipping one element: '{}'", String.valueOf(i), strings);
					}
				}
			}
			//set enforce year nukex
			try {
				nfsc.setEnforceYearNukex(Integer.parseInt(sectionEnforceYearNukex));
			} catch(NumberFormatException e) {
				nfsc.setEnforceYearNukex(3);
                logger.warn("improper formatted {}.enforce.year.nukex given, defaulting nukex to 3", String.valueOf(i));
			}
			//set enforce year
			if(!sectionEnforceYear.equals("")) {
				for(String strings : sectionEnforceYear.split(";")) {
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					element.setElement(strings);
					nfsc.addEnforceYearElement(element);
				}
			}
			//set filter group
			if(!sectionFilterGroup.equals("")) {
				for(String strings : sectionFilterGroup.split(";")) {
					String[] elements = strings.split("~");
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					if(elements.length == 2) {
						try {
							element.setElement(elements[0]);
							element.setNukex(Integer.parseInt(elements[1]));
							nfsc.addFilterGroupElement(element);
						} catch(NumberFormatException e) {
                            logger.warn("improper formatted global.filter.group nukex given, skipping one element: '{}'", strings);
						}
					} else if(elements.length == 1) {
						element.setElement(elements[0]);
						element.setNukex(3);
						nfsc.addFilterGroupElement(element);
					} else {
                        logger.warn("improper formatted global.filter.group given, skipping one element: '{}'", strings);
					}
				}
			}
			//set enforce group nukex
			try {
				nfsc.setEnforceGroupNukex(Integer.parseInt(sectionEnforceGroupNukex));
			} catch(NumberFormatException e) {
				nfsc.setEnforceGroupNukex(3);
                logger.warn("improper formatted {}.enforce.group.nukex given, defaulting nukex to 3", String.valueOf(i));
			}
			//set enforce group
			if(!sectionEnforceGroup.equals("")) {
				for(String strings : sectionEnforceGroup.split(";")) {
					NukeFilterConfigElement element = new NukeFilterConfigElement();
					element.setElement(strings);
					nfsc.addEnforceGroupElement(element);
				}
			}
			//save section configuration to nfscMap
			nfscMap.put(sectionName.trim(), nfsc);
		}
	}
	
	public NukeFilterGlobalConfig getNukeFilterGlobalConfig() {
		return nfgc;
	}
	
	public NukeFilterSectionConfig getSectionConfig(String section) {
		return nfscMap.get(section);
	}
	
	public boolean hasSectionSpecificConfig(String section) {
		return nfscMap.get(section) != null;
	}
	
	public String getNuker() {
		return nfnc.getNuker();
	}
	
	public String[] getExemptDirectories() {
		return nfnc.getExemptsArray();
	}
	
}
