package net.sf.drftpd.master;
import java.util.Collection;
import net.sf.drftpd.LinkedRemoteFile;

/**
 * This is the base class of all the user manager classes.
 * If we want to add a new user manager, we have to override
 * this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public abstract class UserManager {

	//    protected FtpConfig mConfig;
//	protected String mstAdminName;

	/**
	 * Constrcutor
	 */

	/**
	 * Save the user. If a new user, create it else update the
	 * existing user.
	 */
	public abstract void save(User user) throws Exception;

	/**
	 * Delete the user from the system.
	 *
	 * @param name name of the user to be deleted. 
	 */
	public abstract void delete(String userName) throws Exception;

	/**
	 * Get user by name.
	 */
	public abstract User getUserByName(String name);

	/**
	 * Get all user names in the system.
	 */
	public abstract Collection getAllUserNames();

	/**
	 * User existance check.
	 *
	 * @param name user name
	 */
	public abstract boolean exists(String name);

	/**
	 * Authenticate user
	 */
	public abstract boolean authenticate(String login, String password);

	/**
	 * Load the user data again
	 */
	public void reload() throws Exception {
	}

	/**
	 * Close the user manager - dummy method.
	 */
	public void dispose() {
	}

	/**
	 * Get admin name
	 */
	/*
	public String getAdminName() {
		return mstAdminName;
	}
	*/
}
