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
package org.drftpd.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import java.util.ResourceBundle;

/**
 * @author mog
 * @version $Id$
 */
public class ReplacerUtils {
	
	private static final Logger logger = LogManager.getLogger(ReplacerUtils.class);
	
	private ReplacerUtils() {
		super();
	}
	
	public static ReplacerFormat finalFormat(ResourceBundle bundle, String key)
			throws FormatterException {

		return ReplacerFormat.createFormat(bundle.getString(key));
	}

	public static String jprintf(String key, ReplacerEnvironment env,
			ResourceBundle bundle) {
		try {
			return SimplePrintf.jprintf(finalFormat(bundle, key), env);
		} catch (Exception e) {
            logger.info("Error formatting message for key - {}", key, e);
			return key;
		}
	}
}
