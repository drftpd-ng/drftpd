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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UnhandledCommandException;

import org.drftpd.slave.Slave;

import java.io.PrintWriter;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;


/**
 * @version $Id$
 */
public class Misc implements CommandHandlerFactory, CommandHandler {
    /**
     * <code>ABOR &lt;CRLF&gt;</code><br>
     *
     * This command tells the server to abort the previous FTP
     * service command and any associated transfer of data.
     * No action is to be taken if the previous command
     * has been completed (including data transfer).  The control
     * connection is not to be closed by the server, but the data
     * connection must be closed.
     * Current implementation does not do anything. As here data
     * transfers are not multi-threaded.
     */
    private Reply doABOR(BaseFtpConnection conn) {
        return Reply.RESPONSE_226_CLOSING_DATA_CONNECTION;
    }

    // LIST;NLST;RETR;STOR
    private Reply doFEAT(BaseFtpConnection conn) {
        PrintWriter out = conn.getControlWriter();
        out.print("211-Extensions supported:\r\n");

        for (Iterator iter = conn.getCommandManager().getCommandHandlersMap()
                                 .values().iterator(); iter.hasNext();) {
            CommandHandler hnd = (CommandHandler) iter.next();
            String[] feat = hnd.getFeatReplies();

            if (feat == null) {
                continue;
            }

            for (int i = 0; i < feat.length; i++) {
                out.print(" " + feat[i] + "\r\n");
            }
        }

        //				+ " CLNT\r\n"
        //				+ " MDTM\r\n"
        //				+ " PRET\r\n"
        //				+ " SIZE\r\n"
        //				+ " XCRC\r\n"
        out.print("211 End\r\n");

        return null;
    }

    /**
     * <code>HELP [&lt;SP&gt; <string>] &lt;CRLF&gt;</code><br>
     *
     * This command shall cause the server to send helpful
     * information regarding its implementation status over the
     * control connection to the user.  The command may take an
     * argument (e.g., any command name) and return more specific
     * information as a response.
     */

    //TODO implement HELP, SITE HELP would be good too.
    //	public FtpReply doHELP(BaseFtpConnection conn) {
    //
    // print global help
    //		if (!request.hasArgument()) {
    //		FtpReply response = new FtpReply(214);
    //		response.addComment("The following commands are recognized.");
    //out.write(ftpStatus.getResponse(214, null, user, null));
    //		Method methods[] = this.getClass().getDeclaredMethods();
    //		for (int i = 0; i < methods.length; i++) {
    //			Method method = methods[i];
    //			Class parameterTypes[] = method.getParameterTypes();
    //			if (parameterTypes.length == 2
    //				&& parameterTypes[0] == FtpRequest.class
    //				&& parameterTypes[1] == PrintWriter.class) {
    //				String commandName =
    //					method.getName().substring(2).replace('_', ' ');
    //				response.addComment(commandName);
    //			}
    //		}
    //		out.print(response);
    //		return;
    //		}
    //
    //		// print command specific help
    //		String ftpCmd = request.getArgument().toUpperCase();
    //		String args[] = null;
    //		FtpRequest tempRequest = new FtpRequest(ftpCmd);
    //		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
    //		return;
    //	}
    private Reply doSITE_STAT(BaseFtpConnection conn) {
        if (conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
        }

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

        response.addComment(conn.status());

        return response;
    }

    private Reply doSITE_TIME(BaseFtpConnection conn) {
        if (conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        return new Reply(200, "Server time is: " + new Date());
    }

    private Reply doSITE_HELP(BaseFtpConnection conn) {
        String cmd = "";
        String msg = "";
        if (conn.getRequest().hasArgument()) {
            cmd = conn.getRequest().getArgument();
        }
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        Map handlers = conn.getCommandManager().getCommandHandlersMap();
        for (Iterator iter = handlers.keySet().iterator(); iter.hasNext();) {
            CommandHandler hnd = (CommandHandler) handlers.get(iter.next());
            msg += hnd.getHelp(cmd.toLowerCase());
        }
        if ("".equals(msg))
            return new Reply(200,"No help for site "+ cmd);
        
        response.addComment(msg);
        return response;
    }

    private Reply doSITE_VERS(BaseFtpConnection conn) {
        return new Reply(200, Slave.VERSION);
    }
    
    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if ("ABOR".equals(cmd)) {
            return doABOR(conn);
        }

        if ("FEAT".equals(cmd)) {
            return doFEAT(conn);
        }

        if ("SITE STAT".equals(cmd)) {
            return doSITE_STAT(conn);
        }

        if ("SITE TIME".equals(cmd)) {
            return doSITE_TIME(conn);
        }

        if ("SITE VERS".equals(cmd)) {
            return doSITE_VERS(conn);
        }

        if ("SITE HELP".equals(cmd)) {
            return doSITE_HELP(conn);
        }

        throw UnhandledCommandException.create(Misc.class, conn.getRequest());
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String getHelp(String cmd) {
        ResourceBundle bundle = ResourceBundle.getBundle(Misc.class.getName());
        if ("".equals(cmd))
            return bundle.getString("help.general")+"\n";
        else if("help".equals(cmd))
            return bundle.getString("help.help")+"\n";
        else
            return "";
    }
    
    public String[] getFeatReplies() {
        return null;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
