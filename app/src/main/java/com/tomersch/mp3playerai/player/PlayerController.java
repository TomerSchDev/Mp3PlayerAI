
package com.tomersch.mp3playerai.player;

import com.tomersch.mp3playerai.models.Song;
import java.util.List;

public interface PlayerController {
    void playQueue(List<Song> queue, int startIndex);
    void playSong(Song song);
    void togglePlayPause();
    void next();
    void previous();
    Song getCurrentSong();
    boolean isPlaying();
}
