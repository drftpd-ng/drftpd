package net.sf.drftpd.master;

import java.io.OutputStream;
import java.io.Serializable;

//import ranab.io.IoUtils;
//import ranab.io.AsciiOutputStream; 
//import ranab.server.ftp.usermanager.User;


/**
 * Ftp user class. It handles all user specific file system task.
 * It supports user virtual root directory.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public
class FtpUser extends User implements Serializable {
    
    public final static String ANONYMOUS = "anonymous";
    
    private char mcDataType    = 'A';
    private char mcStructure   = 'F';
    private char mcMode        = 'S';
    
    /**
     * Constructor - does nothing.
     */
    public FtpUser(VirtualDirectory vd) {
	mUserDirectory = vd;
    }
    
    /**
     * Get the user data type.
     */
    public char getType() {
        return mcDataType;
    }

    /**
     * Set the data type. Supported types are A (ascii) and I (binary).
     * @return true if success
     */
    public boolean setType(char type) {
        type = Character.toUpperCase(type);
        if( (type != 'A') && (type != 'I') ) {
            return false;
        }
        mcDataType = type;
        return true;
    }


    /**
     * Get the file structure.
     */
    public char getStructure() {
        return mcStructure;
    } 
   
    /**
     * Set the file structure. Supported structure type is F (file).
     * @return true if success
     */
    public boolean setStructure(char stru) {
        stru = Character.toUpperCase(stru);
        if(stru != 'F') {
            return false;
        }
        mcStructure = stru;
        return true;
    }

    
    /**
     * Get the transfer mode.
     */
    public char getMode() {
        return mcMode;
    }
    
    /**
     * Set the transfer type. Supported transfer type is S (stream).
     * @return true if success
     */
    public boolean setMode(char md) {
        md = Character.toUpperCase(md);
        if(md != 'S') {
            return false;
        }
        mcMode = md;
        return true;
    }

    /**
     * Get output stream. Returns <code>ftpserver.util.AsciiOutputStream</code>
     * if the transfer type is ASCII.
     */
    public OutputStream getOutputStream(OutputStream os) {
        //os = IoUtils.getBufferedOutputStream(os);
        if(mcDataType == 'A') {
            os = new AsciiOutputStream(os);
        }
        return os;
    }
    
    /**
     * Is an anonymous user?
     */
    public boolean getIsAnonymous() {
        return ANONYMOUS.equals(getName());
    }
}


