/*
 * Created on 2003-sep-29
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd;


/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Nukee implements Comparable {
	private String _username;
	private long _amount;

	public long getBytes() {
		return _amount;
	}

	public int compareTo(Object o) {
		return compareTo((Nukee) o);
	}

	public int compareTo(Nukee o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();
		return (
			thisVal < anotherVal ? 1 : (thisVal == anotherVal ? 0 : -1));
	}

	public Nukee(String user, long l) {
		_username = user;
		_amount = l;
	}

	public String getUsername() {
		return _username;
	}

	public long getAmount() {
		return _amount;
	}

}
