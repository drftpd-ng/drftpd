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
package org.drftpd.master.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mog
 * @version $Id$
 */
public class ReplacerUtils {

    private static final Logger logger = LogManager.getLogger(ReplacerUtils.class);
    private static final Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9\\.,-]+)\\}");

    private ReplacerUtils() {
        super();
    }

    public static String jprintf(String template, Map<String, Object> env) {
        Map<String, Object> envVars = new HashMap<>(env);
        Matcher matcher = pattern.matcher(template);
        String[] variables = matcher.results().map(m -> m.group(1)).toArray(String[]::new);
        // Take care of all options
        for (String variable : variables) {
            if (variable.contains(",")) {
                String[] varSplitter = variable.split(",");
                String varName = varSplitter[0];
                String currentValue = envVars.get(varName).toString();
                String options = varSplitter.length == 2 ? varSplitter[1] : null;
                if (options != null) {
                    boolean alignLeft = options.startsWith("-");
                    options = options.replace("-", "");
                    String[] sizeAndMaxOptions = options.split("\\.");
                    int size = Integer.parseInt(sizeAndMaxOptions[0]);
                    Integer maxSize = sizeAndMaxOptions.length == 2 ? Integer.parseInt(sizeAndMaxOptions[1]) : null;
                    // Adapt from the option
                    int paddingSize = size - currentValue.length();
                    if (alignLeft) {
                        currentValue = StringUtils.rightPad(currentValue, paddingSize);
                    } else {
                        currentValue = StringUtils.leftPad(currentValue, paddingSize);
                    }
                    if (maxSize != null && currentValue.length() > maxSize) {
                        currentValue = alignLeft ? currentValue.substring(0, maxSize) :
                                currentValue.substring(currentValue.length() - maxSize);
                    }
                }
                envVars.put(variable, currentValue);
            }
        }
        StringSubstitutor sub = new StringSubstitutor(envVars);
        return sub.replace(template);
    }

    public static String jprintf(String key, Map<String, Object> env, ResourceBundle bundle) {
        try {
            String template = bundle.getString(key);
            return jprintf(template, env);
        } catch (Exception e) {
            logger.info("Error formatting message for key - {}", key, e);
            return key;
        }
    }
}
