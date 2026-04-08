package com.micplugin.di

import android.content.Context
import com.micplugin.audio.AudioEngine
import com.micplugin.audio.OboeEngine
import com.micplugin.plugin.PluginChain
import com.micplugin.plugin.PluginManager
import com.micplugin.preset.PresetManager
import com.micplugin.service.ShizukuManager
import com.micplugin.service.VirtualMicService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideOboeEngine(): OboeEngine = OboeEngine()

    @Provides @Singleton
    fun providePluginChain(): PluginChain = PluginChain()

    @Provides @Singleton
    fun provideAudioEngine(oboe: OboeEngine, chain: PluginChain): AudioEngine =
        AudioEngine(oboe, chain)

    @Provides @Singleton
    fun providePluginManager(@ApplicationContext ctx: Context): PluginManager =
        PluginManager(ctx)

    @Provides @Singleton
    fun providePresetManager(@ApplicationContext ctx: Context): PresetManager =
        PresetManager(ctx)

    @Provides @Singleton
    fun provideShizukuManager(): ShizukuManager = ShizukuManager()

    @Provides @Singleton
    fun provideVirtualMicService(
        @ApplicationContext ctx: Context,
        shizukuManager: ShizukuManager,
    ): VirtualMicService = VirtualMicService(ctx, shizukuManager)
}
