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
package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.event.ConnectionEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;

import org.apache.oro.text.regex.MalformedPatternException;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.commands.UserManagment;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.IOException;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author mog
 * @version $Id$
 */
public class Login implements CommandHandlerFactory, CommandHandler, Cloneable {
    private static final Logger logger = Logger.getLogger(Login.class);

    /**
     * If _idntAddress == null, IDNT hasn't been used.
     */
    protected InetAddress _idntAddress;
    protected String _idntIdent;

    /**
     * Syntax: IDNT ident@ip:dns
     * Returns nothing on success.
     */
    private Reply doIDNT(BaseFtpConnection conn) {
        if (_idntAddress != null) {
            logger.error("Multiple IDNT commands");
            return new Reply(530, "Multiple IDNT commands");
        }

        if (!conn.getGlobalContext().getConfig().getBouncerIps().contains(conn.getClientAddress())) {
            logger.warn("IDNT from non-bnc");

            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        String arg = conn.getRequest().getArgument();
        int pos1 = arg.indexOf('@');

        if (pos1 == -1) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        int pos2 = arg.indexOf(':', pos1 + 1);

        if (pos2 == -1) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        try {
            _idntAddress = InetAddress.getByName(arg.substring(pos1 + 1, pos2));
            _idntIdent = arg.substring(0, pos1);
        } catch (UnknownHostException e) {
            logger.info("Invalid hostname passed to IDNT", e);

            //this will most likely cause control connection to become unsynchronized
            //but give error anyway, this error is unlikely to happen
            return new Reply(501, "IDNT FAILED: " + e.getMessage());
        }

        // bnc doesn't expect any reply
        return null;
    }

    /**
     * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
     *
     * The argument field is a Telnet string specifying the user's
     * password.  This command must be immediately preceded by the
     * user name command.
     */
    private Reply doPASS(BaseFtpConnection conn) {
        if (conn.getUserNull() == null) {
            return Reply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
        }

        FtpRequest request = conn.getRequest();

        // set user password and login
        String pass = request.hasArgument() ? request.getArgument() : "";

        // login failure - close connection
        if (conn.getUserNull().checkPassword(pass)) {
            conn.getUserNull().login();
            conn.setAuthenticated(true);
            conn.getGlobalContext().getConnectionManager().dispatchFtpEvent(new ConnectionEvent(
                    conn, "LOGIN"));

            Reply response = new Reply(230,
                    conn.jprintf(Login.class, "pass.success"));

            try {
                Textoutput.addTextToResponse(response, "welcome");
            } catch (IOException e) {
                logger.warn("Error reading welcome", e);
            }

            return response;
        }

        return new Reply(530, conn.jprintf(Login.class, "pass.fail"));
    }

    /**
     * <code>QUIT &lt;CRLF&gt;</code><br>
     *
     * This command terminates a USER and if file transfer is not
     * in progress, the server closes the control connection.
     */
    private Reply doQUIT(BaseFtpConnection conn) {
        conn.stop();

        return new Reply(221, conn.jprintf(Login.class, "quit.success"));
    }

    /**
     * <code>USER &lt;SP&gt; &lt;username&gt; &lt;CRLF&gt;</code><br>
     *
     * The argument field is a Telnet string identifying the user.
     * The user identification is that which is required by the
     * server for access to its file system.  This command will
     * normally be the first command transmitted by the user after
     * the control connections are made.
     */
    private Reply doUSER(BaseFtpConnection conn) throws ReplyException {
        FtpRequest request = conn.getRequest();
        conn.setAuthenticated(false);
        conn.setUser(null);

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User newUser;

        try {
            newUser = conn.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(request.getArgument());
        } catch (NoSuchUserException ex) {
            return new Reply(530, ex.getMessage());
        } catch (UserFileException ex) {
            logger.warn("", ex);
            return new Reply(530, "IOException: " + ex.getMessage());
        } catch (RuntimeException ex) {
            logger.error("", ex);

            throw new ReplyException(ex);
        }

        if (newUser.isDeleted()) {
        	return new Reply(530,
        			(String)newUser.getKeyedMap().getObject(
        					UserManagment.REASON,
							Reply.RESPONSE_530_ACCESS_DENIED.getMessage()));
        }
        if(!conn.getGlobalContext().getConfig().isLoginAllowed(newUser)) {
        	return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        try {
            if (((_idntAddress != null) &&
                    newUser.getHostMaskCollection().check(_idntIdent,
                        _idntAddress, null)) ||
                    ((_idntAddress == null) &&
                    (newUser.getHostMaskCollection().check(null,
                        conn.getClientAddress(), conn.getControlSocket())))) {
                //success
                // max_users and num_logins restriction
                Reply response = conn.getGlobalContext()
                                        .getConnectionManager().canLogin(conn,
                        newUser);

                if (response != null) {
                    logger.debug("response != null");

                    return response;
                }

                conn.setUser(newUser);

                return new Reply(331,
                    conn.jprintf(Login.class, "user.success"));
            }
        } catch (MalformedPatternException e) {
            return new Reply(530, e.getMessage());
        }

        //fail
        logger.warn("Failed hostmask check");

        return Reply.RESPONSE_530_ACCESS_DENIED;
    }

    public Reply execute(BaseFtpConnection conn)
        throws ReplyException {
        String cmd = conn.getRequest().getCommand();

        if ("USER".equals(cmd)) {
            return doUSER(conn);
        }

        if ("PASS".equals(cmd)) {
            return doPASS(conn);
        }

        if ("QUIT".equals(cmd)) {
            return doQUIT(conn);
        }

        if ("IDNT".equals(cmd)) {
            return doIDNT(conn);
        }

        throw UnhandledCommandException.create(Login.class, conn.getRequest());
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        try {
            return (Login) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
