package com.tomersch.mp3playerai.player;

import com.tomersch.mp3playerai.utils.LibraryRepository;

public interface LibraryProvider {
    LibraryRepository getLibraryRepository();
}
