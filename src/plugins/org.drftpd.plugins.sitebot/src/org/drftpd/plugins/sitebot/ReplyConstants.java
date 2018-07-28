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

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public interface ReplyConstants {


	// Error Replies.
    int ERR_NOSUCHNICK = 401;
	int ERR_NOSUCHSERVER = 402;
	int ERR_NOSUCHCHANNEL = 403;
	int ERR_CANNOTSENDTOCHAN = 404;
	int ERR_TOOMANYCHANNELS = 405;
	int ERR_WASNOSUCHNICK = 406;
	int ERR_TOOMANYTARGETS = 407;
	int ERR_NOORIGIN = 409;
	int ERR_NORECIPIENT = 411;
	int ERR_NOTEXTTOSEND = 412;
	int ERR_NOTOPLEVEL = 413;
	int ERR_WILDTOPLEVEL = 414;
	int ERR_UNKNOWNCOMMAND = 421;
	int ERR_NOMOTD = 422;
	int ERR_NOADMININFO = 423;
	int ERR_FILEERROR = 424;
	int ERR_NONICKNAMEGIVEN = 431;
	int ERR_ERRONEUSNICKNAME = 432;
	int ERR_NICKNAMEINUSE = 433;
	int ERR_NICKCOLLISION = 436;
	int ERR_USERNOTINCHANNEL = 441;
	int ERR_NOTONCHANNEL = 442;
	int ERR_USERONCHANNEL = 443;
	int ERR_NOLOGIN = 444;
	int ERR_SUMMONDISABLED = 445;
	int ERR_USERSDISABLED = 446;
	int ERR_NOTREGISTERED = 451;
	int ERR_NEEDMOREPARAMS = 461;
	int ERR_ALREADYREGISTRED = 462;
	int ERR_NOPERMFORHOST = 463;
	int ERR_PASSWDMISMATCH = 464;
	int ERR_YOUREBANNEDCREEP = 465;
	int ERR_KEYSET = 467;
	int ERR_CHANNELISFULL = 471;
	int ERR_UNKNOWNMODE = 472;
	int ERR_INVITEONLYCHAN = 473;
	int ERR_BANNEDFROMCHAN = 474;
	int ERR_BADCHANNELKEY = 475;
	int ERR_NOPRIVILEGES = 481;
	int ERR_CHANOPRIVSNEEDED = 482;
	int ERR_CANTKILLSERVER = 483;
	int ERR_NOOPERHOST = 491;
	int ERR_UMODEUNKNOWNFLAG = 501;
	int ERR_USERSDONTMATCH = 502;


	// Server Info (RFC2812)
    int RPL_WELCOME = 1;
	int RPL_YOURHOST = 2;
	int RPL_CREATED = 3;
	int RPL_MYINFO = 4;
	int RPL_BOUNCE = 5;


	// Command Responses.
    int RPL_TRACELINK = 200;
	int RPL_TRACECONNECTING = 201;
	int RPL_TRACEHANDSHAKE = 202;
	int RPL_TRACEUNKNOWN = 203;
	int RPL_TRACEOPERATOR = 204;
	int RPL_TRACEUSER = 205;
	int RPL_TRACESERVER = 206;
	int RPL_TRACENEWTYPE = 208;
	int RPL_STATSLINKINFO = 211;
	int RPL_STATSCOMMANDS = 212;
	int RPL_STATSCLINE = 213;
	int RPL_STATSNLINE = 214;
	int RPL_STATSILINE = 215;
	int RPL_STATSKLINE = 216;
	int RPL_STATSYLINE = 218;
	int RPL_ENDOFSTATS = 219;
	int RPL_UMODEIS = 221;
	int RPL_STATSLLINE = 241;
	int RPL_STATSUPTIME = 242;
	int RPL_STATSOLINE = 243;
	int RPL_STATSHLINE = 244;
	int RPL_LUSERCLIENT = 251;
	int RPL_LUSEROP = 252;
	int RPL_LUSERUNKNOWN = 253;
	int RPL_LUSERCHANNELS = 254;
	int RPL_LUSERME = 255;
	int RPL_ADMINME = 256;
	int RPL_ADMINLOC1 = 257;
	int RPL_ADMINLOC2 = 258;
	int RPL_ADMINEMAIL = 259;
	int RPL_TRACELOG = 261;
	int RPL_NONE = 300;
	int RPL_AWAY = 301;
	int RPL_USERHOST = 302;
	int RPL_ISON = 303;
	int RPL_UNAWAY = 305;
	int RPL_NOWAWAY = 306;
	int RPL_WHOISUSER = 311;
	int RPL_WHOISSERVER = 312;
	int RPL_WHOISOPERATOR = 313;
	int RPL_WHOWASUSER = 314;
	int RPL_ENDOFWHO = 315;
	int RPL_WHOISIDLE = 317;
	int RPL_ENDOFWHOIS = 318;
	int RPL_WHOISCHANNELS = 319;
	int RPL_LISTSTART = 321;
	int RPL_LIST = 322;
	int RPL_LISTEND = 323;
	int RPL_CHANNELMODEIS = 324;
	int RPL_NOTOPIC = 331;
	int RPL_TOPIC = 332;
	int RPL_TOPICINFO = 333;
	int RPL_INVITING = 341;
	int RPL_SUMMONING = 342;
	int RPL_VERSION = 351;
	int RPL_WHOREPLY = 352;
	int RPL_NAMREPLY = 353;
	int RPL_LINKS = 364;
	int RPL_ENDOFLINKS = 365;
	int RPL_ENDOFNAMES = 366;
	int RPL_BANLIST = 367;
	int RPL_ENDOFBANLIST = 368;
	int RPL_ENDOFWHOWAS = 369;
	int RPL_INFO = 371;
	int RPL_MOTD = 372;
	int RPL_ENDOFINFO = 374;
	int RPL_MOTDSTART = 375;
	int RPL_ENDOFMOTD = 376;
	int RPL_YOUREOPER = 381;
	int RPL_REHASHING = 382;
	int RPL_TIME = 391;
	int RPL_USERSSTART = 392;
	int RPL_USERS = 393;
	int RPL_ENDOFUSERS = 394;
	int RPL_NOUSERS = 395;


	// Reserved Numerics.
    int RPL_TRACECLASS = 209;
	int RPL_STATSQLINE = 217;
	int RPL_SERVICEINFO = 231;
	int RPL_ENDOFSERVICES = 232;
	int RPL_SERVICE = 233;
	int RPL_SERVLIST = 234;
	int RPL_SERVLISTEND = 235;
	int RPL_WHOISCHANOP = 316;
	int RPL_KILLDONE = 361;
	int RPL_CLOSING = 362;
	int RPL_CLOSEEND = 363;
	int RPL_INFOSTART = 373;
	int RPL_MYPORTIS = 384;
	int ERR_YOUWILLBEBANNED = 466;
	int ERR_BADCHANMASK = 476;
	int ERR_NOSERVICEHOST = 492;

}
