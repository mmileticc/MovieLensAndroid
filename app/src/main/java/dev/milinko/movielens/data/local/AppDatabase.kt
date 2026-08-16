package dev.milinko.movielens.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Offline keš baza. Do Faze C (16. avgust 2026) je bila namerno SAMO watchlist/istorija (podaci
 * "koje ja posedujem"). Pretraga/bioskopski termini OSTAJU nekešrani (live upit ka TMDB/skrapovanim
 * terminima, vremenski osetljivo - vidi MOVIELENS_ROADMAP.md). `GenericCacheEntity` dodat u Fazi C
 * proširuje ovo na profil/prijatelje/pending zahteve/feed aktivnosti/taste profil/Movie Night
 * predloge/profil prijatelja - svi su ranije čitali direktno sa mreže bez ikakvog lokalnog keša,
 * korisnička žalba da app "ne deluje momentalno" i da ne radi ništa offline.
 */
@Database(
    entities = [WatchlistEntity::class, HistoryEntity::class, GenericCacheEntity::class],
    // v3 (16. avgust 2026, Faza C) - dodat GenericCacheEntity. Nema Migration klase jer je ovo čist
    // keš (uvek se ponovo puni sa servera) - videti fallbackToDestructiveMigration u DatabaseModule.kt.
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
    abstract fun historyDao(): HistoryDao
    abstract fun genericCacheDao(): GenericCacheDao
}
