package com.raf.audiovisualizer.data.di

import com.raf.audiovisualizer.data.audio.AudioVisualizerRepositoryImpl
import com.raf.audiovisualizer.data.media.NowPlayingRepositoryImpl
import com.raf.audiovisualizer.domain.repository.AudioVisualizerRepository
import com.raf.audiovisualizer.domain.repository.NowPlayingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindAudioVisualizerRepository(
        impl: AudioVisualizerRepositoryImpl,
    ): AudioVisualizerRepository

    @Binds
    @Singleton
    abstract fun bindNowPlayingRepository(
        impl: NowPlayingRepositoryImpl,
    ): NowPlayingRepository
}
