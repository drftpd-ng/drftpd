package org.drftpd.request.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.permissions.Permission;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class RequestSettings {

    private static final Logger logger = LogManager.getLogger(RequestSettings.class);

    private static RequestSettings ref;

    private String _requestPath;
    private boolean _createRequestPath;

    private String _requestFilledPrefix;
    private String _requestPrefix;
    private String _requestDateFormat;

    // Request weekly allotment
    private int _requestWeekMax;
    private Permission _requestWeekExempt;

    private boolean _RequestDecreaseWeekReqs;

    private List<Pattern> _requestDenyRegex;

    private RequestSettings() {
        // Set defaults (just in case)
        _requestDenyRegex = new ArrayList<>();

        reload();
    }

    public static synchronized RequestSettings getSettings() {
        if (ref == null) {
            // it's ok, we can call this constructor
            ref = new RequestSettings();
        }
        return ref;
    }

    /**
     * Reads 'config/plugins/request.conf'
     */
    public void reload() {
        logger.debug("Loading configuration");
        Properties p = ConfigLoader.loadPluginConfig("request.conf");

        _requestPath = p.getProperty("request.dirpath", "/REQUESTS/");
        _createRequestPath = Boolean.parseBoolean(p.getProperty("request.createpath", "false"));
        logger.debug("Request path set to {}. Path creation is {}", _requestPath, _createRequestPath ? "enabled" : "disabled");

        _requestPrefix = p.getProperty("request.prefix", "REQUEST-by.");
        _requestFilledPrefix = p.getProperty("reqfilled.prefix", "FILLED-for.");
        _requestDateFormat = p.getProperty("request.dateformat", "yyyy-MM-dd @ HH:mm");
        // Test the date format:
        try {
            new SimpleDateFormat(_requestDateFormat);
        } catch(Exception e) {
            logger.error("Request Date Format {} is invalid, reverting to default", _requestDateFormat, e);
            _requestDateFormat = "yyyy-MM-dd @ HH:mm";
        }
        logger.debug("Request prefix: {}, Filled prefix: {}, Date format: {}", _requestPrefix, _requestFilledPrefix, _requestDateFormat);

        _requestWeekMax = Integer.parseInt(p.getProperty("request.weekmax", "0"));
        _requestWeekExempt = new Permission(p.getProperty("request.weekexempt", ""));
        logger.debug("Request weekly max: {}, Exempt: {}", _requestWeekMax, _requestWeekExempt);

        _RequestDecreaseWeekReqs = Boolean.parseBoolean(p.getProperty("request.weekdecrease", "false"));

        int i = 1;
        String regex;
        List<Pattern> requestDenyRegex = new ArrayList<>();
        while ((regex = p.getProperty("request.deny." + i)) != null) {
            requestDenyRegex.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
            i++;
        }
        _requestDenyRegex = requestDenyRegex;
        logger.debug("Denied regular expressions in requests has been set to: {}", Arrays.toString(_requestDenyRegex.toArray()));
    }

    public String getRequestPath() {
        return _requestPath;
    }

    public boolean getCreateRequestPath() {
        return _createRequestPath;
    }

    public String getRequestPrefix() {
        return _requestPrefix;
    }

    public String getRequestFilledPrefix() {
        return _requestFilledPrefix;
    }

    public String getRequestDateFormat() {
        return _requestDateFormat;
    }

    public List<Pattern> getRequestDenyRegex() {
        return _requestDenyRegex;
    }

    public int getRequestWeekMax() {
        return _requestWeekMax;
    }

    public Permission getRequestWeekExempt() {
        return _requestWeekExempt;
    }

    public boolean getRequestDecreaseWeekReqs() {
        return _RequestDecreaseWeekReqs;
    }
}
