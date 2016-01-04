package org.drftpd.usermanager.encryptedjavabeans;

import java.beans.DefaultPersistenceDelegate;
import java.lang.ref.SoftReference;
import java.util.Properties;
import java.io.OutputStream;
import java.beans.XMLEncoder;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.drftpd.usermanager.javabeans.BeanUser;
import org.drftpd.usermanager.javabeans.BeanUserManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

public class EncryptedBeanUserManager extends BeanUserManager {

	protected static final Logger logger = Logger.getLogger(EncryptedBeanUserManager.class);
	
	private int _passcrypt = 0;
	
	/*
	 * Constructor to read encryption type, and subscribe to events
	 */
	public EncryptedBeanUserManager() {
		AnnotationProcessor.process(this);
		readPasscrypt();
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	readPasscrypt();
    }
	
	/*
	 * Reads the Password Encryption into memory for user
	 */
	private void readPasscrypt() {
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("encryptedbeanuser.conf");
		String passcrypt = cfg.getProperty("passcrypt");
		if (passcrypt == null) {
			_passcrypt = 0;
		} else if (passcrypt.equalsIgnoreCase("md2")) {
			_passcrypt = 1;
		} else if (passcrypt.equalsIgnoreCase("md5")) {
			_passcrypt = 2;
		} else if (passcrypt.equalsIgnoreCase("sha-1")) {
			_passcrypt = 3;
		} else if (passcrypt.equalsIgnoreCase("sha-256")) {
			_passcrypt = 4;
		} else if (passcrypt.equalsIgnoreCase("sha-384")) {
			_passcrypt = 5;			
		} else if (passcrypt.equalsIgnoreCase("sha-512")) {
			_passcrypt = 6;
		} else {
			_passcrypt = 0;
		}
	}
	
	/*
	 * Returns current Passcrypt type
	 */
	protected int getPasscrypt() {
		return _passcrypt;
	}
	
	/**
	 * Creates a user named 'username' and adds it to the users map.
	 */
	protected synchronized User createUser(String username) {
		EncryptedBeanUser buser = new EncryptedBeanUser(this, username);
		_users.put(username, new SoftReference<User>(buser));
		return buser;
	}
	
	@Override
	public XMLEncoder getXMLEncoder(OutputStream out) {
		XMLEncoder e = super.getXMLEncoder(out);
		e.setPersistenceDelegate(EncryptedBeanUser.class,
				new DefaultPersistenceDelegate(new String[] { "name" }));
		return e;
	}
	
	@Override
	protected User loadUser(String userName) throws NoSuchUserException, UserFileException {
		User user = super.loadUser(userName);
		if ( !(user instanceof EncryptedBeanUser) && (user instanceof BeanUser)) {
			EncryptedBeanUser buser = new EncryptedBeanUser(this,(BeanUser) user);
			return buser;
		}
		return user;
	}
}
