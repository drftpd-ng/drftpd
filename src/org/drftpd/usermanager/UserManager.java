/*
 * Created on Nov 6, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.drftpd.usermanager;


import java.util.Collection;

import org.drftpd.master.ConnectionManager;


/**
 * @author mog
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public interface UserManager {
    public abstract User create(String username) throws UserFileException;

    public abstract Collection getAllGroups() throws UserFileException;

    /**
     * Get all user names in the system.
     */
    public abstract Collection getAllUsers() throws UserFileException;

    public abstract Collection getAllUsersByGroup(String group)
        throws UserFileException;

    /**
     * Get user by name.
     */
    public abstract User getUserByName(String username)
        throws NoSuchUserException, UserFileException;

    public abstract User getUserByIdent(String ident)
    	throws NoSuchUserException, UserFileException;

    public abstract User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException;

    /**
     * A kind of constuctor defined in the interface for allowing the
     * usermanager to get a hold of the ConnectionManager object for dispatching
     * events etc.
     */
    public abstract void init(ConnectionManager mgr);

    public abstract void saveAll() throws UserFileException;

	public abstract User getUserByNameIncludeDeleted(String argument) throws NoSuchUserException, UserFileException;
}
