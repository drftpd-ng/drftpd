/*
 * Created on 2004-okt-12
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.drftpd.slave.async;


/**
 * @author mog
 * @version $Id: AsyncResponseException.java,v 1.2 2004/11/02 07:32:53 zubov Exp $
 */
public class AsyncResponseException extends AsyncResponse {
    private static final long serialVersionUID = -6024340147843529987L;
    private Throwable _t;

    public AsyncResponseException(String index, Throwable t) {
        super(index);
        _t = t;
    }

    public Throwable getThrowable() {
        return _t;
    }
}
