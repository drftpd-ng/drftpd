package org.drftpd.plugins.sitebot.plugins.sysop.event;

public class SysopEvent {
	
	private String _username;
	private String _message;
	private String _response;
	private boolean _login;
	private boolean _successful;

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
