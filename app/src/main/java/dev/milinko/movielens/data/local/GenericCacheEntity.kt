package dev.milinko.movielens.data.local

import androidx.room.Entity

/**
 * Faza C (16. avgust 2026, korisnički zahtev "kesiraju u room... da bi radilo sto brze... i offline
 * stvari") - opšti keš za ekrane koji su ranije čitali direktno sa mreže bez ikakvog lokalnog
 * čuvanja (sopstveni profil, prijatelji, pending zahtevi, feed aktivnosti, taste profil, Movie Night
 * predlozi, profil prijatelja). Umesto posebne Room tabele/entiteta za svaki od ovih (različiti
 * oblici podataka, ali svi se uvek čitaju/pišu kao "cela lista/objekat odjednom", nikad ne treba
 * SQL upit po pojedinačnom polju) - JEDNA tabela sa JSON payload-om po `cacheKey`, isti princip kao
 * "poor man's key-value store". `cacheKey` kodira i tip podatka i vlasnika (npr. "friends:<userId>",
 * "movienight:<userId>:<friendId>") da bi različiti nalozi na istom uređaju i različiti prijatelji
 * imali odvojene unose.
 */
@Entity(tableName = "generic_cache", primaryKeys = ["cacheKey"])
data class GenericCacheEntity(
    val cacheKey: String,
    val jsonPayload: String,
    val updatedAt: Long = System.currentTimeMillis()
)
