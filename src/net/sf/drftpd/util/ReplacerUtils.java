package net.sf.drftpd.util;

import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

/**
 * @author mog
 * @version $Id: ReplacerUtils.java,v 1.1 2004/01/20 06:59:01 mog Exp $
 */
public class ReplacerUtils {
	private static final Logger logger = Logger.getLogger(ReplacerUtils.class);

	public static ReplacerFormat finalFormat(Class baseName, String key)
		throws FormatterException {
		return finalFormat(baseName.getName(), key);
	}

	public static ReplacerFormat finalFormat(String baseName, String key)
		throws FormatterException {
		ResourceBundle bundle = ResourceBundle.getBundle(baseName);
		String str = bundle.getString(key);
		return ReplacerFormat.createFormat(str);
	}

	public static String finalJprintf(
		ReplacerFormat str,
		ReplacerEnvironment env) throws FormatterException {
		return SimplePrintf.jprintf(str, env);
	}

	public static String jprintf(
		String key,
		ReplacerEnvironment env,
		Class baseName) {
		return jprintf(key, env, baseName.getName());
	}

	public static String jprintf(
		String key,
		ReplacerEnvironment env,
		String baseName) {
		ReplacerFormat str;
		try {
			str = finalFormat(baseName, key);
			return finalJprintf(str, env);
		} catch (FormatterException e) {
			logger.warn("", e);
			return key;
		}
	}

	private ReplacerUtils() {
		super();
	}

}
