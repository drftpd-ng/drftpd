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
package org.drftpd.zipscript.common.flac;

import org.drftpd.common.dynamicdata.Key;

import java.io.Serializable;

/**
 * @author norox
 */
@SuppressWarnings("serial")
public class FlacInfo implements Serializable {

    public static final Key<FlacInfo> FLACINFO = new Key<>(FlacInfo.class, "flac");

    private VorbisTag _vorbisTag;

    private int _samplerate = 0;

    private int _channels = 0;

    private float _runtime = 0;

    public FlacInfo() {
    }

    public VorbisTag getVorbisTag() {
        return _vorbisTag;
    }

    public void setVorbisTag(VorbisTag vorbisTag) {
        _vorbisTag = vorbisTag;
    }

    public int getSamplerate() {
        return _samplerate;
    }

    public void setSamplerate(int samplerate) {
        _samplerate = samplerate;
    }

    public int getChannels() {
        return _channels;
    }

    public void setChannels(int channels) {
        _channels = channels;
    }

    public float getRuntime() {
        return _runtime;
    }

    public void setRuntime(float runtime) {
        _runtime = runtime;
    }
}
