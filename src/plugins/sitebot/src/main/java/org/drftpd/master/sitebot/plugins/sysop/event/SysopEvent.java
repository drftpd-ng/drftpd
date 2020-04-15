package org.drftpd.master.sitebot.plugins.sysop.event;

public class SysopEvent {

    private final String _username;
    private final String _message;
    private final String _response;
    private final boolean _login;
    private final boolean _successful;

    public SysopEvent(String username, String message, String response, boolean login, boolean successful) {
        _username = username;
        _message = message;
        _response = response;
        _login = login;
        _successful = successful;
    }

    public String getUsername() {
        return _username;
    }

    public String getMessage() {
        return _message;
    }

    public String getResponse() {
        return _response;
    }

    public boolean isLogin() {
        return _login;
    }

    public boolean isSuccessful() {
        return _successful;
    }
}
