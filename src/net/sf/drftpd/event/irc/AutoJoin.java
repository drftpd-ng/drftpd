/*
 * Created on 2003-aug-14
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event.irc;

import java.util.Timer;
import java.util.TimerTask;

import f00f.net.irc.martyr.Debug;
import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.commands.JoinCommand;
import f00f.net.irc.martyr.commands.KickCommand;
import f00f.net.irc.martyr.errors.ChannelInviteOnlyError;

/**
 * <p>AutoJoin joins a group if the IRCConnection is ready.  It will wait until
 * it is ready if it is not (by waiting for the REGISTERED state change).</p>
 *
 * <p>AutoJoin maintains a persistent Join (re-join if kicked).
 * AutoJoin can cease to be persistent by calling the 'disable'
 * method.</p>
 * 
 * @author Morgan Christiansson <mog@mog.se>
 */
public class AutoJoin extends GenericAutoService
{

private String channel = null;
private String key = null;

public AutoJoin( IRCConnection connection, String channel )
{
	this( connection, channel, null );
}

public AutoJoin( IRCConnection connection, String channel, String key )
{
	super( connection );
	
	this.channel = channel;
	this.key = key;

	enable();

	updateState( connection.getState() );
}

protected void updateState( State state )
{

	if( state == State.REGISTERED )
		performJoin();
}

protected void updateCommand(InCommand command_o )
{
	if( command_o instanceof KickCommand )
	{
		KickCommand kickCommand = (KickCommand)command_o;

		if( kickCommand.kickedUs( getConnection().getClientState() ) )
		{
			if(kickCommand.getChannel().equalsIgnoreCase(this.channel)) {
				getConnection().sendCommand( new JoinCommand( this.channel,  this.key) );
			} else {
				getConnection().sendCommand( new JoinCommand( kickCommand.getChannel() ) );
			}
		}
	}
	else if( command_o instanceof ChannelInviteOnlyError )
	{
		ChannelInviteOnlyError inviteErr = (ChannelInviteOnlyError)command_o;
		
		if( Channel.areEqual( inviteErr.getChannel(), channel ) )
		{
			// And what do we do now?
			Debug.println( this, "Channel is invite only.", Debug.VERBOSE );
			Timer timer = new Timer();
			timer.schedule(new RejoinTimerTask(getConnection(), this.channel, this.key), 10000);
		}
	}
}

private void performJoin()
{
	getConnection().sendCommand( new JoinCommand( channel, key ) );
}

public String toString()
{
	if( key == null )
		return "AutoJoin [" + channel + "]";
	return "AutoJoin [" + channel + "," + key + "]";
}

// END AutoResponder
}

class RejoinTimerTask extends TimerTask {
	private IRCConnection conn;
	private String channel;
	private String key;
	
	public RejoinTimerTask(IRCConnection conn, String channel) {
		this(conn, channel, null);
	}

	public RejoinTimerTask(IRCConnection conn, String channel, String key) {
		this.conn = conn;
		this.channel = channel;
		this.key = key;
	}
	
	public void run() {
		conn.sendCommand(new JoinCommand(this.channel, this.key));
	}
	
}