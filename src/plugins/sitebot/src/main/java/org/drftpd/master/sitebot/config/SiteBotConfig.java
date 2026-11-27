/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.sitebot.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.network.BlindTrustManager;
import org.drftpd.master.sitebot.PartialTrustManager;

import javax.net.ssl.X509TrustManager;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Properties;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotConfig {

    private static final Logger logger = LogManager.getLogger(SiteBotConfig.class);

    private boolean _autoNick;

    private boolean _blowfishEnabled;

    private boolean _blowfishEcbFallback;

    private boolean _blowfishPunish;

    private String _blowfishPunishAction;

    private String _blowfishPunishReason;

    private String _botName;

    private boolean _channelAutoJoin;

    private final ArrayList<ChannelConfig> _channels = new ArrayList<>();

    private boolean _chanservEnabled;

    private final ArrayList<String> _chanservInvites = new ArrayList<>();

    private String _charset;

    private boolean _commandsBlock = false;

    private int _commandsMax = 1;

    private boolean _commandsQueue = false;

    private String _commandTrigger;

    private final ArrayList<String> _connectCommands = new ArrayList<>();

    private long _connectDelay;

    private String _ctcpFinger;

    private String _ctcpVersion;

    private boolean _dh1080Enabled;

    private boolean _dh1080EcbEnabled;

    private boolean _dh1080CbcEnabled;

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

    private final LinkedList<ServerConfig> _servers = new LinkedList<>();

    private String _user;

    private String _userModes;

    private long _delayAfterNickserv;

    public SiteBotConfig(Properties cfg) {
        readProperties(cfg);
    }

    private void readProperties(Properties cfg) {
        X509TrustManager trustManager = new BlindTrustManager();
        if (cfg.getProperty("ssl.strictTrust", "false").trim().equalsIgnoreCase("true")) {
            trustManager = new PartialTrustManager(cfg.getProperty("ssl.keystore.password"));
        }
        for (int i = 1; ; i++) {
            String hostName = cfg.getProperty("server." + i + ".host");
            if (hostName == null) {
                break;
            }
            int port = Integer.parseInt(requireProperty(cfg, "server." + i + ".port").trim());
            String password = cfg.getProperty("server." + i + ".password");
            boolean ssl = cfg.getProperty("server." + i + ".use_ssl", "true").trim().equalsIgnoreCase("true");
            String saslUsername = cfg.getProperty("server." + i + ".sasl.username", "").trim();
            String saslPassword = cfg.getProperty("server." + i + ".sasl.password", "");
            boolean saslRequired = cfg.getProperty("server." + i + ".sasl.required", "false").trim().equalsIgnoreCase("true");
            boolean saslAllowPlaintext = cfg.getProperty("server." + i + ".sasl.allow_plaintext", "false").trim().equalsIgnoreCase("true");
            boolean saslAllowInsecureTls = cfg.getProperty("server." + i + ".sasl.allow_insecure_tls", "false").trim().equalsIgnoreCase("true");
            _servers.add(new ServerConfig(hostName, port, password, ssl, trustManager,
                    saslUsername, saslPassword, saslRequired, saslAllowPlaintext, saslAllowInsecureTls));
        }
        _connectDelay = Long.parseLong(cfg.getProperty("connect.delay", "30").trim()) * 1000;
        _messageDelay = Long.parseLong(cfg.getProperty("message.sendDelay", "300").trim());
        _autoNick = cfg.getProperty("nick.auto", "true").trim().equalsIgnoreCase("true");
        _name = cfg.getProperty("name");
        _nick = cfg.getProperty("nick");
        _user = cfg.getProperty("user");
        _userModes = cfg.getProperty("nick.usermodes");
        for (int i = 1; ; i++) {
            String connectCommand = cfg.getProperty("connected.command." + i);
            if (connectCommand == null) {
                break;
            }
            _connectCommands.add(connectCommand);
        }
        _nickservEnabled = cfg.getProperty("services.nickserv.enable", "false").trim().equalsIgnoreCase("true");
        _nickservJoinGroup = cfg.getProperty("services.nickserv.register.joingroup", "false").trim().equalsIgnoreCase("true");
        _nickservRegEmail = cfg.getProperty("services.nickserv.register.email");
        _nickservRegNew = cfg.getProperty("services.nickserv.register.new", "false").trim().equalsIgnoreCase("true");
        _nickservRegNick = cfg.getProperty("services.nickserv.register.nick");
        _nickservRegPassword = cfg.getProperty("services.nickserv.register.password");

        _delayAfterNickserv = Long.parseLong(cfg.getProperty("services.nickserv.delayafter", "0"));

        _chanservEnabled = cfg.getProperty("services.chanserv.enable", "false").trim().equalsIgnoreCase("true");
        for (int i = 1; ; i++) {
            String chanservInvite = cfg.getProperty("services.chanserv.invite." + i);
            if (chanservInvite == null) {
                break;
            }
            _chanservInvites.add(chanservInvite);
        }
        _ctcpFinger = cfg.getProperty("ctcp.finger");
        _ctcpVersion = cfg.getProperty("ctcp.version");
        for (int i = 1; ; i++) {
            String chanName = cfg.getProperty("channel." + i);
            if (chanName == null) {
                break;
            }
            String chanKey = cfg.getProperty("channel." + i + ".chankey");
            String chanPerms = cfg.getProperty("channel." + i + ".perms");
            String blowKey = cfg.getProperty("channel." + i + ".blowkey");
            String blowMode = cfg.getProperty("channel." + i + ".blowmode", "cbc");
            // A key written FiSH style as cbc:<key> is a CBC key, the prefix is not part of it
            if (blowKey != null && blowKey.regionMatches(true, 0, "cbc:", 0, 4)) {
                blowKey = blowKey.substring(4);
                if (!blowMode.equalsIgnoreCase("cbc")) {
                    logger.warn("channel.{}.blowkey starts with cbc:, using CBC instead of blowmode {} for {}",
                            i, blowMode, chanName);
                    blowMode = "cbc";
                }
            }
            _channels.add(new ChannelConfig(chanName, blowKey, blowMode, chanKey, chanPerms));
        }
        _commandTrigger = cfg.getProperty("command.trigger");
        _channelAutoJoin = cfg.getProperty("channel.autojoin", "true").trim().equalsIgnoreCase("true");
        _charset = cfg.getProperty("charset");
        _blowfishEnabled = cfg.getProperty("blowfish.enable", "true").trim().equalsIgnoreCase("true");
        _blowfishEcbFallback = cfg.getProperty("blowfish.ecb.fallback", "false").trim().equalsIgnoreCase("true");
        _dh1080Enabled = cfg.getProperty("blowfish.dh1080.enable", "true").trim().equalsIgnoreCase("true");
        _dh1080EcbEnabled = cfg.getProperty("blowfish.dh1080.ecb", "false").trim().equalsIgnoreCase("true");
        _dh1080CbcEnabled = cfg.getProperty("blowfish.dh1080.cbc", "true").trim().equalsIgnoreCase("true");
        _blowfishPunish = cfg.getProperty("blowfish.unencrypted.punish", "false").trim().equalsIgnoreCase("true");
        _blowfishPunishAction = cfg.getProperty("blowfish.unencrypted.action");
        _blowfishPunishReason = cfg.getProperty("blowfish.unencrypted.reason");
        _localBindHost = cfg.getProperty("bind.host");
        _botName = cfg.getProperty("bot.name");
        _commandsMax = Integer.parseInt(cfg.getProperty("commands.max", "5").trim());
        String commandsFull = cfg.getProperty("commands.full", "queue").trim();
        if (commandsFull.equalsIgnoreCase("block")) {
            _commandsBlock = true;
        } else if (commandsFull.equalsIgnoreCase("queue")) {
            _commandsQueue = true;
        }
        _maxLineLength = Integer.parseInt(cfg.getProperty("line.length.max", "512"));
    }

    private static String requireProperty(Properties cfg, String key) {
        String value = cfg.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required key \"" + key + "\" in irc.conf");
        }
        return value;
    }

    public boolean getAutoNick() {
        return _autoNick;
    }

    public boolean getBlowfishEnabled() {
        return _blowfishEnabled;
    }

    public boolean getBlowfishEcbFallback() {
        return _blowfishEcbFallback;
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

    public boolean getDH1080CBCEnabled() {
        return _dh1080CbcEnabled;
    }

    public boolean getDH1080ECBEnabled() {
        return _dh1080EcbEnabled;
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
