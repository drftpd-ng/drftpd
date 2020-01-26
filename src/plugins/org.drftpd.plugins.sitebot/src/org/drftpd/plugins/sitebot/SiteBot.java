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
package org.drftpd.plugins.sitebot;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponseInterface;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.FatalException;
import org.drftpd.misc.CaseInsensitiveConcurrentHashMap;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.config.ChannelConfig;
import org.drftpd.plugins.sitebot.config.ServerConfig;
import org.drftpd.plugins.sitebot.config.SiteBotConfig;
import org.drftpd.plugins.sitebot.event.InviteEvent;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public class SiteBot implements ReplyConstants, Runnable {

	private static final Logger logger = LogManager.getLogger(SiteBot.class);

	public static final ReplacerEnvironment GLOBAL_ENV = new ReplacerEnvironment();

	static {
		GLOBAL_ENV.add("bold", "\u0002");
		GLOBAL_ENV.add("coloroff", "\u000f");
		GLOBAL_ENV.add("color", "\u0003");
		GLOBAL_ENV.add("underline", "\u001f");
	}

	// Configuration
	private String _confDir;
	private SiteBotConfig _config = null;
	private String _name;

	// Connection stuff.
	private InputThread _inputThread = null;
	private OutputThread _outputThread = null;
	private Charset _charset = Charset.defaultCharset();
	private InetAddress _inetAddress = null;

	// Details about the last server that we connected to.
	private String _server = null;
	private int _port = -1;
	private String _password = null;
	private SocketFactory _factory = null;

	// Outgoing message stuff.
	private Queue _outQueue = new Queue();
	private CaseInsensitiveConcurrentHashMap<String,OutputWriter> _writers = new CaseInsensitiveConcurrentHashMap<>();
	private ThreadPoolExecutor _pool;

	// A HashMap of channels that points to a selfreferential HashMap of
	// User objects (used to remember which users are in which channels).
	private CaseInsensitiveHashMap<String,HashMap<IrcUser,IrcUser>> _channels = new CaseInsensitiveHashMap<>();

	// A HashMap to temporarily store channel topics when we join them
	// until we find out who set that topic.
	private HashMap<String,String> _topics = new HashMap<>();

	// A HashMap of nicknames of which we know details about, such as the
	// corresponding ftp user.
	private CaseInsensitiveHashMap<String,UserDetails> _users = new CaseInsensitiveHashMap<>();

	// A HashMap of BlowfishManager objects for channels we are aware of
	private CaseInsensitiveHashMap<String,BlowfishManager> _ciphers = new CaseInsensitiveHashMap<>();

	/* A HashMap of DH1080 objects for users, this is used to store a
	 * temporary object when initiating a DH1080 request whilst we wait
	 * for the response
	 */
	private CaseInsensitiveHashMap<String,DH1080> _dh1080 = new CaseInsensitiveHashMap<>();

	// Command Manager to use for executing commands
	private HashMap<String,Properties> _cmds;
	private CommandManagerInterface _commandManager;
	private static final String themeDir = "conf/themes/irc";

	// An ArrayList to hold references to announce plugins we have connected
	private ArrayList<AbstractAnnouncer> _announcers = new ArrayList<>();
	private AnnounceConfig _announceConfig = null;
	private ArrayList<String> _eventTypes = new ArrayList<>();

	// ArrayList to hold Listeners
	private ArrayList<ListenerInterface> _listeners = new ArrayList<>();
	
	// Default settings for the PircBot.
	private String _version;
	private String _nick = null;
	private String _ctcpVersion = "DrFTPD SiteBot ";
	private String _finger = "You ought to be arrested for fingering a bot!";
	private String _hostMask = null;
	private boolean _isTerminated = false;
	private boolean _userDisconnected = false;

	// A HashMap to store the available prefixes and associated mode operator
	private HashMap<String,String> _userPrefixes = new HashMap<>();
	// prefixes as delivered from the server .. highest to lowest - default to op/voice
	private String _userPrefixOrder = "@+";
	private String _channelPrefixes = "#&+!";

	public SiteBot(String confDir) throws FatalException {
		_confDir = confDir;
	}

	public void run() {
		_config = new SiteBotConfig(GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin(_confDir+"/irc.conf"));

		// set a default mode/prefix for the users in case the server doesn't have it defined properly
		// default to PREFIX=(ov)@+
		_userPrefixes.put("o", "@");
		_userPrefixes.put("v", "+");

		// Set version to current plugin version
		_version = CommonPluginUtils.getPluginVersionForObject(this);

		// Set CTCP responses if defined in config
		String finger = _config.getCTCPFinger();
		if (finger != null && !finger.equals("")) {
			_finger = finger;
		}
		String version = _config.getCTCPVersion();
		if (version != null && !version.equals("")) {
			_ctcpVersion = version;
		}
		else {
			_ctcpVersion = _ctcpVersion + _version;
		}

		// Set internal name
		_name = _config.getBotName();

		// Set thread name to the internal bot name to aid debugging
		Thread.currentThread().setName(_name);

		// Load commands config and get/initialise a command manager with them
		loadCommands();
		_commandManager = GlobalContext.getGlobalContext().getCommandManager();
		_commandManager.initialize(_cmds, themeDir);

		// Initialise the thread pool for executing commands
		int maxCommands = _config.getCommandsMax();
		if (_config.getCommandsQueue()) {
			_pool = new ThreadPoolExecutor(maxCommands, maxCommands,
					60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(),
					new CommandThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
		} else if (_config.getCommandsBlock()) {
			_pool = new ThreadPoolExecutor(maxCommands, maxCommands,
					60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
					new CommandThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
		} else {
			throw new FatalException("commands.full has an invalid value in irc.conf");
		}
		try {
			setEncoding(_config.getCharset());
			connect();
		} catch (Exception e) {
			logger.warn("Error connecting to server",e);
		}

		// Find and start announcers
		loadAnnouncers(_confDir);

		// Find all Bot Listeners
		loadListeners();
		
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	/**
	 * Attempt to connect to the specified IRC server using the supplied
	 * password.
	 * The onConnect method is called upon success.
	 *
	 * @param hostname The hostname of the server to connect to.
	 * @param port The port number to connect to on the server.
	 * @param password The password to use to join the server.
	 *
	 * @throws IOException if it was not possible to connect to the server.
	 * @throws IrcException if the server would not let us join it.
	 * @throws NickAlreadyInUseException if our nick is already in use on the server.
	 */
	public final synchronized void connect() throws IOException, IrcException, NickAlreadyInUseException {

		ServerConfig serverConfig = _config.getServer();
		_server = serverConfig.getHostName();
		_port = serverConfig.getPort();
		_password = serverConfig.getPassword();
		_factory = serverConfig.getSocketFactory();
		_inputThread = null;
		_outputThread = null;

		if (isConnected()) {
			throw new IOException("The Bot is already connected to an IRC server.  Disconnect first.");
		}

		// Clear everything we may have know about channels.
		this.removeAllChannels();

		String bindAddress = _config.getLocalBindHost();
		Socket socket = null;
		try {
			// Connect to the server.
			if (_factory == null) {
				_factory = SocketFactory.getDefault();
			}
			if (bindAddress == null || bindAddress.equals("")) {
				socket = _factory.createSocket(_server, _port);
			}
			else {
				socket = _factory.createSocket(_server, _port, InetAddress.getByName(bindAddress), 0);
			}
			String[] sslProtocols = GlobalContext.getConfig().getSSLProtocols();
			if (sslProtocols != null && sslProtocols.length > 0) {
				((SSLSocket)socket).setEnabledProtocols(GlobalContext.getConfig().getSSLProtocols());
			}
			logger.info("*** Connected to server.");
		}
		catch (IOException e) {
			// Something failed during connecting, call reconnect() to try another server
            logger.warn("Connection to {}:{} failed, retrying or trying next server if one is available", _server, _port);
			reconnect();
			return;
		}

		_inetAddress = socket.getLocalAddress();

		InputStreamReader inputStreamReader = null;
		OutputStreamWriter outputStreamWriter = null;
		inputStreamReader = new InputStreamReader(socket.getInputStream(), getEncoding());
		outputStreamWriter = new OutputStreamWriter(socket.getOutputStream(), getEncoding());

		BufferedReader breader = new BufferedReader(inputStreamReader);
		BufferedWriter bwriter = new BufferedWriter(outputStreamWriter);

		// Attempt to join the server.
		if (_password != null && !_password.equals("")) {
			OutputThread.sendRawLine(this, bwriter, "PASS " + _password);
		}
		String nick = _config.getNick();
		OutputThread.sendRawLine(this, bwriter, "NICK " + nick);
		OutputThread.sendRawLine(this, bwriter, "USER " + _config.getUser() + " 8 * :" + _config.getName());

		_inputThread = new InputThread(this, socket, breader, bwriter);

		// Read stuff back from the server to see if we connected.
		String line = null;
		int tries = 1;
		while ((line = breader.readLine()) != null) {

			this.handleLine(line);

			int firstSpace = line.indexOf(" ");
			int secondSpace = line.indexOf(" ", firstSpace + 1);
			if (secondSpace >= 0) {
				String code = line.substring(firstSpace + 1, secondSpace);

				if (code.equals("004")) {
					// We're connected to the server.
					break;
				}
				else if (code.equals("433")) {
					if (_config.getAutoNick()) {
						tries++;
						nick = _config.getNick() + tries;
						OutputThread.sendRawLine(this, bwriter, "NICK " + nick);
					}
					else {
						socket.close();
						_inputThread = null;
						throw new NickAlreadyInUseException(line);
					}
				}
				else if (code.equals("439")) {
					// seems to be sent from some servers to trick fake clients into disconnecting
					logger.info("Received response code 439 from server.  Ignoring as this shouldn't mean anything as it is sometimes used to trap bots and spammers.");
				}
				else if (code.startsWith("5") || code.startsWith("4")) {
					socket.close();
					_inputThread = null;
					throw new IrcException("Could not log into the IRC server: " + line);
				}
			}
			this.setNick(nick);

		}

		// Find what the server considers our hostmask to be
		OutputThread.sendRawLine(this, bwriter, "WHOIS " + nick);
		line = null;
		String hostMask = "";
		while ((line = breader.readLine()) != null) {
			this.handleLine(line);
			StringTokenizer tokenizer = new StringTokenizer(line);
			// Disregard sender
			tokenizer.nextToken();
			String code = tokenizer.nextToken();
			if (code.equals("311")) {
				// Whois reply received
				// Disregard target
				tokenizer.nextToken();
				String ourNick = tokenizer.nextToken();
				String ourUser = tokenizer.nextToken();
				String ourHost = tokenizer.nextToken();
				hostMask = ourNick + "!" + ourUser + "@" + ourHost;
				break;
			}
			else if (code.equals("318")) {
				// End of whois reached.
				break;
			}
			else if (code.startsWith("4") || code.startsWith("5")) {
				// Error returned from command
                logger.error("Error returned from whois command: {}", line);
				break;
			}
		}
		this.setHostMask(hostMask);

		logger.info("*** Logged onto server.");

		// This makes the socket timeout on read operations after 5 minutes.
		// Maybe in some future version I will let the user change this at runtime.
		socket.setSoTimeout(5 * 60 * 1000);

		// Now start the InputThread to read all other lines from the server.
		_inputThread.start();

		// Now start the outputThread that will be used to send all messages.
		if (_outputThread == null) {
			_outputThread = new OutputThread(this, _outQueue);
			_outputThread.start();
		}

		this.onConnect();

	}


	/**
	 * Reconnects to the IRC server that we were previously connected to.
	 * If necessary, the appropriate port number and password will be used.
	 * This method will throw an IrcException if we have never connected
	 * to an IRC server previously.
	 *
	 * @since PircBot 0.9.9
	 *
	 * @throws IOException if it was not possible to connect to the server.
	 * @throws IrcException if the server would not let us join it.
	 * @throws NickAlreadyInUseException if our nick is already in use on the server.
	 */
	public final synchronized void reconnect() throws IOException, IrcException, NickAlreadyInUseException{
		if (getServer() == null) {
			throw new IrcException("Cannot reconnect to an IRC server because we were never connected to one previously!");
		}
		if (!_userDisconnected) {
			try {
				wait(_config.getConnectDelay());
			}
			catch (InterruptedException e) {
				// do nothing
			}
		}
		connect();
	}


	/**
	 * This method disconnects from the server cleanly by calling the
	 * quitServer() method.  Providing the PircBot was connected to an
	 * IRC server, the onDisconnect() will be called as soon as the
	 * disconnection is made by the server.
	 *
	 * @see #quitServer() quitServer
	 * @see #quitServer(String) quitServer
	 */
	public final synchronized void disconnect() {
		this.quitServer();
	}

	private void joinChannel(ChannelConfig chan) {
		String chanName = chan.getName();
		String chanKey = chan.getChanKey();
		if (chanKey != null && !chanKey.equals("")) {
			this.sendRawLine("JOIN " + chanName + " " + chanKey);
		}
		else {
			this.sendRawLine("JOIN " + chanName);
		}
	}

	private void sendtoListener(String channel, String sender, String hostname, String message) {
		for (ListenerInterface listener : new ArrayList<>(_listeners)) {
			listener.handleInput(this.getBotName(),channel,sender,hostname,message);
		}
	}


	/**
	 * Parts a channel.
	 *
	 * @param channel The name of the channel to leave.
	 */
	public final void partChannel(String channel) {
		this.sendRawLine("PART " + channel);
	}


	/**
	 * Parts a channel, giving a reason.
	 *
	 * @param channel The name of the channel to leave.
	 * @param reason  The reason for parting the channel.
	 */
	public final void partChannel(String channel, String reason) {
		this.sendRawLine("PART " + channel + " :" + reason);
	}


	/**
	 * Quits from the IRC server.
	 * Providing we are actually connected to an IRC server, the
	 * onDisconnect() method will be called as soon as the IRC server
	 * disconnects us.
	 */
	public final void quitServer() {
		this.quitServer("");
	}


	/**
	 * Quits from the IRC server with a reason.
	 * Providing we are actually connected to an IRC server, the
	 * onDisconnect() method will be called as soon as the IRC server
	 * disconnects us.
	 *
	 * @param reason The reason for quitting the server.
	 */
	public final void quitServer(String reason) {
		this.sendRawLine("QUIT :" + reason);
	}


	/**
	 * Sends a raw line to the IRC server as soon as possible, bypassing the
	 * outgoing message queue.
	 *
	 * @param line The raw line to send to the IRC server.
	 */
	public final synchronized void sendRawLine(String line) {
		if (isConnected()) {
			_inputThread.sendRawLine(line);
		}
	}

	/**
	 * Sends a raw line through the outgoing message queue.
	 *
	 * @param line The raw line to send to the IRC server.
	 */
	public final synchronized void sendRawLineViaQueue(String line) {
		if (line == null) {
			throw new NullPointerException("Cannot send null messages to server");
		}
		if (isConnected()) {
			_outQueue.add(line);
		}
	}


	/**
	 * Sends a message to a channel or a private message to a user.  These
	 * messages are added to the outgoing message queue and sent at the
	 * earliest possible opportunity.
	 *  <p>
	 * Some examples: -
	 *  <pre>    // Send the message "Hello!" to the channel #cs.
	 *    sendMessage("#cs", "Hello!");
	 *
	 *    // Send a private message to Paul that says "Hi".
	 *    sendMessage("Paul", "Hi");</pre>
	 *
	 * You may optionally apply colours, boldness, underlining, etc to
	 * the message by using the <code>Colors</code> class.
	 *
	 * @param target The name of the channel or user nick to send to.
	 * @param message The message to send.
	 *
	 * @see Colors
	 */
	public final void sendMessage(String target, String message) {
		_outQueue.add("PRIVMSG " + target + " :" + message);
	}


	/**
	 * Sends an action to the channel or to a user.
	 *
	 * @param target The name of the channel or user nick to send to.
	 * @param action The action to send.
	 *
	 * @see Colors
	 */
	public final void sendAction(String target, String action) {
		sendCTCPCommand(target, "ACTION " + action);
	}


	/**
	 * Sends a notice to the channel or to a user.
	 *
	 * @param target The name of the channel or user nick to send to.
	 * @param notice The notice to send.
	 */
	public final void sendNotice(String target, String notice) {
		_outQueue.add("NOTICE " + target + " :" + notice);
	}


	/**
	 * Sends a CTCP command to a channel or user.  (Client to client protocol).
	 * Examples of such commands are "PING <number>", "FINGER", "VERSION", etc.
	 * For example, if you wish to request the version of a user called "Dave",
	 * then you would call <code>sendCTCPCommand("Dave", "VERSION");</code>.
	 * The type of response to such commands is largely dependant on the target
	 * client software.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param target The name of the channel or user to send the CTCP message to.
	 * @param command The CTCP command to send.
	 */
	public final void sendCTCPCommand(String target, String command) {
		_outQueue.add("PRIVMSG " + target + " :\u0001" + command + "\u0001");
	}


	/**
	 * Attempt to change the current nick (nickname) of the bot when it
	 * is connected to an IRC server.
	 * After confirmation of a successful nick change, the getNick method
	 * will return the new nick.
	 *
	 * @param newNick The new nick to use.
	 */
	public final void changeNick(String newNick) {
		this.sendRawLine("NICK " + newNick);
	}


	/**
	 * Set the mode of a channel.
	 * This method attempts to set the mode of a channel.  This
	 * may require the bot to have operator status on the channel.
	 * For example, if the bot has operator status, we can grant
	 * operator status to "Dave" on the #cs channel
	 * by calling setMode("#cs", "+o Dave");
	 * An alternative way of doing this would be to use the op method.
	 *
	 * @param channel The channel on which to perform the mode change.
	 * @param mode    The new mode to apply to the channel.  This may include
	 *                zero or more arguments if necessary.
	 *
	 * @see #op(String,String) op
	 */
	public final void setMode(String channel, String mode) {
		this.sendRawLine("MODE " + channel + " " + mode);
	}


	/**
	 * Sends an invitation to join a channel.  Some channels can be marked
	 * as "invite-only", so it may be useful to allow a bot to invite people
	 * into it.
	 *
	 * @param nick    The nick of the user to invite
	 * @param channel The channel you are inviting the user to join.
	 *
	 */
	public final void sendInvite(String nick, String channel) {
		this.sendRawLine("INVITE " + nick + " :" + channel);
	}


	/**
	 * Bans a user from a channel.  An example of a valid hostmask is
	 * "*!*compu@*.18hp.net".  This may be used in conjunction with the
	 * kick method to permanently remove a user from a channel.
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel to ban the user from.
	 * @param hostmask A hostmask representing the user we're banning.
	 */
	public final void ban(String channel, String hostmask) {
		this.sendRawLine("MODE " + channel + " +b " + hostmask);
	}


	/**
	 * Unbans a user from a channel.  An example of a valid hostmask is
	 * "*!*compu@*.18hp.net".
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel to unban the user from.
	 * @param hostmask A hostmask representing the user we're unbanning.
	 */
	public final void unBan(String channel, String hostmask) {
		this.sendRawLine("MODE " + channel + " -b " + hostmask);
	}


	/**
	 * Grants operator privilidges to a user on a channel.
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel we're opping the user on.
	 * @param nick The nick of the user we are opping.
	 */
	public final void op(String channel, String nick) {
		this.setMode(channel, "+o " + nick);
	}


	/**
	 * Removes operator privilidges from a user on a channel.
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel we're deopping the user on.
	 * @param nick The nick of the user we are deopping.
	 */
	public final void deOp(String channel, String nick) {
		this.setMode(channel, "-o " + nick);
	}


	/**
	 * Grants voice privilidges to a user on a channel.
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel we're voicing the user on.
	 * @param nick The nick of the user we are voicing.
	 */
	public final void voice(String channel, String nick) {
		this.setMode(channel, "+v " + nick);
	}


	/**
	 * Removes voice privilidges from a user on a channel.
	 * Successful use of this method may require the bot to have operator
	 * status itself.
	 *
	 * @param channel The channel we're devoicing the user on.
	 * @param nick The nick of the user we are devoicing.
	 */
	public final void deVoice(String channel, String nick) {
		this.setMode(channel, "-v " + nick);
	}


	/**
	 * Set the topic for a channel.
	 * This method attempts to set the topic of a channel.  This
	 * may require the bot to have operator status if the topic
	 * is protected.
	 *
	 * @param channel The channel on which to perform the mode change.
	 * @param topic   The new topic for the channel.
	 *
	 */
	public final void setTopic(String channel, String topic) {
		this.sendRawLine("TOPIC " + channel + " :" + topic);
	}


	/**
	 * Kicks a user from a channel.
	 * This method attempts to kick a user from a channel and
	 * may require the bot to have operator status in the channel.
	 *
	 * @param channel The channel to kick the user from.
	 * @param nick    The nick of the user to kick.
	 */
	public final void kick(String channel, String nick) {
		this.kick(channel, nick, "");
	}


	/**
	 * Kicks a user from a channel, giving a reason.
	 * This method attempts to kick a user from a channel and
	 * may require the bot to have operator status in the channel.
	 *
	 * @param channel The channel to kick the user from.
	 * @param nick    The nick of the user to kick.
	 * @param reason  A description of the reason for kicking a user.
	 */
	public final void kick(String channel, String nick, String reason) {
		this.sendRawLine("KICK " + channel + " " + nick + " :" + reason);
	}


	/**
	 * Issues a request for a list of all channels on the IRC server.
	 * When the PircBot receives information for each channel, it will
	 * call the onChannelInfo method, which you will need to override
	 * if you want it to do anything useful.
	 *
	 * @see #onChannelInfo(String,int,String) onChannelInfo
	 */
	public final void listChannels() {
		this.listChannels(null);
	}


	/**
	 * Issues a request for a list of all channels on the IRC server.
	 * When the PircBot receives information for each channel, it will
	 * call the onChannelInfo method, which you will need to override
	 * if you want it to do anything useful.
	 *  <p>
	 * Some IRC servers support certain parameters for LIST requests.
	 * One example is a parameter of ">10" to list only those channels
	 * that have more than 10 users in them.  Whether these parameters
	 * are supported or not will depend on the IRC server software.
	 *
	 * @param parameters The parameters to supply when requesting the
	 *                   list.
	 *
	 * @see #onChannelInfo(String,int,String) onChannelInfo
	 */
	public final void listChannels(String parameters) {
		if (parameters == null) {
			this.sendRawLine("LIST");
		}
		else {
			this.sendRawLine("LIST " + parameters);
		}
	}


	/**
	 * This method handles events when any line of text arrives from the server,
	 * then calling the appropriate method in the PircBot.  This method is
	 * protected and only called by the InputThread for this instance.
	 *  <p>
	 * This method may not be overridden!
	 *
	 * @param line The raw line of text from the server.
	 */
	protected void handleLine(String line) {
		// an empty line from a (non-standard) server causes a failure later on, so skip it
		if (line.length() == 0) {
			return;
		}
		logger.debug(line);

		// Check for server pings.
		if (line.startsWith("PING ")) {
			// Respond to the ping and return immediately.
			this.onServerPing(line.substring(5));
			return;
		}

		String sourceNick = "";
		String sourceLogin = "";
		String sourceHostname = "";

		StringTokenizer tokenizer = new StringTokenizer(line);
		String senderInfo = tokenizer.nextToken();
		String command = tokenizer.nextToken();
		String target = null;

		int exclamation = senderInfo.indexOf("!");
		int at = senderInfo.indexOf("@");
		if (senderInfo.startsWith(":")) {
			if (exclamation > 0 && at > 0 && exclamation < at) {
				sourceNick = senderInfo.substring(1, exclamation);
				sourceLogin = senderInfo.substring(exclamation + 1, at);
				sourceHostname = senderInfo.substring(at + 1);
			}
			else {

				if (tokenizer.hasMoreTokens()) {
					String token = command;

					int code = -1;
					try {
						code = Integer.parseInt(token);
					}
					catch (NumberFormatException e) {
						// Keep the existing value.
						// In case of a server PRIVMSG we sould keep the senderInfo as source nick.
					 	sourceNick = senderInfo;
					}

					if (code != -1) {
						String errorStr = token;
						String response = line.substring(line.indexOf(errorStr, senderInfo.length()) + 4, line.length());
						this.processServerResponse(code, response);
						// Return from the method.
						return;
					}
				}
				else {
					// We don't know what this line means.
					this.onUnknown(line);
					// Return from the method;
					return;
				}

			}
		}

		command = command.toUpperCase();
		if (sourceNick.startsWith(":")) {
			sourceNick = sourceNick.substring(1);
		}
		target = tokenizer.nextToken();

		if (target.startsWith(":")) {
			target = target.substring(1);
		}

		// Check for CTCP requests.
		if (command.equals("PRIVMSG") && line.indexOf(":\u0001") > 0 && line.endsWith("\u0001")) {
			String request = line.substring(line.indexOf(":\u0001") + 2, line.length() - 1);
			if (request.equals("VERSION")) {
				// VERSION request
				this.onVersion(sourceNick, sourceLogin, sourceHostname, target);
			}
			else if (request.startsWith("ACTION ")) {
				// ACTION request
				this.onAction(sourceNick, sourceLogin, sourceHostname, target, request.substring(7));
			}
			else if (request.startsWith("PING ")) {
				// PING request
				this.onPing(sourceNick, sourceLogin, sourceHostname, target, request.substring(5));
			}
			else if (request.equals("TIME")) {
				// TIME request
				this.onTime(sourceNick, sourceLogin, sourceHostname, target);
			}
			else if (request.equals("FINGER")) {
				// FINGER request
				this.onFinger(sourceNick, sourceLogin, sourceHostname, target);
			}
			else {
				// An unknown CTCP message - ignore it.
				this.onUnknown(line);
			}
		}
		else if (command.equals("PRIVMSG") && _channelPrefixes.indexOf(target.charAt(0)) >= 0) {
			// This is a normal message to a channel.
			this.onMessage(target, sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		}
		else if (command.equals("PRIVMSG")) {
			// This is a private message to us.
			this.onPrivateMessage(sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		}
		else if (command.equals("JOIN")) {
			// Someone is joining a channel.
			String channel = target;
			this.addUser(channel, new IrcUser("", sourceNick));
			this.onJoin(channel, sourceNick, sourceLogin, sourceHostname);
		}
		else if (command.equals("PART")) {
			// Someone is parting from a channel.
			this.removeUser(target, sourceNick);
			if (sourceNick.equals(this.getNick())) {
				this.removeChannel(target);
			}
			this.onPart(target, sourceNick, sourceLogin, sourceHostname);
		}
		else if (command.equals("NICK")) {
			// Somebody is changing their nick.
			String newNick = target;
			this.renameUser(sourceNick, newNick);
			if (sourceNick.equals(this.getNick())) {
				// Update our nick if it was us that changed nick.
				this.setNick(newNick);
			}
			this.onNickChange(sourceNick, sourceLogin, sourceHostname, newNick);
		}
		else if (command.equals("NOTICE")) {
			// Someone is sending a notice.
			this.onNotice(sourceNick, sourceLogin, sourceHostname, target, line.substring(line.indexOf(" :") + 2));
		}
		else if (command.equals("QUIT")) {
			// Someone has quit from the IRC server.
			if (sourceNick.equals(this.getNick())) {
				this.removeAllChannels();
			}
			else {
				this.removeUser(sourceNick);
			}
			this.onQuit(sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		}
		else if (command.equals("KICK")) {
			// Somebody has been kicked from a channel.
			String recipient = tokenizer.nextToken();
			if (recipient.equals(this.getNick())) {
				this.removeChannel(target);
			}
			this.removeUser(target, recipient);
			this.onKick(target, sourceNick, sourceLogin, sourceHostname, recipient, line.substring(line.indexOf(" :") + 2));
		}
		else if (command.equals("MODE")) {
			// Somebody is changing the mode on a channel or user.
			String mode = line.substring(line.indexOf(target, 2) + target.length() + 1);
			if (mode.startsWith(":")) {
				mode = mode.substring(1);
			}
			this.processMode(target, sourceNick, sourceLogin, sourceHostname, mode);
		}
		else if (command.equals("TOPIC")) {
			// Someone is changing the topic.
			this.onTopic(target, line.substring(line.indexOf(" :") + 2), sourceNick, System.currentTimeMillis(), true);
		}
		else if (command.equals("INVITE")) {
			// Somebody is inviting somebody else into a channel.
			this.onInvite(target, sourceNick, sourceLogin, sourceHostname, line.substring(line.indexOf(" :") + 2));
		}
		else {
			// If we reach this point, then we've found something that the PircBot
			// Doesn't currently deal with.
			this.onUnknown(line);
		}

	}


	/**
	 * This method is called once the PircBot has successfully connected to
	 * the IRC server.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.6
	 */
	protected void onConnect() {
		// Do any commands requested on connect
		for (String command : _config.getConnectCommands()) {
			sendRawLine(command);
		}

		// Check if nickserv is wanted
		if (_config.getNickservEnabled()) {
			doNickservCommands();
		}

		// Set any user mode changes asked for
		if (_config.getUserModes() != null) {
			sendRawLine("MODE " + getNick() + " " + _config.getUserModes());
		}

		slowDown();

		// Check if chanserv is wanted
		if (_config.getChanservEnabled()) {
			doChanservInvites();
		}

		// Join channels
		joinChannels();
	}

	private void slowDown() {
		if (_config.getDelayAfterNickserv() > 0) {
			try {
                logger.debug("Delaying for '{}' milliseconds, Started", _config.getDelayAfterNickserv());
				Thread.sleep(_config.getDelayAfterNickserv());

			} catch (InterruptedException e) {
			}
            logger.debug("Delaying for '{}' milliseconds, Completed", _config.getDelayAfterNickserv());
		}
	}

	private void joinChannels() {
		for (ChannelConfig chan : _config.getChannels()) {
            BlowfishManager cipher = null;
			if (_config.getBlowfishEnabled()) {
				String chanKey = chan.getBlowKey();
				if (chanKey == null || chanKey.equals("")) {
                    logger.error("BlowfishManager is enabled but no BlowfishManager key is set for channel {} ,the bot will not join this channel", chan.getName());
					break;
				}
				cipher = new BlowfishManager(chan.getBlowKey(), chan.getBlowMode());
				_ciphers.put(chan.getName(), cipher);

			}
			_writers.put(chan.getName(),new OutputWriter(this,chan.getName(),cipher));
			// Check whether we are in the channel, if not then join
			if (!_channels.containsKey(chan.getName())) {
				joinChannel(chan);
			}
		}
	}


	/**
	 * This method carries out the actions to be performed when the PircBot
	 * gets disconnected.  This may happen if the PircBot quits from the
	 * server, or if the connection is unexpectedly lost.
	 *  <p>
	 * Disconnection from the IRC server is detected immediately if either
	 * we or the server close the connection normally. If the connection to
	 * the server is lost, but neither we nor the server have explicitly closed
	 * the connection, then it may take a few minutes to detect (this is
	 * commonly referred to as a "ping timeout").
	 *  <p>
	 * If you wish to get your IRC bot to automatically rejoin a server after
	 * the connection has been lost, then this is probably the ideal method to
	 * override to implement such functionality.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 */
	protected void onDisconnect() {
        if (_outputThread != null) {
         	_outputThread.interrupt();
        }
		try {
			if (!_isTerminated && !_userDisconnected) {
				reconnect();
			}
		} catch (Exception e) {
			logger.warn("Error connecting to server",e);
		}
	}


	/**
	 * This method is called by the PircBot when a numeric response
	 * is received from the IRC server.  We use this method to
	 * allow PircBot to process various responses from the server
	 * before then passing them on to the onServerResponse method.
	 *  <p>
	 * Note that this method is private and should not appear in any
	 * of the javadoc generated documenation.
	 *
	 * @param code The three-digit numerical code for the response.
	 * @param response The full response from the IRC server.
	 */
	private final void processServerResponse(int code, String response) {

		if (code == RPL_LIST) {
			// This is a bit of information about a channel.
			int firstSpace = response.indexOf(' ');
			int secondSpace = response.indexOf(' ', firstSpace + 1);
			int thirdSpace = response.indexOf(' ', secondSpace + 1);
			int colon = response.indexOf(':');
			String channel = response.substring(firstSpace + 1, secondSpace);
			int userCount = 0;
			try {
				userCount = Integer.parseInt(response.substring(secondSpace + 1, thirdSpace));
			}
			catch (NumberFormatException e) {
				// Stick with the value of zero.
			}
			String topic = response.substring(colon + 1);
			this.onChannelInfo(channel, userCount, topic);
		}
		else if (code == RPL_TOPIC) {
			// This is topic information about a channel we've just joined.
			int firstSpace = response.indexOf(' ');
			int secondSpace = response.indexOf(' ', firstSpace + 1);
			int colon = response.indexOf(':');
			String channel = response.substring(firstSpace + 1, secondSpace);
			String topic = response.substring(colon + 1);

			_topics.put(channel, topic);
		}
		else if (code == RPL_TOPICINFO) {
			StringTokenizer tokenizer = new StringTokenizer(response);
			tokenizer.nextToken();
			String channel = tokenizer.nextToken();
			String setBy = tokenizer.nextToken();
			long date = 0;
			try {
				date = Long.parseLong(tokenizer.nextToken()) * 1000;
			}
			catch (NumberFormatException e) {
				// Stick with the default value of zero.
			}

			String topic = _topics.get(channel);
			_topics.remove(channel);

			this.onTopic(channel, topic, setBy, date, false);
		}
		else if (code == RPL_NAMREPLY) {
			// This is a list of nicks in a channel that we've just joined.
			int channelEndIndex = response.indexOf(" :");
			String channel = response.substring(response.lastIndexOf(' ', channelEndIndex - 1) + 1, channelEndIndex);

			StringTokenizer tokenizer = new StringTokenizer(response.substring(response.indexOf(" :") + 2));
			while (tokenizer.hasMoreTokens()) {
				String nick = tokenizer.nextToken();
				StringBuilder prefixBuilder = new StringBuilder();
				while (nick.length() > 0) {
					char first = nick.charAt(0);
					if (first >= 0x41 && first <= 0x7D) {
						break;
					}
					prefixBuilder.append(first);
					nick = nick.substring(1);
				}
				this.addUser(channel, new IrcUser(prefixBuilder.toString(), nick));
			}
		}
		else if (code == RPL_ENDOFNAMES) {
			// This is the end of a NAMES list, so we know that we've got
			// the full list of users in the channel that we just joined.
			String channel = response.substring(response.indexOf(' ') + 1, response.indexOf(" :"));
			IrcUser[] users = this.getUsers(channel);
			this.onUserList(channel, users);
		} else if (code == RPL_BOUNCE) {
			int start = response.indexOf("PREFIX")+7;
			// if this is not found, then the defaults set in the constructor are used
			if (start > 7) {
				// grab from after the equals sign until the next variable
				int end = response.indexOf(" ", start);
				if (end < -1) {  // PREFIX could be at the end
					end = response.length();
				}
				String prefixSegment = response.substring(start, end).trim();
				int firstBracket = prefixSegment.indexOf("(") + 1;
				// carry on processing if found, otherwise the defaults set in the constructor will be used
				if (firstBracket >= 0) {
					int secondBracket = prefixSegment.lastIndexOf(")");
					String modeLetters = prefixSegment.substring(firstBracket, secondBracket);
					String modeSymbols = prefixSegment.substring(secondBracket+1);
					if (modeLetters.length() == modeSymbols.length()) {  // just to make sure nothing funny is going on
						// recreate the _userPrefixes table with the server specific info
						_userPrefixes = new HashMap<>();
						_userPrefixOrder = "";
						for (int x=0; x < modeLetters.length(); x++) {
							_userPrefixes.put(modeLetters.charAt(x) +"", modeSymbols.charAt(x) +"");
							_userPrefixOrder = _userPrefixOrder + modeSymbols.charAt(x);
						}
					}
				}
			}
		}
		this.onServerResponse(code, response);
	}


	/**
	 * This method is called when we receive a numeric response from the
	 * IRC server.
	 *  <p>
	 * Numerics in the range from 001 to 099 are used for client-server
	 * connections only and should never travel between servers.  Replies
	 * generated in response to commands are found in the range from 200
	 * to 399.  Error replies are found in the range from 400 to 599.
	 *  <p>
	 * For example, we can use this method to discover the topic of a
	 * channel when we join it.  If we join the channel #test which
	 * has a topic of &quot;I am King of Test&quot; then the response
	 * will be &quot;<code>PircBot #test :I Am King of Test</code>&quot;
	 * with a code of 332 to signify that this is a topic.
	 * (This is just an example - note that overriding the
	 * <code>onTopic</code> method is an easier way of finding the
	 * topic for a channel). Check the IRC RFC for the full list of other
	 * command response codes.
	 *  <p>
	 * PircBot implements the interface ReplyConstants, which contains
	 * contstants that you may find useful here.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param code The three-digit numerical code for the response.
	 * @param response The full response from the IRC server.
	 *
	 * @see ReplyConstants
	 */
	protected void onServerResponse(int code, String response) {
		if(code == RPL_WHOISUSER) {
			//parse the whois for registering user
			String[] reply = response.split(" ");
			String nick = reply[1];
			String ident = nick + "!" + reply[2] + "@" + reply[3];
			if(_users.containsKey(nick)) {
				_users.get(nick).setIdent(ident);
			}
		}
	}


	/**
	 * This method is called when we receive a user list from the server
	 * after joining a channel.
	 *  <p>
	 * Shortly after joining a channel, the IRC server sends a list of all
	 * users in that channel. The PircBot collects this information and
	 * calls this method as soon as it has the full list.
	 *  <p>
	 * To obtain the nick of each user in the channel, call the getNick()
	 * method on each User object in the array.
	 *  <p>
	 * At a later time, you may call the getUsers method to obtain an
	 * up to date list of the users in the channel.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 1.0.0
	 *
	 * @param channel The name of the channel.
	 * @param users An array of User objects belonging to this channel.
	 *
	 * @see IrcUser
	 */
	protected void onUserList(String channel, IrcUser[] users) {}


	/**
	 * This method is called whenever a message is sent to a channel.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel to which the message was sent.
	 * @param sender The nick of the person who sent the message.
	 * @param login The login of the person who sent the message.
	 * @param hostname The hostname of the person who sent the message.
	 * @param message The actual message sent to the channel.
	 */
	protected void onMessage(String channel, String sender, String login, String hostname, String message) {
		if (_config.getBlowfishEnabled()) {
			if (message.startsWith("+OK ") || message.startsWith("mcps ")) {
				BlowfishManager chanCipher = _ciphers.get(channel);
				if (chanCipher == null) {
                    logger.error("Received encrypted message in channel {} but no Blowfish key is set for the channel!", channel);
					return;
				}
				message = _ciphers.get(channel).decrypt(message);
                logger.debug("Decrypted message: {}", message);
			} else {
				// means we got an unencrypted line from a chan that should be encrypted
				if (_config.getBlowfishPunish()) {
					punishUnencryptedUser(sender,sender+"!"+login+"@"+hostname);
				}
				return;
			}
		}
		// Check if the message is a command we should respond to
		if (message.startsWith(_config.getCommandTrigger())) {
			handleCommand(channel, sender, sender+"!"+login+"@"+hostname, message, true);
		}
		
		sendtoListener(channel,sender,sender+"!"+login+"@"+hostname, message);
	}

	/**
	 * This method is called whenever a private message is sent to the PircBot.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param sender The nick of the person who sent the private message.
	 * @param login The login of the person who sent the private message.
	 * @param hostname The hostname of the person who sent the private message.
	 * @param message The actual message.
	 */
	protected void onPrivateMessage(String sender, String login, String hostname, String message) {
		if (_config.getBlowfishEnabled()) {
			if (message.startsWith("+OK ") || message.startsWith("mcps ")) {
				BlowfishManager cipher = getUserDetails(sender, sender+"!"+login+"@"+hostname).getBlowCipher();
				if (cipher == null) {
					/* We don't have a Blowfish key for this user already, there either
					 * isn't one in the user DB or we can't identify the user, if DH1080
					 * is enabled a session has been initiated but it doesn't help
					 * with this message so just ignore it
					 */
					return;
				}
				message = cipher.decrypt(message);
			} else {
				if(_users.containsKey(sender)) {
					StringBuilder reply = new StringBuilder("Use site command ");
					if (_config.getDH1080Enabled()) {
						reply.append("or DH1080 key-exchange ");
					}
					reply.append("to set a BlowfishManager key before sending a private message");
					sendMessage(sender, reply.toString());
				}
				// means we got an unencrypted line from a user that should be encrypted
				if (_config.getBlowfishPunish()) {
					punishUnencryptedUser(sender,sender+"!"+login+"@"+hostname);
				}
				return;
			}
		}
		// Check if the message is a command we should respond to
		if (message.startsWith(_config.getCommandTrigger())) {
			handleCommand(null, sender, sender+"!"+login+"@"+hostname, message, false);
		}

		sendtoListener(null,sender,sender+"!"+login+"@"+hostname, message);
	}


	/**
	 * This method is called whenever an ACTION is sent from a user.  E.g.
	 * such events generated by typing "/me goes shopping" in most IRC clients.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param sender The nick of the user that sent the action.
	 * @param login The login of the user that sent the action.
	 * @param hostname The hostname of the user that sent the action.
	 * @param target The target of the action, be it a channel or our nick.
	 * @param action The action carried out by the user.
	 */
	protected void onAction(String sender, String login, String hostname, String target, String action) {}


	/**
	 * This method is called whenever we receive a notice.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param sourceNick The nick of the user that sent the notice.
	 * @param sourceLogin The login of the user that sent the notice.
	 * @param sourceHostname The hostname of the user that sent the notice.
	 * @param target The target of the notice, be it our nick or a channel name.
	 * @param notice The notice message.
	 */
	protected void onNotice(String sourceNick, String sourceLogin, String sourceHostname, String target, String notice) {
		DH1080 exchange = null;
		String blowfishMode = BlowfishManager.CBC; //default blowfish on CBC
		if (notice.startsWith("DH1080_INIT") && _config.getDH1080Enabled() && _config.getBlowfishEnabled()) {
			StringTokenizer tokenizeMessage = new StringTokenizer(notice.trim().substring(12));
			notice = tokenizeMessage.nextToken();
			if(tokenizeMessage.hasMoreTokens()) {
				blowfishMode = tokenizeMessage.nextToken();
			}
			exchange = new DH1080();
			// Send our public key back to the sender
			sendNotice(sourceNick, "DH1080_FINISH "+exchange.getPublicKey()+" "+blowfishMode);
		} else if (notice.startsWith("DH1080_FINISH") && _config.getDH1080Enabled() && _config.getBlowfishEnabled()) {
			notice = notice.trim().substring(14);
			exchange = _dh1080.get(sourceNick);
			// For security reasons remove the DH1080 object from the map as we no longer need it
			_dh1080.remove(sourceNick);
		}
		if (_config.getDH1080Enabled() && _config.getBlowfishEnabled()) {
			if (exchange == null) {
				// Somehow we got a response to a DH1080 session we didn't initiate, ignore it
			} else {
				String secretKey = exchange.getSharedSecret(notice);
				getUserDetails(sourceNick,sourceNick+"!"+sourceLogin+"@"+sourceHostname)
						.setBlowCipher(secretKey, blowfishMode);
			}
		}
	}


	/**
	 * This method is called whenever someone (possibly us) joins a channel
	 * which we are on.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel which somebody joined.
	 * @param sender The nick of the user who joined the channel.
	 * @param login The login of the user who joined the channel.
	 * @param hostname The hostname of the user who joined the channel.
	 */
	protected void onJoin(String channel, String sender, String login, String hostname) {}


	/**
	 * This method is called whenever someone (possibly us) parts a channel
	 * which we are on.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel which somebody parted from.
	 * @param sender The nick of the user who parted from the channel.
	 * @param login The login of the user who parted from the channel.
	 * @param hostname The hostname of the user who parted from the channel.
	 */
	protected void onPart(String channel, String sender, String login, String hostname) {
		// Check whether the parting user is still in another chan with us, if not tidyup
		if (!isCommonChannel(sender)) {
			_users.remove(sender);
		}
	}


	/**
	 * This method is called whenever someone (possibly us) changes nick on any
	 * of the channels that we are on.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param oldNick The old nick.
	 * @param login The login of the user.
	 * @param hostname The hostname of the user.
	 * @param newNick The new nick.
	 */
	protected void onNickChange(String oldNick, String login, String hostname, String newNick) {
		UserDetails user = _users.remove(oldNick);
		if (user != null) {
			user.setNick(newNick);
			_users.put(newNick, user);
		}
	}


	/**
	 * This method is called whenever someone (possibly us) is kicked from
	 * any of the channels that we are in.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel from which the recipient was kicked.
	 * @param kickerNick The nick of the user who performed the kick.
	 * @param kickerLogin The login of the user who performed the kick.
	 * @param kickerHostname The hostname of the user who performed the kick.
	 * @param recipientNick The unfortunate recipient of the kick.
	 * @param reason The reason given by the user who performed the kick.
	 */
	protected void onKick(String channel, String kickerNick, String kickerLogin, String kickerHostname, String recipientNick, String reason) {
		// Check whether the kicked user is still in another chan with us, if not tidyup
		if (!isCommonChannel(recipientNick)) {
			_users.remove(recipientNick);
		}
	}


	/**
	 * This method is called whenever someone (possibly us) quits from the
	 * server.  We will only observe this if the user was in one of the
	 * channels to which we are connected.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param sourceNick The nick of the user that quit from the server.
	 * @param sourceLogin The login of the user that quit from the server.
	 * @param sourceHostname The hostname of the user that quit from the server.
	 * @param reason The reason given for quitting the server.
	 */
	protected void onQuit(String sourceNick, String sourceLogin, String sourceHostname, String reason) {
		// Remove userdetails if we have them
		_users.remove(sourceNick);
	}


	/**
	 * This method is called whenever a user sets the topic, or when
	 * PircBot joins a new channel and discovers its topic.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel that the topic belongs to.
	 * @param topic The topic for the channel.
	 * @param setBy The nick of the user that set the topic.
	 * @param date When the topic was set (milliseconds since the epoch).
	 * @param changed True if the topic has just been changed, false if
	 *                the topic was already there.
	 *
	 */
	protected void onTopic(String channel, String topic, String setBy, long date, boolean changed) {}


	/**
	 * After calling the listChannels() method in PircBot, the server
	 * will start to send us information about each channel on the
	 * server.  You may override this method in order to receive the
	 * information about each channel as soon as it is received.
	 *  <p>
	 * Note that certain channels, such as those marked as hidden,
	 * may not appear in channel listings.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The name of the channel.
	 * @param userCount The number of users visible in this channel.
	 * @param topic The topic for this channel.
	 *
	 * @see #listChannels() listChannels
	 */
	protected void onChannelInfo(String channel, int userCount, String topic) {}


	/**
	 * Called when the mode of a channel is set.  We process this in
	 * order to call the appropriate onOp, onDeop, etc method before
	 * finally calling the override-able onMode method.
	 *  <p>
	 * Note that this method is private and is not intended to appear
	 * in the javadoc generated documentation.
	 *
	 * @param target The channel or nick that the mode operation applies to.
	 * @param sourceNick The nick of the user that set the mode.
	 * @param sourceLogin The login of the user that set the mode.
	 * @param sourceHostname The hostname of the user that set the mode.
	 * @param mode  The mode that has been set.
	 */
	private final void processMode(String target, String sourceNick, String sourceLogin, String sourceHostname, String mode) {

		if (_channelPrefixes.indexOf(target.charAt(0)) >= 0) {
			// The mode of a channel is being changed.
			String channel = target;
			StringTokenizer tok = new StringTokenizer(mode);
			String[] params = new String[tok.countTokens()];

			int t = 0;
			while (tok.hasMoreTokens()) {
				params[t] = tok.nextToken();
				t++;
			}

			char pn = ' ';
			int p = 1;

			// All of this is very large and ugly, but it's the only way of providing
			// what the users want :-/
			for (int i = 0; i < params[0].length(); i++) {
				char atPos = params[0].charAt(i);

				if (atPos == '+' || atPos == '-') {
					pn = atPos;
				} else if (_userPrefixes.containsKey(atPos + "")) {
					this.updateUser(channel, pn, _userPrefixes.get(atPos + ""), params[p]);
					// now deal with the known(standard) user modes
					if (atPos == 'o') {
						if (pn == '+') {
							onOp(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
						} else {
							onDeop(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
						}
					} else if (atPos == 'v') {
						if (pn == '+') {
							onVoice(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
						} else {
							onDeVoice(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
						}
					}
					p++;

				} else if (atPos == 'k') {
					if (pn == '+') {
						onSetChannelKey(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
					} else {
						onRemoveChannelKey(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
					}
					p++;
				} else if (atPos == 'l') {
					if (pn == '+') {
						onSetChannelLimit(channel, sourceNick, sourceLogin, sourceHostname, Integer.parseInt(params[p]));
						p++;
					} else {
						onRemoveChannelLimit(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 'b') {
					if (pn == '+') {
						onSetChannelBan(channel, sourceNick, sourceLogin, sourceHostname,params[p]);
					} else {
						onRemoveChannelBan(channel, sourceNick, sourceLogin, sourceHostname, params[p]);
					}
					p++;
				} else if (atPos == 't') {
					if (pn == '+') {
						onSetTopicProtection(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemoveTopicProtection(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 'n') {
					if (pn == '+') {
						onSetNoExternalMessages(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemoveNoExternalMessages(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 'i') {
					if (pn == '+') {
						onSetInviteOnly(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemoveInviteOnly(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 'm') {
					if (pn == '+') {
						onSetModerated(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemoveModerated(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 'p') {
					if (pn == '+') {
						onSetPrivate(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemovePrivate(channel, sourceNick, sourceLogin, sourceHostname);
					}
				} else if (atPos == 's') {
					if (pn == '+') {
						onSetSecret(channel, sourceNick, sourceLogin, sourceHostname);
					} else {
						onRemoveSecret(channel, sourceNick, sourceLogin, sourceHostname);
					}
				}
			}

			this.onMode(channel, sourceNick, sourceLogin, sourceHostname, mode);
		} else {
			// The mode of a user is being changed.
			String nick = target;
			this.onUserMode(nick, sourceNick, sourceLogin, sourceHostname, mode);
		}
	}


	/**
	 * Called when the mode of a channel is set.
	 *  <p>
	 * You may find it more convenient to decode the meaning of the mode
	 * string by overriding the onOp, onDeOp, onVoice, onDeVoice,
	 * onChannelKey, onDeChannelKey, onChannelLimit, onDeChannelLimit,
	 * onChannelBan or onDeChannelBan methods as appropriate.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param channel The channel that the mode operation applies to.
	 * @param sourceNick The nick of the user that set the mode.
	 * @param sourceLogin The login of the user that set the mode.
	 * @param sourceHostname The hostname of the user that set the mode.
	 * @param mode The mode that has been set.
	 *
	 */
	protected void onMode(String channel, String sourceNick, String sourceLogin, String sourceHostname, String mode) {}


	/**
	 * Called when the mode of a user is set.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 1.2.0
	 *
	 * @param targetNick The nick that the mode operation applies to.
	 * @param sourceNick The nick of the user that set the mode.
	 * @param sourceLogin The login of the user that set the mode.
	 * @param sourceHostname The hostname of the user that set the mode.
	 * @param mode The mode that has been set.
	 *
	 */
	protected void onUserMode(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String mode) {}



	/**
	 * Called when a user (possibly us) gets granted operator status for a channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param recipient The nick of the user that got 'opped'.
	 */
	protected void onOp(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {}


	/**
	 * Called when a user (possibly us) gets operator status taken away.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param recipient The nick of the user that got 'deopped'.
	 */
	protected void onDeop(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {}


	/**
	 * Called when a user (possibly us) gets voice status granted in a channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param recipient The nick of the user that got 'voiced'.
	 */
	protected void onVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {}


	/**
	 * Called when a user (possibly us) gets voice status removed.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param recipient The nick of the user that got 'devoiced'.
	 */
	protected void onDeVoice(String channel, String sourceNick, String sourceLogin, String sourceHostname, String recipient) {}


	/**
	 * Called when a channel key is set.  When the channel key has been set,
	 * other users may only join that channel if they know the key.  Channel keys
	 * are sometimes referred to as passwords.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param key The new key for the channel.
	 */
	protected void onSetChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key) {}


	/**
	 * Called when a channel key is removed.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param key The key that was in use before the channel key was removed.
	 */
	protected void onRemoveChannelKey(String channel, String sourceNick, String sourceLogin, String sourceHostname, String key) {}


	/**
	 * Called when a user limit is set for a channel.  The number of users in
	 * the channel cannot exceed this limit.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param limit The maximum number of users that may be in this channel at the same time.
	 */
	protected void onSetChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname, int limit) {}


	/**
	 * Called when the user limit is removed for a channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveChannelLimit(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a user (possibly us) gets banned from a channel.  Being
	 * banned from a channel prevents any user with a matching hostmask from
	 * joining the channel.  For this reason, most bans are usually directly
	 * followed by the user being kicked :-)
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param hostmask The hostmask of the user that has been banned.
	 */
	protected void onSetChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask) {}


	/**
	 * Called when a hostmask ban is removed from a channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 * @param hostmask
	 */
	protected void onRemoveChannelBan(String channel, String sourceNick, String sourceLogin, String sourceHostname, String hostmask) {}


	/**
	 * Called when topic protection is enabled for a channel.  Topic protection
	 * means that only operators in a channel may change the topic.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when topic protection is removed for a channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveTopicProtection(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is set to only allow messages from users that
	 * are in the channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is set to allow messages from any user, even
	 * if they are not actually in the channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveNoExternalMessages(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is set to 'invite only' mode.  A user may only
	 * join the channel if they are invited by someone who is already in the
	 * channel.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel has 'invite only' removed.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveInviteOnly(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is set to 'moderated' mode.  If a channel is
	 * moderated, then only users who have been 'voiced' or 'opped' may speak
	 * or change their nicks.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel has moderated mode removed.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveModerated(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is marked as being in private mode.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetPrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is marked as not being in private mode.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemovePrivate(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel is set to be in 'secret' mode.  Such channels
	 * typically do not appear on a server's channel listing.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onSetSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when a channel has 'secret' mode removed.
	 *  <p>
	 * This is a type of mode change and is also passed to the onMode
	 * method in the PircBot class.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param channel The channel in which the mode change took place.
	 * @param sourceNick The nick of the user that performed the mode change.
	 * @param sourceLogin The login of the user that performed the mode change.
	 * @param sourceHostname The hostname of the user that performed the mode change.
	 */
	protected void onRemoveSecret(String channel, String sourceNick, String sourceLogin, String sourceHostname) {}


	/**
	 * Called when we are invited to a channel by a user.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @since PircBot 0.9.5
	 *
	 * @param targetNick The nick of the user being invited - should be us!
	 * @param sourceNick The nick of the user that sent the invitation.
	 * @param sourceLogin The login of the user that sent the invitation.
	 * @param sourceHostname The hostname of the user that sent the invitation.
	 * @param channel The channel that we're being invited to.
	 */
	protected void onInvite(String targetNick, String sourceNick, String sourceLogin, String sourceHostname, String channel)  {
		if (_config.getChannelAutoJoin()) {
			for (ChannelConfig chan : _config.getChannels()) {
				if (chan.getName().equalsIgnoreCase(channel)) {
					BlowfishManager cipher = null;
					joinChannel(chan);
					if (_config.getBlowfishEnabled()) {
						cipher = new BlowfishManager(chan.getBlowKey(), chan.getBlowMode());
						_ciphers.put(chan.getName(), cipher);
					}
					_writers.put(chan.getName(),new OutputWriter(this,chan.getName(),cipher));
					break;
				}
			}
		}
	}


	/**
	 * This method is called whenever we receive a VERSION request.
	 * This abstract implementation responds with the PircBot's _version string,
	 * so if you override this method, be sure to either mimic its functionality
	 * or to call super.onVersion(...);
	 *
	 * @param sourceNick The nick of the user that sent the VERSION request.
	 * @param sourceLogin The login of the user that sent the VERSION request.
	 * @param sourceHostname The hostname of the user that sent the VERSION request.
	 * @param target The target of the VERSION request, be it our nick or a channel name.
	 */
	protected void onVersion(String sourceNick, String sourceLogin, String sourceHostname, String target) {
		this.sendRawLine("NOTICE " + sourceNick + " :\u0001VERSION " + _ctcpVersion + "\u0001");
	}


	/**
	 * This method is called whenever we receive a PING request from another
	 * user.
	 *  <p>
	 * This abstract implementation responds correctly, so if you override this
	 * method, be sure to either mimic its functionality or to call
	 * super.onPing(...);
	 *
	 * @param sourceNick The nick of the user that sent the PING request.
	 * @param sourceLogin The login of the user that sent the PING request.
	 * @param sourceHostname The hostname of the user that sent the PING request.
	 * @param target The target of the PING request, be it our nick or a channel name.
	 * @param pingValue The value that was supplied as an argument to the PING command.
	 */
	protected void onPing(String sourceNick, String sourceLogin, String sourceHostname, String target, String pingValue) {
		this.sendRawLine("NOTICE " + sourceNick + " :\u0001PING " + pingValue + "\u0001");
	}


	/**
	 * The actions to perform when a PING request comes from the server.
	 *  <p>
	 * This sends back a correct response, so if you override this method,
	 * be sure to either mimic its functionality or to call
	 * super.onServerPing(response);
	 *
	 * @param response The response that should be given back in your PONG.
	 */
	protected void onServerPing(String response) {
		this.sendRawLine("PONG " + response);
	}


	/**
	 * This method is called whenever we receive a TIME request.
	 *  <p>
	 * This abstract implementation responds correctly, so if you override this
	 * method, be sure to either mimic its functionality or to call
	 * super.onTime(...);
	 *
	 * @param sourceNick The nick of the user that sent the TIME request.
	 * @param sourceLogin The login of the user that sent the TIME request.
	 * @param sourceHostname The hostname of the user that sent the TIME request.
	 * @param target The target of the TIME request, be it our nick or a channel name.
	 */
	protected void onTime(String sourceNick, String sourceLogin, String sourceHostname, String target) {
		this.sendRawLine("NOTICE " + sourceNick + " :\u0001TIME " + new Date().toString() + "\u0001");
	}


	/**
	 * This method is called whenever we receive a FINGER request.
	 *  <p>
	 * This abstract implementation responds correctly, so if you override this
	 * method, be sure to either mimic its functionality or to call
	 * super.onFinger(...);
	 *
	 * @param sourceNick The nick of the user that sent the FINGER request.
	 * @param sourceLogin The login of the user that sent the FINGER request.
	 * @param sourceHostname The hostname of the user that sent the FINGER request.
	 * @param target The target of the FINGER request, be it our nick or a channel name.
	 */
	protected void onFinger(String sourceNick, String sourceLogin, String sourceHostname, String target) {
		this.sendRawLine("NOTICE " + sourceNick + " :\u0001FINGER " + _finger + "\u0001");
	}


	/**
	 * This method is called whenever we receive a line from the server that
	 * the PircBot has not been programmed to recognise.
	 *  <p>
	 * The implementation of this method in the PircBot abstract class
	 * performs no actions and may be overridden as required.
	 *
	 * @param line The raw line that was received from the server.
	 */
	protected void onUnknown(String line) {
		// And then there were none :)
	}


	/**
	 * Sets the internal nick of the bot.  This is only to be called by the
	 * PircBot class in response to notification of nick changes that apply
	 * to us.
	 *
	 * @param nick The new nick.
	 */
	private final void setNick(String nick) {
		_nick = nick;
	}


	/**
	 * Returns the current nick of the bot. Note that if you have just changed
	 * your nick, this method will still return the old nick until confirmation
	 * of the nick change is received from the server.
	 *  <p>
	 * The nick returned by this method is maintained only by the PircBot
	 * class and is guaranteed to be correct in the context of the IRC server.
	 *
	 * @since PircBot 1.0.0
	 *
	 * @return The current nick of the bot.
	 */
	public String getNick() {
		return _nick;
	}

	private final void setHostMask(String hostMask) {
		_hostMask = hostMask;
	}

	public String getHostMask() {
		return _hostMask;
	}

	/**
	 * Gets the internal finger message of the PircBot.
	 *
	 * @return The finger message of the PircBot.
	 */
	public final String getFinger() {
		return _finger;
	}


	/**
	 * Returns whether or not the PircBot is currently connected to a server.
	 * The result of this method should only act as a rough guide,
	 * as the result may not be valid by the time you act upon it.
	 *
	 * @return True if and only if the PircBot is currently connected to a server.
	 */
	public final synchronized boolean isConnected() {
		return _inputThread != null && _inputThread.isConnected();
	}


	/**
	 * Returns the number of milliseconds that will be used to separate
	 * consecutive messages to the server from the outgoing message queue.
	 *
	 * @return Number of milliseconds.
	 */
	public final long getMessageDelay() {
		return _config.getMessageDelay();
	}

	/**
	 * Returns the name of the last IRC server the PircBot tried to connect to.
	 * This does not imply that the connection attempt to the server was
	 * successful (we suggest you look at the onConnect method).
	 * A value of null is returned if the PircBot has never tried to connect
	 * to a server.
	 *
	 * @return The name of the last machine we tried to connect to. Returns
	 *         null if no connection attempts have ever been made.
	 */
	public final String getServer() {
		return _server;
	}


	/**
	 * Returns the port number of the last IRC server that the PircBot tried
	 * to connect to.
	 * This does not imply that the connection attempt to the server was
	 * successful (we suggest you look at the onConnect method).
	 * A value of -1 is returned if the PircBot has never tried to connect
	 * to a server.
	 *
	 * @since PircBot 0.9.9
	 *
	 * @return The port number of the last IRC server we connected to.
	 *         Returns -1 if no connection attempts have ever been made.
	 */
	public final int getPort() {
		return _port;
	}


	/**
	 * Returns the last password that we used when connecting to an IRC server.
	 * This does not imply that the connection attempt to the server was
	 * successful (we suggest you look at the onConnect method).
	 * A value of null is returned if the PircBot has never tried to connect
	 * to a server using a password.
	 *
	 * @since PircBot 0.9.9
	 *
	 * @return The last password that we used when connecting to an IRC server.
	 *         Returns null if we have not previously connected using a password.
	 */
	public final String getPassword() {
		return _password;
	}


	/**
	 * A convenient method that accepts an IP address represented as a
	 * long and returns an integer array of size 4 representing the same
	 * IP address.
	 *
	 * @since PircBot 0.9.4
	 *
	 * @param address the long value representing the IP address.
	 *
	 * @return An int[] of size 4.
	 */
	public int[] longToIp(long address) {
		int[] ip = new int[4];
		for (int i = 3; i >= 0; i--) {
			ip[i] = (int) (address % 256);
			address = address / 256;
		}
		return ip;
	}


	/**
	 * A convenient method that accepts an IP address represented by a byte[]
	 * of size 4 and returns this as a long representation of the same IP
	 * address.
	 *
	 * @since PircBot 0.9.4
	 *
	 * @param address the byte[] of size 4 representing the IP address.
	 *
	 * @return a long representation of the IP address.
	 */
	public long ipToLong(byte[] address) {
		if (address.length != 4) {
			throw new IllegalArgumentException("byte array must be of length 4");
		}
		long ipNum = 0;
		long multiplier = 1;
		for (int i = 3; i >= 0; i--) {
			int byteVal = (address[i] + 256) % 256;
			ipNum += byteVal*multiplier;
			multiplier *= 256;
		}
		return ipNum;
	}


	/**
	 * Sets the encoding charset to be used when sending or receiving lines
	 * from the IRC server. Simply a convenience method for {@link #setEncoding(java.nio.charset.Charset) } 
	 * 
	 * @since PircBot 1.0.4
	 * @see #setEncoding(java.nio.charset.Charset) 
	 * @param charset The charset as a string to use
	 * @throws NullPointerException If the charset is null
	 * @throws UnsupportedEncodingException If the passed encoding isn't supported
	 * by the JMV
	 */
	public void setEncoding(String charset) throws UnsupportedEncodingException {
		if (charset == null)
			throw new NullPointerException("Can't set charset to null");
		setEncoding(Charset.forName(charset));
	}


	/**
	 * Sets the encoding charset to be used when sending or receiving lines
	 * from the IRC server.  If set to null, then the platform's default
	 * charset is used.  You should only use this method if you are
	 * trying to send text to an IRC server in a different charset, e.g.
	 * "GB2312" for Chinese encoding.  If a PircBot is currently connected
	 * to a server, then it must reconnect before this change takes effect.
	 *
	 * @param charset The new encoding charset to be used by PircBot.
	 * @throws NullPointerException If the charset is null
	 * @throws UnsupportedEncodingException If the named charset is not
	 * supported.
	 */
	public void setEncoding(Charset charset) {
		if (charset == null)
			throw new NullPointerException("Can't set charset to null");
		_charset = charset;
	}


	/**
	 * Returns the encoding used to send and receive lines from
	 * the IRC server. Never will return null Use the {@link #setEncoding(java.nio.charset.Charset)
	 * method to change the encoding charset.
	 *
	 * @since PircBot 1.0.4
	 *
	 * @return The encoding used to send outgoing messages. Never null
	 */
	public Charset getEncoding() {
		return _charset;
	}

	/**
	 * Returns the InetAddress used by the PircBot.
	 * This can be used to find the I.P. address from which the PircBot is
	 * connected to a server.
	 *
	 * @since PircBot 1.4.4
	 *
	 * @return The current local InetAddress, or null if never connected.
	 */
	public InetAddress getInetAddress() {
		return _inetAddress;
	}

	/**
	 * Returns true if and only if the object being compared is the exact
	 * same instance as this PircBot. This may be useful if you are writing
	 * a multiple server IRC bot that uses more than one instance of PircBot.
	 *
	 * @since PircBot 0.9.9
	 *
	 * @return true if and only if Object o is a PircBot and equal to this.
	 */
	public boolean equals(Object o) {
		// This probably has the same effect as Object.equals, but that may change...
		if (o instanceof SiteBot) {
			SiteBot other = (SiteBot) o;
			return other == this;
		}
		return false;
	}


	/**
	 * Returns the hashCode of this PircBot. This method can be called by hashed
	 * collection classes and is useful for managing multiple instances of
	 * PircBots in such collections.
	 *
	 * @since PircBot 0.9.9
	 *
	 * @return the hash code for this instance of PircBot.
	 */
	public int hashCode() {
		return super.hashCode();
	}


	/**
	 * Returns a String representation of this object.
	 * You may find this useful for debugging purposes, particularly
	 * if you are using more than one PircBot instance to achieve
	 * multiple server connectivity. The format of
	 * this String may change between different versions of PircBot
	 * but is currently something of the form
	 * <code>
	 *   Version{PircBot x.y.z Java IRC Bot - www.jibble.org}
	 *   Connected{true}
	 *   Server{irc.dal.net}
	 *   Port{6667}
	 *   Password{}
	 * </code>
	 *
	 * @since PircBot 0.9.10
	 *
	 * @return a String representation of this object.
	 */
	public String toString() {
		return "Version{" + _version + "}" +
		" Connected{" + isConnected() + "}" +
		" Server{" + _server + "}" +
		" Port{" + _port + "}" +
		" Password{" + _password + "}";
	}


	/**
	 * Returns an array of all users in the specified channel.
	 *  <p>
	 * There are some important things to note about this method:-
	 * <ul>
	 *  <li>This method may not return a full list of users if you call it
	 *      before the complete nick list has arrived from the IRC server.
	 *  </li>
	 *  <li>If you wish to find out which users are in a channel as soon
	 *      as you join it, then you should override the onUserList method
	 *      instead of calling this method, as the onUserList method is only
	 *      called as soon as the full user list has been received.
	 *  </li>
	 *  <li>This method will return immediately, as it does not require any
	 *      interaction with the IRC server.
	 *  </li>
	 *  <li>The bot must be in a channel to be able to know which users are
	 *      in it.
	 *  </li>
	 * </ul>
	 *
	 * @since PircBot 1.0.0
	 *
	 * @param channel The name of the channel to list.
	 *
	 * @return An array of User objects. This array is empty if we are not
	 *         in the channel.
	 *
	 * @see #onUserList(String,IrcUser[]) onUserList
	 */
	public final IrcUser[] getUsers(String channel) {
		channel = channel.toLowerCase();
		IrcUser[] userArray = new IrcUser[0];
		synchronized (_channels) {
			HashMap<IrcUser,IrcUser> users = _channels.get(channel);
			if (users != null) {
				userArray = new IrcUser[users.size()];
				Iterator<IrcUser> iter = users.values().iterator();
				for (int i = 0; i < userArray.length; i++) {
					userArray[i] = iter.next();
				}
			}
		}
		return userArray;
	}


	/**
	 * Returns an array of all channels that we are in.  Note that if you
	 * call this method immediately after joining a new channel, the new
	 * channel may not appear in this array as it is not possible to tell
	 * if the join was successful until a response is received from the
	 * IRC server.
	 *
	 * @since PircBot 1.0.0
	 *
	 * @return A String array containing the names of all channels that we
	 *         are in.
	 */
	public final String[] getChannels() {
		String[] channels;
		synchronized (_channels) {
			channels = _channels.keySet().toArray(new String[0]);
		}
		return channels;
	}


	/**
	 * Disposes of all thread resources used by this PircBot. This may be
	 * useful when writing bots or clients that use multiple servers (and
	 * therefore multiple PircBot instances) or when integrating a PircBot
	 * with an existing program.
	 *  <p>
	 * Each PircBot runs its own threads for dispatching messages from its
	 * outgoing message queue and receiving messages from the server.
	 * Calling dispose() ensures that these threads are
	 * stopped, thus freeing up system resources and allowing the PircBot
	 * object to be garbage collected if there are no other references to
	 * it.
	 *  <p>
	 * Once a PircBot object has been disposed, it should not be used again.
	 * Attempting to use a PircBot that has been disposed may result in
	 * unpredictable behaviour.
	 *
	 * @since 1.2.2
	 */
	public synchronized void dispose() {
		//System.out.println("disposing...");
		_outputThread.interrupt();
		_inputThread.dispose();
	}


	/**
	 * Add a user to the specified channel in our memory.
	 * Overwrite the existing entry if it exists.
	 */
	private final void addUser(String channel, IrcUser user) {
		channel = channel.toLowerCase();
		synchronized (_channels) {
			HashMap<IrcUser,IrcUser> users = _channels.get(channel);
			if (users == null) {
				users = new HashMap<>();
				_channels.put(channel, users);
			}
			users.put(user, user);
		}
	}


	/**
	 * Remove a user from the specified channel in our memory.
	 */
	private final IrcUser removeUser(String channel, String nick) {
		channel = channel.toLowerCase();
		IrcUser user = new IrcUser("", nick);
		synchronized (_channels) {
			HashMap<IrcUser,IrcUser> users = _channels.get(channel);
			if (users != null) {
				return users.remove(user);
			}
		}
		return null;
	}


	/**
	 * Remove a user from all channels in our memory.
	 */
	private final void removeUser(String nick) {
		synchronized (_channels) {
			for (String channel : _channels.keySet()) {
				this.removeUser(channel, nick);
			}
		}
	}


	/**
	 * Rename a user if they appear in any of the channels we know about.
	 */
	private final void renameUser(String oldNick, String newNick) {
		synchronized (_channels) {
			for (String channel : _channels.keySet()) {
				IrcUser user = this.removeUser(channel, oldNick);
				if (user != null) {
					user = new IrcUser(user.getPrefix(), newNick);
					this.addUser(channel, user);
				}
			}
		}
	}


	/**
	 * Removes an entire channel from our memory of users.
	 */
	private final void removeChannel(String channel) {
		channel = channel.toLowerCase();
		synchronized (_channels) {
			_channels.remove(channel);
		}
	}


	/**
	 * Removes all channels from our memory of users.
	 */
	private final void removeAllChannels() {
		_channels = new CaseInsensitiveHashMap<>();
	}


	private final void updateUser(String channel, char giveTake, String userPrefix, String nick) {
		channel = channel.toLowerCase();
		synchronized (_channels) {
			HashMap<IrcUser,IrcUser> users = _channels.get(channel);
			if (users != null) {
				for (IrcUser userObj : users.values()) {
					if (userObj.getNick().equalsIgnoreCase(nick)) {
						if (giveTake == '+') {
							userObj.addPrefix(userPrefix);
						} else {
							if (userObj.hasPrefix(userPrefix)) {
								userObj.removePrefix(userPrefix);
							}
						}
					}
				}
			}
		}
	}

	private void doNickservCommands() {
		if (_config.getNickservRegNew()) {
			if (_config.getNickservGroup()) {
				sendRawLine("PRIVMSG nickserv :group " + _config.getNickservNick() + " " + _config.getNickservPassword());
			}
			else {
				sendRawLine("PRIVMSG nickserv :register " + _config.getNickservPassword() + " " + _config.getNickservEmail());
			}
		}
		sendRawLine("PRIVMSG nickserv :identify " + _config.getNickservPassword());
	}

	private void doChanservInvites() {
		for (String channel : _config.getChanservInvites()) {
			sendRawLine("PRIVMSG chanserv :invite " + channel);
		}
	}

	/**
	 * Handles the load of the IRC Commands.
	 * Firstly, it checks if <code>conf/plugins/irc/irccommands.conf</code> exists, if not it halts the daemon.
	 * After that it read the file and create a list of the existing commands.
	 */
	private void loadCommands() {
		_cmds = GlobalContext.loadCommandConfig("conf/plugins/"+_confDir+"/irccommands.conf");
	}

	/**
	 * The HashMap should look like this:<br><code>
	 * Key -> Value<br>
	 * "nuke" -> Properties Object for nuke<br>
	 * "unnuke" -> Properties Object for unnuke</code>
	 */
	public HashMap<String,Properties> getCommands() {
		return _cmds;
	}

	private void handleCommand(String channel, String sender, String ident, String message, boolean isPublic) {
		// Create a request object for accessing the command and arguments
		IrcRequest request = new IrcRequest(message.substring(_config.getCommandTrigger().length()));

		// Retrieve valid inputs for this command to check if we should act upon it
		boolean proceed = false;
		Properties cmd = _cmds.get(request.getCommand());
		// Check if we know of this command, if not just return
		if (cmd == null) {
			return;
		}
		String inputs = cmd.getProperty("input","");
		StringTokenizer ist = new StringTokenizer(inputs);
		while (ist.hasMoreTokens()) {
			String token = ist.nextToken();
			if (token.equalsIgnoreCase("all")) {
				// Must be valid
				proceed = true;
				break;
			} else if (token.equalsIgnoreCase("public") && isPublic) {
				proceed = true;
				break;
			} else if (token.equalsIgnoreCase("private") && !isPublic) {
				proceed = true;
				break;
			} else if (isPublic && token.equalsIgnoreCase(channel)) {
				proceed = true;
				break;
			}
		}
		if (proceed) {
			// Find what outputs we should be sending the response to
			ArrayList<OutputWriter> cmdOutputs = new ArrayList<>();
			String outputs = cmd.getProperty("output","");
			StringTokenizer ost = new StringTokenizer(outputs);
			while(ost.hasMoreTokens()) {
				String token = ost.nextToken();
				if (token.equalsIgnoreCase("public")) {
					cmdOutputs.addAll(_writers.values());
				} else if (token.equalsIgnoreCase("private")) {
					cmdOutputs.add(getUserDetails(sender,ident).getOutputWriter());
				} else if (token.equalsIgnoreCase("source")) {
					if (isPublic) {
						cmdOutputs.add(_writers.get(channel));
					} else {
						cmdOutputs.add(getUserDetails(sender,ident).getOutputWriter());
					}
				} else if (token.startsWith("#")) {
					if (_writers.containsKey(token)) {
						cmdOutputs.add(_writers.get(token));
					}
				}
			}
			// Check if we found valid outputs and if so proceed
			if (!cmdOutputs.isEmpty()) {
				ServiceCommand service = getUserDetails(sender,ident).getCommandSession(cmdOutputs, channel);
				service.setCommands(_cmds);
				try {
					_pool.execute(new CommandThread(request,service,sender,ident));
				} catch (RejectedExecutionException e) {
					OutputWriter rejectWriter = null;
					if (isPublic) {
						rejectWriter = _writers.get(channel);
					} else {
						rejectWriter = getUserDetails(sender,ident).getOutputWriter();
					}
					rejectWriter.sendMessage("All command threads are busy, please wait and try again later");
				}
			}
		}
	}

	private UserDetails getUserDetails(String nick, String ident) {
		UserDetails user = _users.get(nick);
		if (user == null) {
			user = new UserDetails(nick, ident, this);
			_users.put(nick, user);
		}
		return user;
	}

	protected void initiateDH1080(String nick) {
		if (_config.getDH1080Enabled()) {
			DH1080 exchange = new DH1080();
			sendNotice(nick, "DH1080_INIT "+exchange.getPublicKey());
			// Store the DH1080 object for use when we get their reply
			_dh1080.put(nick, exchange);
		}
	}

	private void punishUnencryptedUser(String nick, String ident) {
		String action = _config.getBlowfishPunishAction();
		if (action.equalsIgnoreCase("ban")) {
			for (Entry<String,HashMap<IrcUser,IrcUser>> entry : _channels.entrySet()) {
				for (IrcUser user : entry.getValue().keySet()) {
					if (user.getNick().equalsIgnoreCase(nick)) {
						ban(entry.getKey(), ident);
						kick(entry.getKey(), nick, _config.getBlowfishPunishReason());
						// Found the user in this chan, no need to check remaining nicks
						break;
					}
				}
			}
		} else if (action.equalsIgnoreCase("kick")) {
			for (Entry<String,HashMap<IrcUser,IrcUser>> entry : _channels.entrySet()) {
				for (IrcUser user : entry.getValue().keySet()) {
					if (user.getNick().equalsIgnoreCase(nick)) {
						kick(entry.getKey(), nick, _config.getBlowfishPunishReason());
						// Found the user in this chan, no need to check remaining nicks
						break;
					}
				}
			}
		}
	}

	private boolean isCommonChannel(String nick) {
		boolean foundUser = false;
		for (Entry<String,HashMap<IrcUser,IrcUser>> entry : _channels.entrySet()) {
			for (IrcUser user : entry.getValue().keySet()) {
				if (user.getNick().equalsIgnoreCase(nick)) {
					foundUser = true;
					// Found the user in this chan, no need to check remaining nicks
					break;
				}
			}
			if (foundUser) {
				// We've found the user, no need to check remaining channels
				break;
			}
		}
		return foundUser;
	}

	public String getBotName() {
		return _name;
	}

	public SiteBotConfig getConfig() {
		return _config;
	}

	private void loadListeners() {
		_listeners = new ArrayList<>();
		try {
			List<ListenerInterface> loadedListeners = CommonPluginUtils.getPluginObjects(this, "org.drftpd.plugins.sitebot", "Listener", "Class");
			for (ListenerInterface listener : loadedListeners) {
				_listeners.add(listener);
                logger.debug("Loading sitebot listener from plugin {}", CommonPluginUtils.getPluginIdForObject(listener));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.sitebot extension point 'Listener', possibly the " + "org.drftpd.plugins.sitebot extension point definition has changed in the plugin.xml",e);
		}
	}

	private void loadAnnouncers(String confDir) {
		try {
			List<AbstractAnnouncer> loadedAnnouncers =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.plugins.sitebot", "Announce", "Class");
			for (AbstractAnnouncer announcer : loadedAnnouncers) {
				announcer.setConfDir(_confDir);
				_announcers.add(announcer);
                logger.debug("Loading sitebot announcer from plugin {}", CommonPluginUtils.getPluginIdForObject(announcer));
				for (String type : announcer.getEventTypes()) {
					_eventTypes.add(type);
				}
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.sitebot extension point 'Announce', possibly the "+
					"org.drftpd.plugins.sitebot extension point definition has changed in the plugin.xml",e);
		}

		// Add the fallback default type to the eventTypes list
		_eventTypes.add("default");
		_announceConfig = new AnnounceConfig(confDir, _eventTypes, this);

		// Initialise the announcers with the config
		for (AbstractAnnouncer announcer : _announcers) {
			announcer.initialise(_announceConfig,_commandManager.getResourceBundle());
		}
	}

	public CaseInsensitiveConcurrentHashMap<String,OutputWriter> getWriters() {
		return _writers;
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
        logger.info("Reloading conf/plugins/{}/irccommands.conf, origin {}", _confDir, event.getOrigin());
		loadCommands();
		_commandManager.initialize(getCommands(), themeDir);
		_config = new SiteBotConfig(GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin(_confDir+"/irc.conf"));
		// Just call joinChannels() , this will join us to any new channels and update details
		// held on any existing ones
		joinChannels();
		// Check whether each channel we are currently in is still listed in the conf, if not then part
		ArrayList<ChannelConfig> chanConfig = _config.getChannels();
		for (String channel: _channels.keySet()) {
			boolean isConf = false;
			for (ChannelConfig confChan : chanConfig) {
				if (confChan.getName().equalsIgnoreCase(channel)) {
					isConf = true;
					break;
				}
			}
			if (!isConf) {
				partChannel(channel,"No longer servicing this channel");
			}
		}
		_announceConfig.reload();

		// Ensure that all announcers pickup any changes to the theme files
		for (AbstractAnnouncer announcer : _announcers) {
			announcer.setResourceBundle(_commandManager.getResourceBundle());
		}

		// Update OutputWriters for users, not required for channels as new ones have
		// been constructed in the joinChannels() call
		for (UserDetails userDets : _users.values()) {
			userDets.getOutputWriter().reload();
		}
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		Set<AbstractAnnouncer> unloadedAnnouncers =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "Announce", event, _announcers);
		if (!unloadedAnnouncers.isEmpty()) {
			boolean typeRemoved = false;
			for (Iterator<AbstractAnnouncer> iter = _announcers.iterator(); iter.hasNext();) {
				AbstractAnnouncer announcer = iter.next();
				if (unloadedAnnouncers.contains(announcer)) {
					for (String type : announcer.getEventTypes()) {
						if (_eventTypes.remove(type)) {
							typeRemoved = true;
						}
					}
					announcer.stop();
                    logger.debug("Unloading sitebot announcer provided by plugin {}", CommonPluginUtils.getPluginIdForObject(announcer));
					iter.remove();
				}
			}
			if (typeRemoved) {
				_announceConfig.reload();
			}
		}

		// Remove unloaded listeneres
		Set<ListenerInterface> unloadedListeners = MasterPluginUtils.getUnloadedExtensionObjects(this, "Listener", event, _listeners);
		if (!unloadedListeners.isEmpty()) {
            _listeners.removeIf(unloadedListeners::contains);
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			boolean typeAdded = false;
			List<AbstractAnnouncer> loadedAnnouncers =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.plugins.sitebot", "Announce", "Class", event);
			for (AbstractAnnouncer announcer : loadedAnnouncers) {
                logger.debug("Loading sitebot announcer provided by plugin {}", CommonPluginUtils.getPluginIdForObject(announcer));
				announcer.setConfDir(_confDir);
				announcer.initialise(_announceConfig,_commandManager.getResourceBundle());
				_announcers.add(announcer);
				for (String type : announcer.getEventTypes()) {
					_eventTypes.add(type);
					typeAdded = true;
				}
			}
			if (typeAdded) {
				_announceConfig.reload();
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.plugins.sitebot extension point 'Announce'"+
					", possibly the org.drftpd.plugins.sitebot extension point definition has changed in the plugin.xml",e);
		}

		// Activate new Loaded Listeners
		try {
			List<ListenerInterface> loadedListeners = MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.plugins.sitebot", "Listener", "Class", event);
			for (ListenerInterface listener : loadedListeners) {
                logger.debug("Loading sitebot announcer provided by plugin {}", CommonPluginUtils.getPluginIdForObject(listener));
				_listeners.add(listener);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.plugins.sitebot extension point 'Listener', possibly the org.drftpd.plugins.sitebot extension point definition has changed in the plugin.xml",e);
		}
	}

	@EventSubscriber
	public void onInviteEvent(InviteEvent event) {
		if (event.getTargetBot().equalsIgnoreCase(_name)) {
			UserDetails userDetails = new UserDetails(event.getIrcNick(), "", this);
			userDetails.setFtpUser(event.getUser().getName());
			_users.put(event.getIrcNick(), userDetails);
			sendRawLineViaQueue("WHOIS " + event.getIrcNick());
		}
	}

	protected void terminate(String reason) {
		for (UserDetails user : _users.values()) {
			for (ServiceCommand command : user.getCommandSessions()) {
				command.abortCommand();
			}
		}
		_isTerminated = true;
		quitServer(reason);
		_pool.shutdown();
		dispose();
	}

	public void setDisconnected(boolean state) {
		_userDisconnected = state;
	}

	class CommandThread implements Runnable {

		private IrcRequest _request;

		private ServiceCommand _service;

		private String _nickname;

		private String _ident;

		private CommandThread(IrcRequest request, ServiceCommand service, String nick, String ident) {
			_request = request;
			_service = service;
			_nickname = nick;
			_ident = ident;
		}

		public void run() {
			UserDetails user = getUserDetails(_nickname,_ident);
			CommandRequestInterface cmdRequest = _commandManager.newRequest( _request.getCommand(),
					_request.getArgument(), new DirectoryHandle("/"), user.getFtpUser(), _service, _cmds.get(_request.getCommand()));
			CommandResponseInterface cmdResponse = _commandManager.execute(cmdRequest);
			if (cmdResponse != null) {
				_service.printOutput(new IrcReply(cmdResponse));
			}
			// Remove this command session from the user now we have completed executing it
			user.removeCommandSession(_service);
		}
	}
}

class CommandThreadFactory implements ThreadFactory {

	public static String getIdleThreadName(long threadId) {
		return "SiteBot Command Handler-"+ threadId + " - Waiting for commands";
	}

	public Thread newThread(Runnable r) {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setName(getIdleThreadName(t.getId()));
		return t;
	}
}
