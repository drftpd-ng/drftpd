package org.drftpd.find.master;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.vfs.DirectoryHandle;

public class FindSettings {

    private boolean _quiet;

    private DupeUtils.KeepPolicy _dupe;
    private final String _dupeExclude;
    private final String _dupeMinAge;

    private int _limit;
    private int _maxLimit;

    private DirectoryHandle _dirHandle;

    public FindSettings(CommandRequest request) {

        _limit = Integer.parseInt(request.getProperties().getProperty("limit.default", "5"));
        _maxLimit = Integer.parseInt(request.getProperties().getProperty("limit.max", "20"));

        _quiet = false;

        _dupe = null;
        _dupeExclude = request.getProperties().getProperty("dupe.exclude");
        _dupeMinAge = request.getProperties().getProperty("dupe.min.age");

        // We by default initialize to root!
        _dirHandle = GlobalContext.getGlobalContext().getRoot();
    }

    public boolean getQuiet() {
        return _quiet;
    }

    public void setQuiet(boolean quiet) {
        _quiet = quiet;
    }

    /**
     * @return the keep policy when the -dupe option is used, null otherwise
     */
    public DupeUtils.KeepPolicy getDupe() {
        return _dupe;
    }

    public void setDupe(DupeUtils.KeepPolicy dupe) {
        _dupe = dupe;
    }

    public String getDupeExclude() {
        return _dupeExclude;
    }

    /**
     * @return the configured dupe.min.age in minutes, null when not configured
     */
    public String getDupeMinAge() {
        return _dupeMinAge;
    }

    public int getLimit() {
        return _limit;
    }

    public void setLimit(int limit) {
        _limit = limit;
    }

    public int getMaxLimit() {
        return _maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        _maxLimit = maxLimit;
    }

    public DirectoryHandle getDirectoryHandle() {
        return _dirHandle;
    }

    public void setDirectoryHandle(DirectoryHandle dirHandle) {
        _dirHandle = dirHandle;
    }
}
