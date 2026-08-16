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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "movielens.db")
            // Destructive fallback uključen od v2 (16. avgust 2026, HistoryEntity.review kolona) -
            // ovo su čiste keš tabele koje se uvek ponovo pune sa servera (getHistory/getWatchlist
            // pri sledećem otvaranju ekrana), pa je brisanje pri promeni šeme bezbedno i jednostavnije
            // od pisanja Migration klasa za podatke koji ionako nisu izvor istine.
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
