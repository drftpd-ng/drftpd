package net.sf.drftpd.master;

import java.io.Serializable;
//import ranab.io.VirtualDirectory;
import java.net.InetAddress;
import java.rmi.server.UID;

/**
 * Generic user class. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */

public
class User implements Serializable {
    
    protected String mstUserName    = null;
    protected String mstPassword    = null;

    protected long mlIdleTime          = 0; // no limit
    protected int  miUploadRateLimit   = 0; // no limit
    protected int  miDownloadRateLimit = 0; // no limit
    
    protected long mlLoginTime         = 0;
    protected long mlLastAccessTime    = 0;

    protected boolean mbEnabled        = true;
    
    protected VirtualDirectory mUserDirectory = null;
    protected String mstSessionId             = null;    
    protected InetAddress mClientAddress      = null;

    /**
     * Constructor, set session id and default virtual directory object.
     */
    public User() {
        //mUserDirectory = new VirtualDirectory();
        mstSessionId = new UID().toString();
    }
    
    
    /**
     * Get the user name.
     */
    public String getName() {
        return mstUserName;
    }
        
    /**
     * Set user name.
     */
    public void setName(String name) {
        mstUserName = name;
    }
    

    /**
     * Get the user password.
     */
    public String getPassword() {
        return mstPassword;
    }
    
    /**
     * Set user password
     */
    public void setPassword(String pass) {
        mstPassword = pass;
    }


    /**
     * Get the maximum idle time in second.
     */
    public int getMaxIdleTime() {
        return (int)(mlIdleTime/1000);
    }

    /**
     * Set the maximum idle time in second.
     */
    public void setMaxIdleTime(int idleSec) {
        if(idleSec < 0L) {
            mlIdleTime = 0L;
        }
        mlIdleTime = idleSec * 1000L;
    }

    
    /**
     * Get the user enable status.
     */
    public boolean getEnabled() {
        return mbEnabled;
    }
    
    /**
     * Set the user enable status
     */
    public void setEnabled(boolean enb) {
        mbEnabled = enb;
    }
    

    /**
     * Get maximum user upload rate in bytes/sec.
     */
    public int getMaxUploadRate() {
        return miUploadRateLimit;
    }
    
    /**
     * Set user maximum upload rate limit.
     * Less than or equal to zero means no limit.
     */
    public void setMaxUploadRate(int rate) {
        miUploadRateLimit = rate;
    }
    

    /**
     * Get maximum user download rate in bytes/sec
     */
    public int getMaxDownloadRate() {
        return miDownloadRateLimit;
    }
    
    /**
     * Set user maximum download rate limit.
     * Less than or equal to zero means no limit.
     */
    public void setMaxDownloadRate(int rate) {
        miDownloadRateLimit = rate;
    }
    
    
    /**
     * Get client address
     */
    public InetAddress getClientAddress() {
       return mClientAddress;
    }

    /**
     * Set client address
     */
    public void setClientAddress(InetAddress clientAddress) {
       mClientAddress = clientAddress;
    }


    /**
     * get user filesystem view
     */
    public VirtualDirectory getVirtualDirectory() {
        return mUserDirectory;
    }
    
    /**
     * Get session id.
     */
    public String getSessionId() {
       return mstSessionId;
    }        
    
    /**
     * Get user loglin time.
     */    
    public long getLoginTime() {
       return mlLoginTime;
    }

    /**
     * Get last access time
     */
    public long getLastAccessTime() {
       return mlLastAccessTime;
    }
    
    /**
     * Check the user login status.
     */
    public boolean hasLoggedIn() {
        return mlLoginTime != 0;
    }
    
    /**
     * User login.
     */
    public void login() {
        mlLoginTime = System.currentTimeMillis();
        mlLastAccessTime = mlLoginTime;
    }    
    
    /**
     * User logout
     */    
    public void logout() {
        mlLoginTime = 0;
    }    


    /**
     * Is an active user (is removable)?
     * Compares the last access time with the specified time.
     */
    public boolean isActive(long currTime) {
         boolean bActive = true;
         long maxIdleTime = getMaxIdleTime() * 1000; // milliseconds
         if(maxIdleTime != 0L) {
            long idleTime = currTime - mlLastAccessTime;
            bActive = maxIdleTime > idleTime;
         }
         return bActive;
    } 
      
    /**
     * Is still active. Compares the last access time with the
     * current time.
     */
    public boolean isActive() {
        return isActive(System.currentTimeMillis());
    }
    
    /**
     * Hit user - update last access time
     */
    public void hitUser() {
       mlLastAccessTime = System.currentTimeMillis();
    }     

    /**
     * Equality check.
     */
    public boolean equals(Object obj) {
        if (obj instanceof User) {
            return ((User)obj).mstSessionId.equals(mstSessionId);
        }
        return false;
    } 

    /** 
     * String representation
     */
    public String toString() {
        return mstUserName;
    }    
}
