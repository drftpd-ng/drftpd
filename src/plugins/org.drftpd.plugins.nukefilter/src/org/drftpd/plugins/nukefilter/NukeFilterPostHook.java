package org.drftpd.plugins.nukefilter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.plugins.nukefilter.event.NukeFilterEvent;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author phew
 */
public class NukeFilterPostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(NukeFilterPostHook.class);
	
	private NukeFilterSettings _nfs;

	@Override
	public void initialize(StandardCommandManager cManager) {
		_nfs = NukeFilterManager.getNukeFilterManager().getNukeFilterSettings();
	}
	
	public void doMKDPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 257) {
			//MKD failed, skip filter
			return;
		}
		//get the handle for the directory created
		DirectoryHandle newDir;
		try {
			newDir = request.getCurrentDirectory().getDirectoryUnchecked(request.getArgument());
		} catch (FileNotFoundException e) {
            logger.error("Failed getting directory handle for {}", request.getArgument(), e);
			return;
		} catch (ObjectNotValidException e) {
            logger.error("Failed getting directory handle for {}", request.getArgument(), e);
			return;
		}
		//is directory name exempt?
		if(isExemptDirectoryName(newDir.getName(), _nfs.getExemptDirectories()))
			return;
		
		//do checks
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(newDir);
		String sectionName = section.getName();
		//always perform global check
		boolean getsNuked = doGlobalCheck(newDir);
		//only perform section check when section config available and item is not going to be nuked yet
		if(_nfs.hasSectionSpecificConfig(sectionName) && !getsNuked) {
			doSectionCheck(newDir, sectionName);
		}
	}
	
	/**
	 * Checks if directory name is exempt from NukeFilter checks.
	 * @param dirName name of the directory
	 * @param exemptRegex string to match against
	 * @return <b>TRUE</b> if dirName is exempt<br>
	 * 		   <b>FALSE</b> if dirName is NOT an exempt
	 */
	private boolean isExemptDirectoryName(String dirName, String[] exemptRegex) {
        for (String anExemptRegex : exemptRegex) {
            if (Pattern.matches(anExemptRegex, dirName))
                return true;
        }
		return false;
	}
	
	/**
	 * This method performs the global checks.
	 * @param dir DirectoryHandle to the dir to be checked
	 * @return <b>TRUE</b> if dir failed the checks (gets nuked)
	 * 		   <b>FALSE</b> if dir passed the checks (doesn't get nuked)	
	 */
	private boolean doGlobalCheck(DirectoryHandle dir) {
		//return if global filters are disabled
		if(!_nfs.getNukeFilterGlobalConfig().isEnabled()) return false;
		//check if section is exempt
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		ArrayList<SectionInterface> exempts = _nfs.getNukeFilterGlobalConfig().getExemptSections();
		for (SectionInterface exempt : exempts) {
			if (exempt.getName().equals(section.getName()))
				return false;
		}
		if(doFilterStringCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getFilterStringList(), "global"))
			return true;
		if(doEnforceStringCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getEnforceStringList(), "global"))
			return true;
		if(doFilterRegexCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getFilterRegexList(), "global"))
			return true;
		if(doEnforceRegexCheck(dir,
				_nfs.getNukeFilterGlobalConfig().getEnforceRegexList(), "global"))
			return true;
		if(doFilterYearCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getFilterYearList(), "global"))
			return true;
		if(doEnforceYearCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getEnforceYearList(), "global"))
			return true;
		if(doFilterGroupCheck(dir, 
				_nfs.getNukeFilterGlobalConfig().getFilterGroupList(), "global"))
			return true;
        return doEnforceGroupCheck(dir,
                _nfs.getNukeFilterGlobalConfig().getEnforceGroupList(), "global");

    }
	
	/**
	 * This method performs the section specific checks.
	 * @param dir DirectoryHandle to be processed
	 * @param sectionName dir's parent section
	 * @return <b>TRUE</b> if dir failed the checks (gets nuked)
	 * 		   <b>FALSE</b> if dir passed the checks (doesn't get nuked)
	 */
	private boolean doSectionCheck(DirectoryHandle dir, String sectionName) {
		if(doFilterStringCheck(dir, 
				_nfs.getSectionConfig(sectionName).getFilterStringList(), "section"))
			return true;
		if(doEnforceStringCheck(dir, 
				_nfs.getSectionConfig(sectionName).getEnforceStringList(), "section"))
			return true;
		if(doFilterRegexCheck(dir,
				_nfs.getSectionConfig(sectionName).getFilterRegexList(), "section"))
			return true;
		if(doEnforceRegexCheck(dir, 
				_nfs.getSectionConfig(sectionName).getEnforceRegexList(), "section"))
			return true;
		if(doFilterYearCheck(dir, 
				_nfs.getSectionConfig(sectionName).getFilterYearList(), "section"))
			return true;
		if(doEnforceYearCheck(dir, 
				_nfs.getSectionConfig(sectionName).getEnforceYearList(), "section"))
			return true;
		if(doFilterGroupCheck(dir, 
				_nfs.getSectionConfig(sectionName).getFilterGroupList(), "section"))
			return true;
        return doEnforceGroupCheck(dir,
                _nfs.getSectionConfig(sectionName).getEnforceGroupList(), "section");
    }
	
	/**
	 * This method checks the filter string settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param filterStringList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the filter<br>
	 * 		   <b>FALSE</b> if dir passed the filter
	 */
	private boolean doFilterStringCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> filterStringList, String type) {
		for (NukeFilterConfigElement e : filterStringList) {
			if (dir.getName().toLowerCase().contains(e.getElement().toLowerCase())) {
				if (type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir,
							"directory.contains.global.banned.string", e.getElement(),
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
							e.getNukex()), "global.filter.string.announce");
				} else if (type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir,
							"directory.contains.section.banned.string", e.getElement(),
							_nfs.getSectionConfig(section.getName()).getNukeDelay(),
							e.getNukex()), "section.filter.string.announce");
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks the enforce string settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param enforceStringList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the enforcement<br>
	 * 		   <b>FALSE</b> if dir passed the enforcement
	 */
	private boolean doEnforceStringCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> enforceStringList, String type) {
		for (NukeFilterConfigElement e : enforceStringList) {
			if (!dir.getName().toLowerCase().contains(e.getElement().toLowerCase())) {
				if (type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir,
							"directory.is.missing.global.enforced.string", e.getElement(),
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
							e.getNukex()), "global.enforce.string.announce");
				} else if (type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir,
							"directory.is.missing.section.enforced.string", e.getElement(),
							_nfs.getSectionConfig(section.getName()).getNukeDelay(),
							e.getNukex()), "section.enforce.string.announce");
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks the filter regex settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param filterRegexList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the filter<br>
	 * 		   <b>FALSE</b> if dir passed the filter
	 */
	private boolean doFilterRegexCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> filterRegexList, String type) {
		for (NukeFilterConfigElement e : filterRegexList) {
			Pattern pattern = Pattern.compile(e.getElement());
			Matcher matcher = pattern.matcher(dir.getName());
			if (matcher.matches()) {
				if (type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir,
							"global.filter.regex.matched", e.getElement(),
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
							e.getNukex()), "global.filter.regex.announce");
				} else if (type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir,
							"section.filter.regex.matched", e.getElement(),
							_nfs.getSectionConfig(section.getName()).getNukeDelay(),
							e.getNukex()), "section.filter.regex.announce");
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks the enforce regex settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param enforceRegexList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the enforcement<br>
	 * 		   <b>FALSE</b> if dir passed the enforcement     
	 */
	private boolean doEnforceRegexCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> enforceRegexList, String type) {
		for (NukeFilterConfigElement e : enforceRegexList) {
			Pattern pattern = Pattern.compile(e.getElement());
			Matcher matcher = pattern.matcher(dir.getName());
			if (!matcher.matches()) {
				if (type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir,
							"global.enforce.regex.did.not.match", e.getElement(),
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
							e.getNukex()), "global.enforce.regex.announce");
				} else if (type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir,
							"section.enforce.regex.did.not.match", e.getElement(),
							_nfs.getSectionConfig(section.getName()).getNukeDelay(),
							e.getNukex()), "section.enforce.regex.announce");
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks the filter year settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param filterYearList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the filter<br>
	 * 		   <b>FALSE</b> if dir passed the filter
	 */
	private boolean doFilterYearCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> filterYearList, String type) {
		Pattern patternYear = Pattern.compile("^.+[-.]([0-9]{4})[-.].+$");
		Matcher matcherYear = patternYear.matcher(dir.getName());
		if(matcherYear.matches()) {
			try {
				int rlsYear = Integer.parseInt(matcherYear.group(1));
				for (NukeFilterConfigElement e : filterYearList) {
					if (e.getElement().contains("-")) {
						String[] range = e.getElement().split("-");
						if (range.length != 2) {
                            logger.warn("improper formatted global.filter.year range element given, skipping '{}'", e.getElement());
							continue;
						}
						int start;
						int stop;
						try {
							start = Integer.parseInt(range[0]);
							stop = Integer.parseInt(range[1]);
						} catch (NumberFormatException er) {
                            logger.warn("improper formatted global.filter.year element given, skipping '{}'", e.getElement());
							continue;
						}
						if (stop < start) {
							int tmp = start;
							start = stop;
							stop = tmp;
						}
						if (rlsYear >= start && rlsYear <= stop) {
							//within range, nuke that bitch
							if (type.equals("global")) {
								nuke(new NukeFilterNukeItem(dir,
										"global.banned.year", e.getElement(),
										_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
										e.getNukex()), "global.filter.year.announce");
							} else if (type.equals("section")) {
								SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
								nuke(new NukeFilterNukeItem(dir,
										"section.banned.year", e.getElement(),
										_nfs.getSectionConfig(section.getName()).getNukeDelay(),
										e.getNukex()), "section.filter.year.announce");
							}
							return true;
						}
					} else {
						try {
							if (rlsYear == Integer.parseInt(e.getElement())) {
								//nuke that bitch
								if (type.equals("global")) {
									nuke(new NukeFilterNukeItem(dir,
											"global.banned.year", e.getElement(),
											_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
											e.getNukex()), "global.filter.year.announce");
								} else if (type.equals("section")) {
									SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
									nuke(new NukeFilterNukeItem(dir,
											"section.banned.year", e.getElement(),
											_nfs.getSectionConfig(section.getName()).getNukeDelay(),
											e.getNukex()), "section.filter.year.announce");
								}
								return true;
							}
						} catch (NumberFormatException er) {
                            logger.warn("improper formatted global.filter.year element given, skipping '{}'", e.getElement());
						}
					}
				}
			} catch(NumberFormatException e) {
                logger.warn("could not format the year string extracted from '{}', skipping global.filter.year (\\d\\d\\d\\d) on given release", dir.getName());
			}
		}
		Pattern patternYearx = Pattern.compile("^.+[-.]([0-9]{3}x)[-.].+$");
		Matcher matcherYearx = patternYearx.matcher(dir.getName());
		if(matcherYearx.matches()) {
			String rlsYear = matcherYearx.group(1).substring(0, 3);
			try {
				Integer.parseInt(rlsYear);
			} catch(NumberFormatException e) {
                logger.warn("could not format the year string extracted from '{}', skipping global.filter.year (\\d\\d\\dx) on given release", dir.getName());
				return false;
			}
			for (NukeFilterConfigElement e : filterYearList) {
				if (e.getElement().contains("-")) {
					String[] range = e.getElement().split("-");
					if (range.length != 2) {
                        logger.warn("improper formatted global.filter.year element given, skipping '{}'", e.getElement());
						continue;
					}
					int start;
					int stop;
					try {
						start = Integer.parseInt(range[0]);
						stop = Integer.parseInt(range[1]);
					} catch (NumberFormatException er) {
                        logger.warn("improper formatted global.filter.year element given, skipping '{}'", e.getElement());
						continue;
					}
					if (stop < start) {
						int tmp = start;
						start = stop;
						stop = tmp;
					}
					for (int i = 0; i < 10; i++) {
						int year = Integer.parseInt(rlsYear + String.valueOf(i));
						if (year >= start && year <= stop) {
							if (type.equals("global")) {
								nuke(new NukeFilterNukeItem(dir,
										"global.banned.year", e.getElement(),
										_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
										e.getNukex()), "global.filter.year.announce");
							} else if (type.equals("section")) {
								SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
								nuke(new NukeFilterNukeItem(dir,
										"section.banned.year", e.getElement(),
										_nfs.getSectionConfig(section.getName()).getNukeDelay(),
										e.getNukex()), "section.filter.year.announce");
							}
							return true;
						}
					}
				} else {
					try {
						for (int i = 0; i < 10; i++) {
							int year = Integer.parseInt(rlsYear + String.valueOf(i));
							if (year == Integer.parseInt(e.getElement())) {
								if (type.equals("global")) {
									nuke(new NukeFilterNukeItem(dir,
											"global.banned.year", e.getElement(),
											_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
											e.getNukex()), "global.filter.year.announce");
								} else if (type.equals("section")) {
									SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
									nuke(new NukeFilterNukeItem(dir,
											"section.banned.year", e.getElement(),
											_nfs.getSectionConfig(section.getName()).getNukeDelay(),
											e.getNukex()), "section.filter.year.announce");
								}
								return true;
							}
						}
					} catch (NumberFormatException er) {
                        logger.warn("improper formatted global.filter.year element given, skipping '{}'", e.getElement());
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * This method checks the enforce year settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param enforceYearList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the enforcement<br>
	 * 		   <b>FALSE</b> if dir passed the enforcement
	 */
	private boolean doEnforceYearCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> enforceYearList, String type) {
		if(enforceYearList.isEmpty()) return false;
		boolean nuke = true;
		Pattern patternYear = Pattern.compile("^.+[-.]([0-9]{4})[-.].+$");
		Matcher matcherYear = patternYear.matcher(dir.getName());
		if(matcherYear.matches()) {
			try {
				int rlsYear = Integer.parseInt(matcherYear.group(1));
				Iterator<NukeFilterConfigElement> fyIter = enforceYearList.iterator();
				while(fyIter.hasNext() && nuke) {
					NukeFilterConfigElement e = fyIter.next();
					if(e.getElement().contains("-")) {
						String[] range = e.getElement().split("-");
						if(range.length != 2) {
                            logger.warn("improper formatted global.enforce.year/section.enforce.year range element given, skipping '{}'", e.getElement());
							continue;
						}
						int start;
						int stop;
						try {
							start = Integer.parseInt(range[0]);
							stop = Integer.parseInt(range[1]);
						} catch(NumberFormatException er) {
                            logger.warn("improper formatted global.enforce.year/section.enforce.year element given, skipping '{}'", e.getElement());
							continue;
						}
						if(stop < start) {
							int tmp = start;
							start = stop;
							stop = tmp;
						}
						if(rlsYear >= start && rlsYear <= stop) {
							nuke = false;
							break;
						}
					} else {
						try {
							if(rlsYear == Integer.parseInt(e.getElement())) {
								nuke = false;
								break;
							}
						} catch(NumberFormatException er) {
                            logger.warn("improper formatted global.enforce.year/section.enforce.year element given, skipping '{}'", e.getElement());
						}
					}
				}
				if(nuke) {
					if(type.equals("global")) {
						nuke(new NukeFilterNukeItem(dir, "failing.enforced.year.list", matcherYear.group(1), 
								_nfs.getNukeFilterGlobalConfig().getNukeDelay(), 
								_nfs.getNukeFilterGlobalConfig().getEnforceYearNukex()), 
								"global.enforce.year.announce");
					} else if(type.equals("section")) {
						SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
						nuke(new NukeFilterNukeItem(dir, "failing.enforced.year.list", matcherYear.group(1), 
								_nfs.getSectionConfig(section.getName()).getNukeDelay(), 
								_nfs.getSectionConfig(section.getName()).getEnforceYearNukex()), 
								"section.enforce.year.announce");
					}
				}
			} catch(NumberFormatException e) {
                logger.warn("could not format the year string extracted from '{}', skipping global.enforce.year/section.enforce.year (\\d\\d\\d\\d) on given release", dir.getName());
				return false;
			}
		}
		Pattern patternYearx = Pattern.compile("^.+[-.]([0-9]{3}x)[-.].+$");
		Matcher matcherYearx = patternYearx.matcher(dir.getName());
		if(matcherYearx.matches()) {
			String rlsYear = matcherYearx.group(1).substring(0, 3);
			try {
				Integer.parseInt(rlsYear);
			} catch(NumberFormatException e) {
                logger.warn("could not format the year string extracted from '{}', skipping global.enforce.year/section.enforce.year (\\d\\d\\dx) on given release", dir.getName());
				return false;
			}
			Iterator<NukeFilterConfigElement> fyIter = enforceYearList.iterator();
			while(fyIter.hasNext() && nuke) {
				NukeFilterConfigElement e = fyIter.next();
				if(e.getElement().contains("-")) {
					String[] range = e.getElement().split("-");
					if(range.length != 2) {
                        logger.warn("improper formatted global.enforce.year/section.enforce.year element given, skipping '{}'", e.getElement());
						continue;
					}
					int start;
					int stop;
					try {
						start = Integer.parseInt(range[0]);
						stop = Integer.parseInt(range[1]);
					} catch(NumberFormatException er) {
                        logger.warn("improper formatted global.enforce.year/section.enforce.year element given, skipping '{}'", e.getElement());
						continue;
					}
					if(stop < start) {
						int tmp = start;
						start = stop;
						stop = tmp;
					}
					for(int i = 0; i < 10; i++) {
						int year = Integer.parseInt(rlsYear+String.valueOf(i));
						if(year >= start && year <= stop) {
							nuke = false;
							break;
						}
					}
				} else {
					try {
						for(int i = 0; i < 10; i++) {
							int year = Integer.parseInt(rlsYear+String.valueOf(i));
							if(year == Integer.parseInt(e.getElement())) {
								nuke = false;
								break;
							}
						}
					} catch(NumberFormatException er) {
                        logger.warn("improper formatted global.enforce.year/section.enforce.year element given, skipping '{}'", e.getElement());
					}
				}
			}
			if(nuke) {
				if(type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir, "failing.enforced.year.list", matcherYearx.group(1), 
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(), 
							_nfs.getNukeFilterGlobalConfig().getEnforceYearNukex()), 
							"global.enforce.year.announce");
				} else if(type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir, "failing.enforced.year.list", matcherYearx.group(1), 
							_nfs.getSectionConfig(section.getName()).getNukeDelay(), 
							_nfs.getSectionConfig(section.getName()).getEnforceYearNukex()), 
							"section.enforce.year.announce");
				}
			}
		}
		return nuke;
	}
	
	/**
	 * This method checks the filter group settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param filterGroupList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the filter<br>
	 * 		   <b>FALSE</b> if dir passed the filter
	 */
	private boolean doFilterGroupCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> filterGroupList, String type) {
		for (NukeFilterConfigElement e : filterGroupList) {
			if (dir.getName().toLowerCase().endsWith("-" + e.getElement().toLowerCase())) {
				if (type.equals("global")) {
					nuke(new NukeFilterNukeItem(dir, "global.banned.group", e.getElement(),
							_nfs.getNukeFilterGlobalConfig().getNukeDelay(),
							e.getNukex()), "global.filter.group.announce");
				} else if (type.equals("section")) {
					SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
					nuke(new NukeFilterNukeItem(dir,
							"section.banned.group", e.getElement(),
							_nfs.getSectionConfig(section.getName()).getNukeDelay(),
							e.getNukex()), "section.filter.group.announce");
				}
				return true;
			}
		}
		return false;
	}
	
	/**
	 * This method checks the enforce group settings against the given dir.
	 * @param dir directory handle to be examined
	 * @param enforceGroupList list of strings
	 * @param type global or section specific?
	 * @return <b>TRUE</b> if dir did not pass the enforcement<br>
	 * 		   <b>FALSE</b> if dir passed the enforcement
	 */
	private boolean doEnforceGroupCheck(DirectoryHandle dir, 
			ArrayList<NukeFilterConfigElement> enforceGroupList, String type) {
		if(enforceGroupList.isEmpty()) return false;
		boolean nuke = true;
		String grp = dir.getName(); 
		grp = grp.substring(grp.lastIndexOf('-')+1);
		for (NukeFilterConfigElement e : enforceGroupList) {
			if (dir.getName().toLowerCase().endsWith("-" + e.getElement().toLowerCase())) {
				nuke = false;
				break;
			}
		}
		if(nuke) {
			if(type.equals("global")) {
				nuke(new NukeFilterNukeItem(dir, "failing.enforced.group.list", grp, 
						_nfs.getNukeFilterGlobalConfig().getNukeDelay(), 
						_nfs.getNukeFilterGlobalConfig().getEnforceGroupNukex()), 
						"global.enforce.group.announce");
			} else if(type.equals("section")) {
				SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
				nuke(new NukeFilterNukeItem(dir, "failing.enforced.group.list", grp, 
						_nfs.getSectionConfig(section.getName()).getNukeDelay(), 
						_nfs.getSectionConfig(section.getName()).getEnforceGroupNukex()), 
						"section.enforce.group.announce");
			}
		}
		return nuke;
	}
	
	/**
	 * Nukes the specified NukeFilterNukeItem
	 * @param nfni item to be nuked
	 */
	private void nuke(NukeFilterNukeItem nfni, String ircString) {
		//start timer to nuke target in 'delay' seconds
		Timer timer = new Timer();
		NukeFilterNukeTask nfnt = new NukeFilterNukeTask(nfni);
		timer.schedule(nfnt, nfni.getDelay() * 1000);
		//create and unleash NukeFilterEvent
		NukeFilterEvent nfe = new NukeFilterEvent(nfni, ircString);
		GlobalContext.getEventService().publishAsync(nfe);
	}
	

}
