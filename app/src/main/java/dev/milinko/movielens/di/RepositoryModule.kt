package dev.milinko.movielens.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.milinko.movielens.data.repository.ChatRepository
import dev.milinko.movielens.data.repository.ChatRepositoryImpl
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.repository.VisionRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindVisionRepository(
        visionRepositoryImpl: VisionRepositoryImpl
    ): VisionRepository
}
