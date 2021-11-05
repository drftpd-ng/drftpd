package org.drftpd.tvmaze.master;

import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.tvmaze.master.metadata.TvMazeInfo;

public class ConfigTvMaze extends ConfigElement<TvMazeInfo> {

    public ConfigTvMaze(TvMazeInfo value) {
        super(value);
    }
}
