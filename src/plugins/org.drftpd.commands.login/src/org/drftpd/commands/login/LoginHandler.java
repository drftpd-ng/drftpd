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
package org.drftpd.commands.login;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;


import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.login.PostHookInterface;
import org.drftpd.commands.login.PreHookInterface;
import org.drftpd.event.UserEvent;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.FtpReply;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author mog
 * @author djb61
 * @version $Id: Login.java 1621 2007-02-13 20:41:31Z djb61 $
 */
public class LoginHandler implements CommandInterface {
    private static final Logger logger = Logger.getLogger(LoginHandler.class);

    /**
     * If _idntAddress == null, IDNT hasn't been used.
     */
    protected InetAddress _idntAddress;
    protected String _idntIdent;

    private HashMap<Integer, Object[]> _postHooks;

	private HashMap<Integer, Object[]> _preHooks;

	private ArrayList<Integer> _postHookPriorities;

	private ArrayList<Integer> _preHookPriorities;

	public void initialize(String method) {
		_postHooks = new HashMap<Integer, Object[]>();
		_preHooks = new HashMap<Integer, Object[]>();
		
		PluginManager manager = PluginManager.lookup(this);

		/* Iterate through the post hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint postHookExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"org.drftpd.commands.dataconnection", "PostHook");

		for (Iterator postHooks = postHookExtPoint.getConnectedExtensions().iterator();
			postHooks.hasNext();) { 

			Extension postHook = (Extension) postHooks.next();

			if (postHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(postHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(postHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
					}
				}
				ClassLoader postHookLoader = manager.getPluginClassLoader( 
						postHook.getDeclaringPluginDescriptor());
				try {
					Class postHookCls = postHookLoader.loadClass(
							postHook.getParameter("HookClass").valueAsString());
					PostHookInterface postHookInstance = (PostHookInterface) postHookCls.newInstance();
					postHookInstance.initialize();

					Method m = postHookInstance.getClass().getMethod(
							postHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class, CommandResponse.class});
					_postHooks.put((Integer)postHook.getParameter("Priority").valueAsNumber(),
							new Object[] {m,postHookInstance});
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add post hook handler to " +
							"org.drftpd.commands.dataconnection from plugin: "
							+postHook.getDeclaringPluginDescriptor().getId());
				}
			}
		}

		_postHookPriorities = new ArrayList<Integer>(_postHooks.keySet());
		Collections.sort(_postHookPriorities);
		Collections.reverse(_postHookPriorities);

		/* Iterate through the ppre hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint preHookExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"org.drftpd.commands.dataconnection", "PreHook");

		for (Iterator preHooks = preHookExtPoint.getConnectedExtensions().iterator();
			preHooks.hasNext();) { 

			Extension preHook = (Extension) preHooks.next();

			if (preHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(preHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(preHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
					}
				}
				ClassLoader preHookLoader = manager.getPluginClassLoader( 
						preHook.getDeclaringPluginDescriptor());
				try {
					Class preHookCls = preHookLoader.loadClass(
							preHook.getParameter("HookClass").valueAsString());
					PreHookInterface preHookInstance = (PreHookInterface) preHookCls.newInstance();
					preHookInstance.initialize();

					Method m = preHookInstance.getClass().getMethod(
							preHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class});
					_preHooks.put((Integer)preHook.getParameter("Priority").valueAsNumber(),
							new Object[] {m,preHookInstance});
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add pre hook handler to " +
							"org.drftpd.commands.dataconnection from plugin: "
							+preHook.getDeclaringPluginDescriptor().getId());
				}
			}
		}

		_preHookPriorities = new ArrayList<Integer>(_preHooks.keySet());
		Collections.sort(_preHookPriorities);
		Collections.reverse(_preHookPriorities);
	}

	private void doPostHooks(CommandRequest request, CommandResponse response) {
		for (Integer key : _postHookPriorities) {
			Object[] hook = _postHooks.get(key);
			Method m = (Method) hook[0];
			try {
				m.invoke(hook[1], new Object[] {request, response});
			}
			catch (Exception e) {
				/* Not that important, this just means that this post hook
				 * failed and we'll just move onto the next one
				 */
			}
		}
	}

	private CommandRequest doPreHooks(CommandRequest request) {
		CommandRequest _request = request;
		_request.setAllowed(new Boolean(true));
		for (Integer key : _preHookPriorities) {
			Object[] hook = _preHooks.get(key);
			Method m = (Method) hook[0];
			try {
				_request = (CommandRequest) m.invoke(hook[1], new Object[] {_request});
			}
			catch (Exception e) {
				/* Not that important, this just means that this pre hook
				 * failed and we'll just move onto the next one
				 */
			}
			if (!_request.getAllowed()) {
				break;
			}
		}
		return _request;
	}

    /**
     * Syntax: IDNT ident@ip:dns
     * Returns nothing on success.
     */
    public CommandResponse doIDNT(CommandRequest request) {
    	CommandRequest _request = request;
    	CommandResponse _response;
    	_request = doPreHooks(_request);
    	if(!_request.getAllowed()) {
    		_response = _request.getDeniedResponse();
    		if (_response == null) {
    			_response = StandardCommandManager.genericResponse(
    					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
    					_request.getUser());
    		}
    		doPostHooks(_request, _response);
    		return _response;
    	}
    	BaseFtpConnection conn = _request.getConnection();
        if (_idntAddress != null) {
            logger.error("Multiple IDNT commands");
            _response = new CommandResponse(530, "Multiple IDNT commands",
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;

        }

        if (!conn.getGlobalContext().getConfig().getBouncerIps().contains(conn.getClientAddress())) {
            logger.warn("IDNT from non-bnc");

            _response = StandardCommandManager.genericResponse(
					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
        }

        String arg = _request.getArgument();
        int pos1 = arg.indexOf('@');

        if (pos1 == -1) {
        	_response = StandardCommandManager.genericResponse(
					"RESPONSE_501_SYNTAX_ERROR", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
        }

        int pos2 = arg.indexOf(':', pos1 + 1);

        if (pos2 == -1) {
        	_response = StandardCommandManager.genericResponse(
					"RESPONSE_501_SYNTAX_ERROR", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
        }

        try {
            _idntAddress = InetAddress.getByName(arg.substring(pos1 + 1, pos2));
            _idntIdent = arg.substring(0, pos1);
        } catch (UnknownHostException e) {
            logger.info("Invalid hostname passed to IDNT", e);

            //this will most likely cause control connection to become unsynchronized
            //but give error anyway, this error is unlikely to happen
            _response = new CommandResponse(501, "IDNT FAILED: " + e.getMessage(),
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;
        }

        // bnc doesn't expect any reply
        doPostHooks(_request, null);
        return null;
    }

    /**
     * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
     *
     * The argument field is a Telnet string specifying the user's
     * password.  This command must be immediately preceded by the
     * user name command.
     */
    public CommandResponse doPASS(CommandRequest request) {
    	CommandRequest _request = request;
    	CommandResponse _response;
    	_request = doPreHooks(_request);
    	if(!_request.getAllowed()) {
    		_response = _request.getDeniedResponse();
    		if (_response == null) {
    			_response = StandardCommandManager.genericResponse(
    					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
    					_request.getUser());
    		}
    		doPostHooks(_request, _response);
    		return _response;
    	}
    	BaseFtpConnection conn = _request.getConnection();
        if (conn.getUserNull() == null) {
        	_response = StandardCommandManager.genericResponse(
					"RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
        }

        // set user password and login
        String pass = _request.hasArgument() ? _request.getArgument() : "";

        // login failure - close connection
        if (conn.getUserNull().checkPassword(pass)) {
            conn.setAuthenticated(true);
            conn.getGlobalContext().dispatchFtpEvent(new UserEvent(
                    conn.getUserNull(), "LOGIN", System.currentTimeMillis()));

            _response = new CommandResponse(230,
                    conn.jprintf(LoginHandler.class, "pass.success"),
            		_request.getCurrentDirectory(), _request.getUser());
            
            /* TODO: Come back to this later
             * 
             */
            /*try {
                Textoutput.addTextToResponse(response, "welcome");
            } catch (IOException e) {
                logger.warn("Error reading welcome", e);
            }*/

            doPostHooks(_request, _response);
            return _response;
        }

        _response = new CommandResponse(530, conn.jprintf(LoginHandler.class, "pass.fail"),
        		_request.getCurrentDirectory(), _request.getUser());
        doPostHooks(_request, _response);
        return _response;
    }

    /**
     * <code>QUIT &lt;CRLF&gt;</code><br>
     *
     * This command terminates a USER and if file transfer is not
     * in progress, the server closes the control connection.
     */
    public CommandResponse doQUIT(CommandRequest request) {
    	CommandRequest _request = request;
    	CommandResponse _response;
    	_request = doPreHooks(_request);
    	if(!_request.getAllowed()) {
    		_response = _request.getDeniedResponse();
    		if (_response == null) {
    			_response = StandardCommandManager.genericResponse(
    					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
    					_request.getUser());
    		}
    		doPostHooks(_request, _response);
    		return _response;
    	}
    	BaseFtpConnection conn = _request.getConnection();
        conn.stop();

        _response = new CommandResponse(221, conn.jprintf(LoginHandler.class, "quit.success"),
        		_request.getCurrentDirectory(), _request.getUser());
        doPostHooks(_request, _response);
        return _response;
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
    public CommandResponse doUSER(CommandRequest request) {
    	CommandRequest _request = request;
    	CommandResponse _response;
    	_request = doPreHooks(_request);
    	if(!_request.getAllowed()) {
    		_response = _request.getDeniedResponse();
    		if (_response == null) {
    			_response = StandardCommandManager.genericResponse(
    					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
    					_request.getUser());
    		}
    		doPostHooks(_request, _response);
    		return _response;
    	}
    	BaseFtpConnection conn = _request.getConnection();

        conn.setAuthenticated(false);
        conn.setUser(null);

        // argument check
        if (!_request.hasArgument()) {
        	_response = StandardCommandManager.genericResponse(
					"RESPONSE_501_SYNTAX_ERROR", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
        }

        User newUser;

        try {
            newUser = conn.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(request.getArgument());
        } catch (NoSuchUserException ex) {
        	_response = new CommandResponse(530, ex.getMessage(),
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;
        } catch (UserFileException ex) {
            logger.warn("", ex);
            _response = new CommandResponse(530, "IOException: " + ex.getMessage(),
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;
        } catch (RuntimeException ex) {
            logger.error("", ex);

            /* TODO: Come back to this later
             * 
             */
            //throw new ReplyException(ex);
            _response = new CommandResponse(530, "RuntimeException: " + ex.getMessage(),
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;
        }

        if (newUser.isDeleted()) {
        	/* TODO Come back and fix this
        	 * 
        	 *
        	return new Reply(530,
        			(String)newUser.getKeyedMap().getObject(
        					UserManagement.REASON,
							Reply.RESPONSE_530_ACCESS_DENIED.getMessage()));*/
        }
        if(!conn.getGlobalContext().getConfig().isLoginAllowed(newUser)) {
        	_response = StandardCommandManager.genericResponse(
					"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
					_request.getUser());
        	doPostHooks(_request, _response);
            return _response;
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
                FtpReply ftpResponse = conn.getGlobalContext()
                                        .getConnectionManager().canLogin(conn,
                        newUser);

                if (ftpResponse != null) {
                	_response = new CommandResponse(ftpResponse.getCode(), ftpResponse.getMessage(),
                    		_request.getCurrentDirectory(), _request.getUser());
                	doPostHooks(_request, _response);
                    return _response;
                }

                _response = new CommandResponse(331,
                        conn.jprintf(LoginHandler.class, "user.success"),
                		_request.getCurrentDirectory(), newUser.getName());
                doPostHooks(_request, _response);
                return _response;
            }
        } catch (MalformedPatternException e) {
        	_response = new CommandResponse(530, e.getMessage(),
            		_request.getCurrentDirectory(), _request.getUser());
            doPostHooks(_request, _response);
            return _response;
        }

        //fail
        logger.warn("Failed hostmask check");

        _response = StandardCommandManager.genericResponse(
				"RESPONSE_530_ACCESS_DENIED", _request.getCurrentDirectory(),
				_request.getUser());
    	doPostHooks(_request, _response);
        return _response;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public void unload() {
    }
}
