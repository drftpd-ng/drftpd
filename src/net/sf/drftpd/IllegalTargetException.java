package net.sf.drftpd;

import java.io.IOException;

/*
 * Created on 2003-jul-12
 *
 * To change the template for this generated file go to
 */

/**
 * @author mog
 *
 * @version $Id: IllegalTargetException.java,v 1.3 2003/12/23 13:38:18 mog Exp $
 */
public class IllegalTargetException extends IOException {

	/**
	 * 
	 */
	public IllegalTargetException() {
		super();
	}

	/**
	 * @param message
	 */
	public IllegalTargetException(String message) {
		super(message);
	}

}
