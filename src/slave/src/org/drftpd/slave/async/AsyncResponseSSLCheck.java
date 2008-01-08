package org.drftpd.slave.async;

public class AsyncResponseSSLCheck extends AsyncResponse {
	private boolean _sslCheck = false;
	
	public AsyncResponseSSLCheck(String index, boolean sslCheck) {
		super(index);
		_sslCheck = sslCheck;	
	}
	
	public boolean isSSLReady() {
		return _sslCheck;
	}
	
	public String toString() {
		return getClass().getName() + "[isSSLReady=" + isSSLReady() + "]";
	}
}
