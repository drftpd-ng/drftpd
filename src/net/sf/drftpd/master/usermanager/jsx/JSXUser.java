/*
 * Created on 2003-jul-26
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.usermanager.jsx;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.PlainTextPasswordUser;
import net.sf.drftpd.master.usermanager.UnixPassword;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.util.Crypt;
import JSX.ObjOut;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class JSXUser
	extends AbstractUser
	implements PlainTextPasswordUser, UnixPassword {
	private String unixPassword;
	private String password;
	transient JSXUserManager usermanager;
	private transient boolean purged;

	public JSXUser(JSXUserManager usermanager, String username) {
		super(username);
		this.usermanager = usermanager;
	}

	public boolean checkPassword(String password) {
		if (this.password == null) {
			if (this.unixPassword == null)
				throw new IllegalStateException("no password set");
			if (password
				.equals(Crypt.crypt(this.password.substring(0, 2), password))) {
				setPassword(password);
				return true;
			}
			return false;
		}
		return this.password.equals(password);
	}

	public void setPassword(String password) {
		this.unixPassword = null;
		this.password = password;
	}

	public String getPassword() {
		return this.password;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.AbstractUser#rename(java.lang.String)
	 */
	public void rename(String username)
		throws ObjectExistsException, UserFileException {
		usermanager.rename(this, username); // throws ObjectExistsException
		usermanager.getUserFile(this.getUsername()).delete();
		this.username = username;
		commit(); // throws IOException
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#commit()
	 */
	public void commit() throws UserFileException {
		if (this.purged)
			return;

		try {
			ObjOut out =
				new ObjOut(
					new FileWriter(
						usermanager.getUserFile(this.getUsername())));
			out.writeObject(this);
			out.close();
		} catch (IOException ex) {
			throw new UserFileException(
				"Error writing userfile for "
					+ this.getUsername()
					+ ": "
					+ ex.getMessage());
		}
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#purge()
	 */
	public void purge() {
		this.purged = true;
		usermanager.remove(this);
		File userfile = usermanager.getUserFile(this.getUsername());
		userfile.delete();
	}
	/* (non-Javadoc)
	 * @see java.lang.Object#finalize()
	 */
	protected void finalize() throws Throwable {
		this.commit();
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.AbstractUser#update()
	 */
	public void update() {
		//an update was made, but commit() should be called from all places so we don't need to do anything.
		//if we do, make sure it's implemented in all set and update methods in AbstractUser
	}
	public String getUnixPassword() {
		return unixPassword;
	}
	public void setUnixPassword(String password) {
		this.password = null;
		this.unixPassword = password;
	}
}
