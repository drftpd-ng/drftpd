/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mog
 * @version $Id$
 */
public class ReplacerUtils {

    private static final Logger logger = LogManager.getLogger(ReplacerUtils.class);

    private static final Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9@\\.,-]+)\\}");

    private ReplacerUtils() {
        super();
    }

    public static String jprintf(String template, Map<String, Object> env) {

        logger.debug("Running jprintf on template: {}", template);

        // Create empty map to hold our replacement variables
        Map<String, Object> envVars = new HashMap<>();

        // Compile the pattern provided with template and get the variables based on pattern
        Matcher matcher = pattern.matcher(template);
        String[] variables = matcher.results().map(m -> m.group(1)).toArray(String[]::new);

        logger.debug("We found {} variables", variables.length);

        // Take care of all options
        for (String variable : variables) {
            logger.debug("Handling variable {}", variable);

            // The value we end up with
            String currentValue;

            // Check if there is padding required
            if (variable.indexOf(',') != -1) {
                String[] varSplitter = variable.split(",");
                // We only allow 1 comma in a variable description
                if (varSplitter.length != 2) {
                    logger.warn("Variable format anomaly detected for variable {} in pattern {}", variable, pattern);
                    continue;
                }
                String varName = varSplitter[0];
                String options = varSplitter[1];

                Object currentData = env.get(varName);
                currentValue = currentData != null ? currentData.toString() : "[Unknown]";

                boolean alignLeft = false;
                if (options.charAt(0) == '-') {
                    alignLeft = true;
                    options = options.substring(1);
                }
                int fieldSize;
                int maxSize = -1;
                int valueSize = currentValue.length();
                if (options.indexOf('.') != -1) {
                    String[] optionsSplit = options.split("\\.");
                    if (optionsSplit.length != 2) {
                        logger.warn("Variable format anomaly detected for variable {} in pattern {}", variable, pattern);
                        continue;
                    }
                    fieldSize = Integer.parseInt(optionsSplit[0]);
                    maxSize = Integer.parseInt(optionsSplit[1]);
                } else {
                    fieldSize = Integer.parseInt(options);
                }
                logger.debug("valueSize: {}, fieldSize: {}, maxSize: {}", valueSize, fieldSize, maxSize);

                // Deal with maxSize
                if (maxSize != -1 && valueSize > maxSize) {
                    logger.warn("Value {} has a bigger size than the variable {} should hold (value size: {}, variable max size: {}. We are cutting of the end to make it fit", currentValue, variable, valueSize, maxSize);
                    currentValue = currentValue.substring(0, maxSize);
                } else {
                    // Only pad if we need to
                    if (fieldSize > valueSize) {
                        // Apply padding
                        if (alignLeft) {
                            currentValue = StringUtils.rightPad(currentValue, fieldSize);
                        } else {
                            currentValue = StringUtils.leftPad(currentValue, fieldSize);
                        }
                    }
                }
            } else {
                // No padding, just replace variable name with actual value
                Object currentData = env.get(variable);
                currentValue = currentData != null ? currentData.toString() : "[Unknown]";
            }
            logger.debug("Inserting variable {} with value {} into map", variable, currentValue);
            envVars.put(variable, currentValue);
        }

        StringSubstitutor sub = new StringSubstitutor(envVars);
        String finalResult = sub.replace(template);
        logger.debug("Final result for template [{}] is [{}]", template, finalResult);
        return finalResult;
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
