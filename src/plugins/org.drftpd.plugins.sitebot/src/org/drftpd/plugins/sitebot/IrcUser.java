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
package org.drftpd.plugins.sitebot;

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public class IrcUser implements Comparable<IrcUser> {

	private String _prefix;
	private String _nick;
	private String _lowerNick;

	/**
	 * Constructs a User object with a known prefix and nick.
	 *
	 * @param prefix The status of the user, for example, "@".
	 * @param nick The nick of the user.
	 */
	IrcUser(String prefix, String nick) {
		_prefix = prefix;
		_nick = nick;
		_lowerNick = nick.toLowerCase();
	}


	/**
	 * Returns the prefix of the user. If the User object has been obtained
	 * from a list of users in a channel, then this will reflect the user's
	 * status in that channel.
	 *
	 * @return The prefix of the user. If there is no prefix, then an empty
	 *         String is returned.
	 */
	public String getPrefix() {
		return _prefix;
	}


	/**
	 * Returns whether or not the user represented by this object is an
	 * operator. If the User object has been obtained from a list of users
	 * in a channel, then this will reflect the user's operator status in
	 * that channel.
	 * 
	 * @return true if the user is an operator in the channel.
	 */
	public boolean isOp() {
		return _prefix.indexOf('@') >= 0;
	}


	/**
	 * Returns whether or not the user represented by this object has
	 * voice. If the User object has been obtained from a list of users
	 * in a channel, then this will reflect the user's voice status in
	 * that channel.
	 * 
	 * @return true if the user has voice in the channel.
	 */
	public boolean hasVoice() {
		return _prefix.indexOf('+') >= 0;
	}        


	/**
	 * Returns the nick of the user.
	 * 
	 * @return The user's nick.
	 */
	public String getNick() {
		return _nick;
	}


	/**
	 * Returns the nick of the user complete with their prefix if they
	 * have one, e.g. "@Dave".
	 * 
	 * @return The user's prefix and nick.
	 */
	public String toString() {
		return this.getPrefix() + this.getNick();
	}


	/**
	 * Returns true if the nick represented by this User object is the same
	 * as the argument. A case insensitive comparison is made.
	 * 
	 * @return true if the nicks are identical (case insensitive).
	 */
	public boolean equals(String nick) {
		return nick.toLowerCase().equals(_lowerNick);
	}


	/**
	 * Returns true if the nick represented by this User object is the same
	 * as the nick of the User object given as an argument.
	 * A case insensitive comparison is made.
	 * 
	 * @return true if o is a User object with a matching lowercase nick.
	 */
	public boolean equals(Object o) {
		if (o instanceof IrcUser) {
			IrcUser other = (IrcUser) o;
			return other._lowerNick.equals(_lowerNick);
		}
		return false;
	}


	/**
	 * Returns the hash code of this User object.
	 * 
	 * @return the hash code of the User object.
	 */
	public int hashCode() {
		return _lowerNick.hashCode();
	}


	/**
	 * Returns the result of calling the compareTo method on lowercased
	 * nicks. This is useful for sorting lists of User objects.
	 * 
	 * @return the result of calling compareTo on lowercased nicks.
	 */
	public int compareTo(IrcUser u) {
		return u._lowerNick.compareTo(_lowerNick);
	}


	/**
	 * Returns whether or not the user represented by this object has the given 
	 * prefix. If the User object has been obtained from a list of users
	 * in a channel, then this will reflect the user's status in that 
	 * channel.  This is useful for checking non-standard prefixes that may
	 * exist on different networks (IRCd's).
	 * 
	 * @since PircBotNg 1.0
	 *
	 * @param prefix the prefix to check for
	 * @return true if the user has the given prefix.
	 */
	public boolean hasPrefix(String prefix) {
		return _prefix.contains(prefix);
	}

	/**
	 * Add prefix to this user object
	 *
	 * @since PircBotNg 1.0
	 *
	 * @param prefix for add
	 */    
	protected void addPrefix(String prefix) {
		if(!(hasPrefix(prefix))) {
			_prefix = prefix + _prefix;
		}        
	}

	/**
	 * Remove prefix from this user object
	 *
	 * @since PircBotNg 1.0
	 *
	 * @param prefix for remove
	 */    
	protected void removePrefix(String prefix) {
		if(hasPrefix(prefix)) {
			int location = _prefix.indexOf(prefix);
			_prefix = _prefix.substring(0, location) + _prefix.substring(location+1);
		}
	}

}
