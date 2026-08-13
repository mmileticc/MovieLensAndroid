package dev.milinko.movielens.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.milinko.movielens.data.local.AppDatabase
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
            // Nema migracija za sad (version 1, prva verzija ove baze) - keš tabele, bezbedno je
            // da se u budućnosti pri promeni šeme prosto obrišu i ponovo napune sa servera umesto
            // pisanja Migration klasa, ali destructive fallback ovde namerno NIJE uključen dok
            // stvarno ne zatreba (da izmena šeme ne obriše korisnicima keš bez razloga).
            .build()
    }

    @Provides
    fun provideWatchlistDao(db: AppDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideHistoryDao(db: AppDatabase): HistoryDao = db.historyDao()
}
