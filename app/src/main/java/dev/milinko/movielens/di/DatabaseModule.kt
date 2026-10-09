package dev.milinko.movielens.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.milinko.movielens.data.local.AppDatabase
import dev.milinko.movielens.data.local.GenericCacheDao
import dev.milinko.movielens.data.local.HistoryDao
import dev.milinko.movielens.data.local.WatchlistDao
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "movielens.db")
            // Cache tables only; everything is refetched from the server on the next screen load,
            // so wiping them on a schema change is safe and simpler than Migration classes.
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()

    @Provides
    fun provideGenericCacheDao(db: AppDatabase): GenericCacheDao = db.genericCacheDao()
}
