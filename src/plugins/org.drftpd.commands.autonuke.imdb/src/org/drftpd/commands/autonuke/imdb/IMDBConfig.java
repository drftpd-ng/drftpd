package org.drftpd.commands.autonuke.imdb;

import org.drftpd.commands.autonuke.Config;
import org.drftpd.commands.autonuke.ConfigData;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.PropertyHelper;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.apache.log4j.Logger;

import java.util.Properties;
import java.io.FileNotFoundException;

/**
 * @author scitz0
 */
public class IMDBConfig extends Config {
	private static final Logger logger = Logger.getLogger(IMDBConfig.class);
	String _field, _operator, _value;

	public IMDBConfig(int i, Properties p) {
		super(i, p);
		_field = PropertyHelper.getProperty(p, i + ".imdb.field", "");
		_operator = PropertyHelper.getProperty(p, i + ".imdb.operator", "");
		_value = PropertyHelper.getProperty(p, i + ".imdb.value", "");
	}

	/**
	 * Check imdb metadata if available
	 * @param 	configData	Object holding return data
	 * @param 	dir 		Directory currently being handled
	 * @return				Return false if dir should be nuked, else true
	 */
	public boolean process(ConfigData configData, DirectoryHandle dir) {
		try {
			IMDBInfo imdbInfo = dir.getPluginMetaData(IMDBInfo.IMDBINFO);
			if (_field.equalsIgnoreCase("Title")) {
				return _operator.equals("!") == imdbInfo.getTitle().matches(_value);
			} else if (_field.equalsIgnoreCase("Year")) {
				return !handleDigitComparison(imdbInfo.getYear());
			} else if (_field.equalsIgnoreCase("Director")) {
				return _operator.equals("!") == imdbInfo.getDirector().matches(_value);
			} else if (_field.equalsIgnoreCase("Genre")) {
				return _operator.equals("!") == imdbInfo.getGenre().matches(_value);
			} else if (_field.equalsIgnoreCase("Plot")) {
				return _operator.equals("!") == imdbInfo.getPlot().matches(_value);
			} else if (_field.equalsIgnoreCase("Votes")) {
				return !handleDigitComparison(imdbInfo.getVotes());
			} else if (_field.equalsIgnoreCase("Rating")) {
				return !handleDigitComparison(imdbInfo.getRating());
			} else if (_field.equalsIgnoreCase("Screens")) {
				return !handleDigitComparison(imdbInfo.getScreens());
			} else if (_field.equalsIgnoreCase("Limited")) {
				return _operator.equals("!") == imdbInfo.getLimited().matches(_value);
			}
        } catch (KeyNotFoundException e1) {
			// No IMDB info found, return true
		} catch (FileNotFoundException e2) {
            // Hmm...
        }
		return true;
	}

	/**
	 * Handle comparison of fields containing digits
	 * @param 	meta		Metadata for the field specified
	 * @return				Return true if mathematical equation are correct, else false
	 */
	private boolean handleDigitComparison(Integer meta) {
		if (meta != null) {
			int conf_value = Integer.valueOf(_value.replaceAll("\\D",""));
			if (_operator.equals("<")) {
				return meta < conf_value;
			} else if (_operator.equals("=")) {
				return meta == conf_value;
			} else if (_operator.equals("!=")) {
				return meta != conf_value;
			} else if (_operator.equals(">")) {
				return meta > conf_value;
			}
		}
		return false;
	}

}
