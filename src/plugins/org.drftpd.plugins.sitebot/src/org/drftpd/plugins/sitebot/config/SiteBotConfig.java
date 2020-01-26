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
package org.drftpd.plugins.sitebot.config;

import org.drftpd.BlindTrustManager;
import org.drftpd.plugins.sitebot.PartialTrustManager;

import javax.net.ssl.X509TrustManager;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Properties;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotConfig {

	private boolean _autoNick;

	private boolean _blowfishEnabled;

	private boolean _blowfishPunish;

	private String _blowfishPunishAction;

	private String _blowfishPunishReason;

	private String _botName;

	private boolean _channelAutoJoin;

	private ArrayList<ChannelConfig> _channels = new ArrayList<>();

	private boolean _chanservEnabled;

	private ArrayList<String> _chanservInvites = new ArrayList<>();

	private String _charset;

	private boolean _commandsBlock = false;

	private int _commandsMax = 1;

	private boolean _commandsQueue = false;

	private String _commandTrigger;

	private ArrayList<String> _connectCommands = new ArrayList<>();

	private long _connectDelay;

	private String _ctcpFinger;

	private String _ctcpVersion;

	private boolean _dh1080Enabled;

	private String _localBindHost;

	private int _maxLineLength;
	
	private long _messageDelay;

	private String _name;

	private String _nick;

	private boolean _nickservEnabled;

	private boolean _nickservJoinGroup;

	private String _nickservRegEmail;

	private boolean _nickservRegNew;

	private String _nickservRegNick;

	private String _nickservRegPassword;

	private LinkedList<ServerConfig> _servers = new LinkedList<>();

	private X509TrustManager _trustManager;

	private String _user;

	private String _userModes;
	
	private long _delayAfterNickserv;

	public SiteBotConfig(Properties cfg) {
		readProperties(cfg);
	}

	private void readProperties(Properties cfg) {
		if (cfg.getProperty("ssl.strictTrust").equalsIgnoreCase("true")) {
			_trustManager = new PartialTrustManager(
					cfg.getProperty("ssl.keystore.password"));
		}
		else {
			_trustManager = new BlindTrustManager();
		}
		for (int i = 1;; i++) {
			String hostName = cfg.getProperty("server."+i+".host");
			if (hostName == null) {
				break;
			}
			int port = Integer.valueOf(cfg.getProperty("server." + i + ".port"));
			String password = cfg.getProperty("server."+i+".password");
			boolean ssl = cfg.getProperty("server."+i+".use_ssl").equalsIgnoreCase("true");
			_servers.add(new ServerConfig(hostName,port,password,ssl,_trustManager));
		}
		_connectDelay = Long.valueOf(cfg.getProperty("connect.delay")) * 1000;
		_messageDelay = Long.valueOf(cfg.getProperty("message.sendDelay"));
		_autoNick = cfg.getProperty("nick.auto").equalsIgnoreCase("true");
		_name = cfg.getProperty("name");
		_nick = cfg.getProperty("nick");
		_user = cfg.getProperty("user");
		_userModes = cfg.getProperty("nick.usermodes");
		for (int i = 1;; i++) {
			String connectCommand = cfg.getProperty("connected.command."+i);
			if (connectCommand == null) {
				break;
			}
			_connectCommands.add(connectCommand);
		}
		_nickservEnabled = cfg.getProperty("services.nickserv.enable").equalsIgnoreCase("true");
		_nickservJoinGroup = cfg.getProperty("services.nickserv.register.joingroup").equalsIgnoreCase("true");
		_nickservRegEmail = cfg.getProperty("services.nickserv.register.email");
		_nickservRegNew = cfg.getProperty("services.nickserv.register.new").equalsIgnoreCase("true");
		_nickservRegNick = cfg.getProperty("services.nickserv.register.nick");
		_nickservRegPassword = cfg.getProperty("services.nickserv.register.password");
		
		_delayAfterNickserv = Long.valueOf(cfg.getProperty("services.nickserv.delayafter", "0"));
		
		_chanservEnabled = cfg.getProperty("services.chanserv.enable").equalsIgnoreCase("true");
		for (int i = 1;; i++) {
			String chanservInvite = cfg.getProperty("services.chanserv.invite."+i);
			if (chanservInvite == null) {
				break;
			}
			_chanservInvites.add(chanservInvite);
		}
		_ctcpFinger = cfg.getProperty("ctcp.finger");
		_ctcpVersion = cfg.getProperty("ctcp.version");
		for (int i = 1;; i++) {
			String chanName = cfg.getProperty("channel."+i);
			if (chanName == null) {
				break;
			}
			String chanKey = cfg.getProperty("channel."+i+".chankey");
			String chanPerms = cfg.getProperty("channel."+i+".perms");
			String blowKey = cfg.getProperty("channel."+i+".blowkey");
			String blowMode = cfg.getProperty("channel."+i+".blowmode", "cbc");
			_channels.add(new ChannelConfig(chanName,blowKey,blowMode,chanKey,chanPerms));
		}
		_commandTrigger = cfg.getProperty("command.trigger");
		_channelAutoJoin = cfg.getProperty("channel.autojoin").equalsIgnoreCase("true");
		_charset = cfg.getProperty("charset");
		_blowfishEnabled = cfg.getProperty("blowfish.enable").equalsIgnoreCase("true");
		_dh1080Enabled = cfg.getProperty("blowfish.dh1080.enable").equalsIgnoreCase("true");
		_blowfishPunish = cfg.getProperty("blowfish.unencrypted.punish").equalsIgnoreCase("true");
		_blowfishPunishAction = cfg.getProperty("blowfish.unencrypted.action");
		_blowfishPunishReason = cfg.getProperty("blowfish.unencrypted.reason");
		_localBindHost = cfg.getProperty("bind.host");
		_botName = cfg.getProperty("bot.name");
		_commandsMax = Integer.parseInt(cfg.getProperty("commands.max"));
		if (cfg.getProperty("commands.full").equalsIgnoreCase("block")) {
			_commandsBlock = true;
		} else if (cfg.getProperty("commands.full").equalsIgnoreCase("queue")) {
			_commandsQueue = true;
		}
		_maxLineLength = Integer.parseInt(cfg.getProperty("line.length.max", "512"));
	}

	public boolean getAutoNick() {
		return _autoNick;
	}

	public boolean getBlowfishEnabled() {
		return _blowfishEnabled;
	}

	public boolean getBlowfishPunish() {
		return _blowfishPunish;
	}

	public String getBlowfishPunishAction() {
		return _blowfishPunishAction;
	}

	public String getBlowfishPunishReason() {
		return _blowfishPunishReason;
	}

	public String getBotName() {
		return _botName;
	}

	public boolean getChannelAutoJoin() {
		return _channelAutoJoin;
	}

	public ArrayList<ChannelConfig> getChannels() {
		return _channels;
	}

	public String getCharset() {
		return _charset;
	}

	public ArrayList<String> getConnectCommands() {
		return _connectCommands;
	}

	public String getCommandTrigger() {
		return _commandTrigger;
	}

	public long getConnectDelay() {
		return _connectDelay;
	}

	public boolean getDH1080Enabled() {
		return _dh1080Enabled;
	}

	public String getLocalBindHost() {
		return _localBindHost;
	}
	
	public int getMaxLineLength() {
		return _maxLineLength;
	}

	public long getMessageDelay() {
		return _messageDelay;
	}
	
	public long getDelayAfterNickserv() {
		return _delayAfterNickserv;
	}

	public String getName() {
		return _name;
	}

	public String getNick() {
		return _nick;
	}

	public ServerConfig getServer() {
		// rotate the next server to the end of the list
		ServerConfig ret = _servers.poll();
		_servers.add(ret);
		return ret;
	}

	public String getUser() {
		return _user;
	}

	public String getUserModes() {
		return _userModes;
	}

	public boolean getNickservEnabled() {
		return _nickservEnabled;
	}

	public boolean getNickservGroup() {
		return _nickservJoinGroup;
	}

	public String getNickservEmail() {
		return _nickservRegEmail;
	}

	public boolean getNickservRegNew() {
		return _nickservRegNew;
	}

	public String getNickservNick() {
		return _nickservRegNick;
	}

	public String getNickservPassword() {
		return _nickservRegPassword;
	}

	public boolean getChanservEnabled() {
		return _chanservEnabled;
	}

	public ArrayList<String> getChanservInvites() {
		return _chanservInvites;
	}

	public String getCTCPFinger() {
		return _ctcpFinger;
	}

	public String getCTCPVersion() {
		return _ctcpVersion;
	}

	public int getCommandsMax() {
		return _commandsMax;
	}

	public boolean getCommandsBlock() {
		return _commandsBlock;
	}

	public boolean getCommandsQueue() {
		return _commandsQueue;
	}
}
