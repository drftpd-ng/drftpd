package org.drftpd.commands.autonuke;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.autonuke.event.AutoNukeEvent;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.conf.DatedSection;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author scitz0
 */
public abstract class Config {
	private static final Logger logger = LogManager.getLogger(Config.class);
	private int _nuke_mult;
	private long _min_age, _max_age, _nuke_delay;
	private String _irc, _nuke_reason, _sub_directories, _dated_format;
	private ArrayList<SectionInterface> _sections;
	private HashMap<String, Integer> _dated;
	private boolean _debug;

	public Config(int i, Properties p) {
		// Sections
		if (_sections == null) {
        	_sections  = new ArrayList<>();
		} else {
			_sections.clear();
		}
		if (PropertyHelper.getProperty(p, i + ".section", "*").equals("*")) {
			for (SectionInterface si : GlobalContext.getGlobalContext().getSectionManager().getSections()) {
				// Don't add excluded
				if (!AutoNukeSettings.getSettings().getExcludedSections().contains(si)) _sections.add(si);
			}
		} else {
			for (String sec : PropertyHelper.getProperty(p, i + ".section").split(",")) {
				SectionInterface si = GlobalContext.getGlobalContext().getSectionManager().getSection(sec);
				if (!si.getName().equals("")) {
					// Don't add excluded, they shouldn't be defining it anyway
					if (!AutoNukeSettings.getSettings().getExcludedSections().contains(si)) _sections.add(si);
				}
			}
		}

		// Dated sections
		if (_dated == null) {
        	_dated  = new HashMap<>();
		} else {
			_dated.clear();
		}
		String getDated = PropertyHelper.getProperty(p, i + ".dated", "");
		if (!getDated.equals("")) {
			for (String datedItem : getDated.split(",")) {
				String[] setting = datedItem.split(":");
				_dated.put(setting[0], Integer.parseInt(setting[1]));
			}
		}

		// Other
		_irc = PropertyHelper.getProperty(p, i + ".irc", "");
		_nuke_reason = PropertyHelper.getProperty(p, i + ".nuke.reason", "autonuke");
		_sub_directories = PropertyHelper.getProperty(p, i + ".subdirs", "");
		_dated_format = PropertyHelper.getProperty(p, i + ".dated.format", "MMdd");
		_min_age = Long.parseLong(PropertyHelper.getProperty(p, i + ".minage", "120")) * 60000;
		_max_age = Long.parseLong(PropertyHelper.getProperty(p, i + ".maxage", "20160")) * 60000;
		_nuke_delay = Long.parseLong(PropertyHelper.getProperty(p, i + ".nuke.delay", "720")) * 60000;
		_nuke_mult = Integer.parseInt(PropertyHelper.getProperty(p, i + ".nuke.mult", "1"));
		_debug= PropertyHelper.getProperty(p, i + ".debug", "false").equalsIgnoreCase("true");
	}

	/**
	 * Method to handle the current directory/subdirectory being scanned.
	 * @param 	configData 	data to be returned
	 * @param 	dir		 	Directory currently being handled
	 * @return				true if dir has been processed by this config, else false
	 */
	public boolean handleDirectory(ConfigData configData, DirectoryHandle dir) {
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
		if (!_sections.contains(section)) {
			return true;
		}
		if (section instanceof DatedSection) {
			if (_dated.containsKey(section.getName())) {
				int days_back = _dated.get(section.getName());
				if (days_back > 0) {
					SimpleDateFormat smf = new SimpleDateFormat(_dated_format);
					Date date = new Date();
					boolean datedDirValid = false;
					for (int i = days_back; i > 0; i--) {
						if (dir.getParent().getName().equals(smf.format(date))) {
							// Dated dir valid, set true and break
							datedDirValid = true;
						}
						date.setTime(date.getTime() - 86400000);
					}
					if (!datedDirValid) {
						// Dated day not valid, skip check
						return true;
					}
				}
			}
		}
		try {
			if (System.currentTimeMillis() > dir.lastModified()+_max_age) {
				return true;
			}
			if (System.currentTimeMillis() > dir.lastModified()+_min_age) {
				// Dir is old enough to be nuked if it matches a NukeConfig
				// Lets check the dir
				checkDirectory(configData, dir);
				if (configData.getNukeItem() != null) {
					// Add nuke if not in queue already
					// Dont announce if dir has passed nuke delay, will get nuked directly anyway
					if (DirsToNuke.getDirsToNuke().add(configData.getNukeItem()) && (System.currentTimeMillis() < configData.getNukeItem().getTime())) {
						GlobalContext.getEventService().publishAsync(new AutoNukeEvent(configData.getNukeItem(), _irc, configData.getReturnData()));
					}
				}
				return true;
			}
		} catch (FileNotFoundException e) {
			logger.warn("",e);
			return true;
		}
		return false;
	}

	public void checkDirectory(ConfigData configData, DirectoryHandle dir) {
		handleDirectory(configData, dir, false);
		if (configData.getNukeItem() == null) {
			// Dir ok, check subdirs
			try {
				for (DirectoryHandle subDir : dir.getDirectoriesUnchecked()) {
					if (subDir.getName().matches(_sub_directories)) {
						handleDirectory(configData, subDir, true);
						if (configData.getNukeItem() != null) {
							// Break and nuke dir!
							break;
						}
					}
				}
			} catch (FileNotFoundException e) {
				logger.warn("",e);
			}
		}
	}

	/**
	 * Method to handle the current directory/subdirectory being scanned.
	 * @param 	configData 	data to be returned
	 * @param 	dir 		Directory currently being handled
	 * @param 	isSubdir 	Is this a subdir?
	 */
	public void handleDirectory(ConfigData configData, DirectoryHandle dir, boolean isSubdir) {
		if (!process(configData, dir)) {
			NukeItem ni;
			try {
				ni = new NukeItem(dir.lastModified()+_nuke_delay, dir, _nuke_reason, _nuke_mult, isSubdir, _debug);
			} catch (FileNotFoundException e) {
				logger.warn("",e);
				return;
			}
			configData.setNukeItem(ni);
		}
	}

	public abstract boolean process(ConfigData configData, DirectoryHandle dir);

}
