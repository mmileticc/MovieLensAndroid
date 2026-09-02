package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.local.GenericCacheDao
import dev.milinko.movielens.data.local.GenericCacheEntity
import dev.milinko.movielens.data.local.HistoryDao
import dev.milinko.movielens.data.local.HistoryEntity
import dev.milinko.movielens.data.local.WatchlistDao
import dev.milinko.movielens.data.local.WatchlistEntity
import dev.milinko.movielens.data.remote.user.AddToHistoryBody
import dev.milinko.movielens.data.remote.user.FriendActionBody
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UpdateHistoryBody
import dev.milinko.movielens.data.remote.user.UserApiService
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.user.MovieNightResponse
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.util.safeApiCall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile>
    suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile>

    // Faza C (16. avgust 2026, korisnički zahtev "da deluje momentalno pa se pozadinski sa
    // backom to uradi... i offline stvari") - isti obrazac kao observeWatchlist/observeHistory
    // ispod, sad i za profil/prijatelje/pending zahteve/feed aktivnosti/taste profil/profil
    // prijatelja. `observeCached*` SAMO čita iz Room-a (nikad ne zove mrežu) - ViewModel ga koristi
    // za trenutni prikaz dok redovan `getXxx()` poziv ispod (nepromenjen) i dalje ide na mrežu u
    // pozadini i piše sveže podatke nazad u isti keš.
    fun observeCachedUserProfile(userId: String): Flow<UserProfile?>
    fun observeCachedFriends(userId: String): Flow<List<FriendInfo>>
    fun observeCachedPendingRequests(userId: String): Flow<List<PendingRequest>>
    // Korisnički zahtev (2. septembar 2026, "da mi budu vidljivi izlistani poslati zahtevi kao
    // sto su mi izlistani prijatelji") - isti obrazac keš/mreža kao dolazni pending zahtevi.
    fun observeCachedSentRequests(userId: String): Flow<List<PendingRequest>>
    fun observeCachedFriendActivity(userId: String): Flow<List<FriendActivity>>
    fun observeCachedTasteProfile(userId: String): Flow<List<GenreAffinity>>
    fun observeCachedFriendProfile(userId: String, friendId: String): Flow<FriendProfileResponse?>
    // Bugfix (2. septembar 2026, feedback - "posalje se zahtev, pa se ponovo otvori profil i
    // dugme i dalje pise 'Dodaj prijatelja'") - `sendFriendRequest`/`cancelFriendRequest` na
    // FriendProfileScreen-u RANIJE su menjali SAMO _uiState u memoriji, ne i Room keš - pa bi
    // sveže otvoren (nova ViewModel instanca) ekran tog istog prijatelja pročitao STARI keš
    // zapis (npr. i dalje "NONE") i nakratko prikazao pogrešno dugme dok mreža (koja može biti
    // spora na Render cold-startu) ne stigne da ispravi stanje. Ovo piše samo u keš, bez mreže -
    // zove se odmah uz optimistički UI update, isto kao što getFriendProfile piše keš posle
    // stvarnog mrežnog odgovora.
    suspend fun cacheFriendProfile(userId: String, friendId: String, profile: FriendProfileResponse)

    // Bugfix (2. septembar 2026, feedback - "otkažem zahtev kod prijatelja [na Profile ekranu],
    // uđem na detalje [FriendProfileScreen] - stanje se nije promenilo, vratim se nazad na
    // prijatelje - tu bude opet pogrešno kao na detaljima") - `sendFriendRequest`/
    // `cancelFriendRequest` na ProfileScreen-u (rezultati pretrage) su menjali SAMO
    // `friendSearchResults` u memoriji, NE i isti Room keš zapis koji FriendProfileScreen čita
    // (`friendprofile:userId:friendId`). Kad bi se posle toga otvorio taj profil, čitao bi se
    // STARI keš (netaknut ProfileScreen akcijom) - i taj (pogrešan) zapis bi se onda, preko
    // `refreshFriendSearchResults()` cache-merge dorade, prepisao NAZAD u `friendSearchResults`
    // pri povratku na Profile ekran - efektivno brišući ispravno stanje pogrešnim. Ovo zatvara
    // petlju u OBA smera: ako keš zapis za tog prijatelja već postoji (znači korisnik ga je
    // nekad otvorio), status u njemu se odmah ažurira - bez mreže. Ako zapis ne postoji, nema šta
    // da se ažurira (nema dovoljno podataka - bio/email/watchedMovies - da se napravi ceo
    // FriendProfileResponse iz UserSearchResult-a, koji ima samo id/username/avatar/status) - to
    // je ok, prvi network fetch na FriendProfileScreen-u će ga sam popuniti ispravno.
    suspend fun updateCachedFriendshipStatus(userId: String, friendId: String, status: String)

    // Offline-first: čita se odmah iz Room keša (instant, radi i bez interneta), getWatchlist
    // ispod ostaje "osveži sa mreže" - poziva se u pozadini, upisuje u isti keš, Flow automatski
    // emituje novu vrednost UI-ju. Videti diskusiju u MOVIELENS_ROADMAP.md o offline obimu -
    // namerno SAMO watchlist/istorija, ne i pretraga/trending/bioskopski termini.
    fun observeWatchlist(userId: String): Flow<List<WatchlistItem>>
    suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>>
    suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem>
    suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit>

    fun observeHistory(userId: String): Flow<List<HistoryItem>>
    suspend fun getHistory(userId: String): Result<List<HistoryItem>>
    // rating/duration su opcioni - "označeno kao odgledano bez ocene" šalje oba kao null (ne kao
    // 0.0, koji bi backend zaokružio na ocenu 1 - videti Double?.toRatingIntOrNull() na backendu).
    // review - kratak tekstualni komentar uz ocenu (opcion, nezavisan od rating-a).
    suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double? = null, review: String? = null, duration: Int? = null): Result<HistoryItem>
    // rating/review = null znače "ne diraj postojeću vrednost" (npr. izmena samo komentara bez
    // dodira na ocenu, ili obrnuto) - videti napomenu u backend UserActionsRepository.markWatched.
    suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String? = null): Result<HistoryItem>
    // "Undo" za slučajno "označi kao odgledano" - briše i ocenu (isti red na backendu).
    suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit>

    // Pretraga korisnika za dodavanje prijatelja (search-as-you-type, kao Search filmova).
    suspend fun searchUsers(query: String): Result<List<UserSearchResult>>
    suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>>
    suspend fun getSentFriendRequests(userId: String): Result<List<PendingRequest>>

    suspend fun getFriends(userId: String): Result<List<FriendInfo>>
    suspend fun addFriend(userId: String, friendId: String): Result<Unit>
    suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit>
    suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse>
    suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>>

    suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>>

    // Dorada (25. avgust 2026, korisnička žalba "za neke prijatelje se predlozi ponavljaju posle
    // 3-4 ekrana") - vraća ceo MovieNightResponse (ne samo listu) da MovieNightViewModel dobije i
    // `hasMore` (da li TMDB ima još stranica) umesto ranijeg izmišljenog fiksnog broja stranica.
    suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int = 1): Result<MovieNightResponse>

    /**
     * Učitava movienight-suggestions unapred (npr. čim se otvori profil prijatelja) i drži ih u
     * memoriji da getMovieNightSuggestions posle vrati odmah bez čekanja mreže kad korisnik
     * stvarno klikne na dugme. Tiho gutа grešku - ovo je samo optimizacija, ne kritičan poziv;
     * ako ne uspe, obično dugme i dalje radi normalno (samo bez prefetch-a).
     */
    suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String)
}

private data class ApiErrorBody(val error: String? = null)

/** Parsira backend-ovo `{"error": "..."}` telo greške (ErrorResponse.kt) u čitljivu poruku. */
private fun Response<*>.parseErrorMessage(fallback: String): String {
    return try {
        val raw = errorBody()?.string()
        if (raw.isNullOrBlank()) fallback else Gson().fromJson(raw, ApiErrorBody::class.java)?.error ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

private fun WatchlistEntity.toDomain() = WatchlistItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority
)

private fun WatchlistItem.toEntity(userId: String) = WatchlistEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority
)

private fun HistoryEntity.toDomain() = HistoryItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration
)

private fun HistoryItem.toEntity(userId: String) = HistoryEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration
)

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val genericCacheDao: GenericCacheDao
) : UserRepository {

    // Memorijski keš za movienight predloge (userId:friendId -> lista) - popunjava se preko
    // prefetchMovieNightSuggestions kad se otvori profil prijatelja, čita se u
    // getMovieNightSuggestions da MovieNightScreen ne mora da čeka mrežu ako je već prefetch-ovano.
    // ConcurrentHashMap jer prefetch i stvaran poziv mogu teoretski da se preklope. Faza C dodaje
    // Room ISPOD ovoga kao drugi nivo (memorija se prazni na svaki restart procesa, Room ostaje).
    // Dorada (25. avgust 2026) - keš sad drži ceo MovieNightResponse (uklj. hasMore), ne samo
    // listu predloga - videti obrazloženje na interfejsu iznad.
    private val movieNightCache = java.util.concurrent.ConcurrentHashMap<String, MovieNightResponse>()

    // Faza C - deljena Gson instanca za GenericCache (de)serijalizaciju (parseErrorMessage ispod
    // pravi sopstvenu Gson() instancu za sasvim drugu svrhu - namerno odvojeno, ne deli se).
    private val cacheGson = Gson()

    private fun profileCacheKey(userId: String) = "profile:$userId"
    private fun friendsCacheKey(userId: String) = "friends:$userId"
    private fun pendingCacheKey(userId: String) = "pending:$userId"
    private fun sentCacheKey(userId: String) = "sent:$userId"
    private fun activityCacheKey(userId: String) = "activity:$userId"
    private fun tasteCacheKey(userId: String) = "taste:$userId"
    private fun friendProfileCacheKey(userId: String, friendId: String) = "friendprofile:$userId:$friendId"
    private fun movieNightCacheKey(userId: String, friendId: String, page: Int) = "movienight:$userId:$friendId:$page"

    override fun observeCachedUserProfile(userId: String): Flow<UserProfile?> =
        genericCacheDao.observe(profileCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json -> runCatching { cacheGson.fromJson(json, UserProfile::class.java) }.getOrNull() }
        }

    override fun observeCachedFriends(userId: String): Flow<List<FriendInfo>> =
        genericCacheDao.observe(friendsCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<FriendInfo>>(json, object : TypeToken<List<FriendInfo>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedPendingRequests(userId: String): Flow<List<PendingRequest>> =
        genericCacheDao.observe(pendingCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<PendingRequest>>(json, object : TypeToken<List<PendingRequest>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedSentRequests(userId: String): Flow<List<PendingRequest>> =
        genericCacheDao.observe(sentCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<PendingRequest>>(json, object : TypeToken<List<PendingRequest>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedFriendActivity(userId: String): Flow<List<FriendActivity>> =
        genericCacheDao.observe(activityCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<FriendActivity>>(json, object : TypeToken<List<FriendActivity>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedTasteProfile(userId: String): Flow<List<GenreAffinity>> =
        genericCacheDao.observe(tasteCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<GenreAffinity>>(json, object : TypeToken<List<GenreAffinity>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedFriendProfile(userId: String, friendId: String): Flow<FriendProfileResponse?> =
        genericCacheDao.observe(friendProfileCacheKey(userId, friendId)).map { entity ->
            entity?.jsonPayload?.let { json -> runCatching { cacheGson.fromJson(json, FriendProfileResponse::class.java) }.getOrNull() }
                // Bugfix (2. septembar 2026, feedback - "otvorim profil prijatelja i prikaže ga
                // kao da nismo prijatelji, pa se tek posle malo osveži") - stari keš zapisi
                // (snimljeni PRE E1.5) nemaju friendshipStatus u JSON-u, pa Gson tu vrednost
                // ostavlja null (videti komentar u UserModels.kt). Takav zapis se NE SME
                // prikazati kao da je "nije prijatelj" (isLocked=true bi bio pogrešan zaključak,
                // ne stvarno stanje) - bolje je da ekran ostane na spinneru dok pravi mrežni
                // odgovor ne stigne, nego da na trenutak prikaže pogrešan "zaključan" prikaz koji
                // posle "trepne" u tačan. Zato se ovakav (stari/nepotpun) keš zapis tretira kao
                // da uopšte ne postoji.
                ?.takeIf { it.friendshipStatus != null }
        }

    override suspend fun getUserProfile(userId: String): Result<UserProfile> = safeApiCall {
        val response = userApiService.getUserProfile(userId)
        if (response.isSuccessful) {
            val profile = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            genericCacheDao.put(GenericCacheEntity(profileCacheKey(userId), cacheGson.toJson(profile)))
            profile
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile> = safeApiCall {
        val response = userApiService.updateUserProfile(userId, profile)
        if (response.isSuccessful) {
            val updated = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            // Faza C - i ovde upisujemo u keš (ne samo GET), da observeCachedUserProfile odmah
            // odražava izmenu, bez čekanja na sledeći loadProfile() network refresh.
            genericCacheDao.put(GenericCacheEntity(profileCacheKey(userId), cacheGson.toJson(updated)))
            updated
        } else {
            // Backend vraća konkretnu poruku (npr. "Korisničko ime je već zauzeto") u JSON telu
            // greške ({"error": "..."}) - izvlačimo je da UI može da prikaže nešto korisnije od
            // golog HTTP koda (jedini poziv u repo-u gde je poruka greške stvarno actionable za
            // korisnika, za razliku od ostalih mrežnih poziva ovde).
            throw IllegalStateException(response.parseErrorMessage("Ažuriranje profila nije uspelo (${response.code()})"))
        }
    }

    override fun observeWatchlist(userId: String): Flow<List<WatchlistItem>> =
        watchlistDao.observeWatchlist(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>> = safeApiCall {
        val response = userApiService.getWatchlist(userId)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            // Osveži lokalni keš sa serverom - Flow iz observeWatchlist automatski emituje ovu
            // novu vrednost svakom kolektoru (Room je single source of truth za UI).
            watchlistDao.replaceAll(userId, items.map { it.toEntity(userId) })
            items
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem> = safeApiCall {
        val response = userApiService.addToWatchlist(userId, mapOf("tmdbId" to tmdbId))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            watchlistDao.insert(item.toEntity(userId))
            item
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromWatchlist(userId, tmdbId)
        if (response.isSuccessful) {
            watchlistDao.delete(userId, tmdbId)
        } else {
            throw HttpException(response)
        }
    }

    override fun observeHistory(userId: String): Flow<List<HistoryItem>> =
        historyDao.observeHistory(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getHistory(userId: String): Result<List<HistoryItem>> = safeApiCall {
        val response = userApiService.getHistory(userId)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            historyDao.replaceAll(userId, items.map { it.toEntity(userId) })
            items
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double?, review: String?, duration: Int?): Result<HistoryItem> = safeApiCall {
        // rating/duration null se serijalizuju kao JSON null (Gson) - backend AddToHistoryRequest
        // ima podrazumevanu vrednost null za oba, pa je to ekvivalentno izostavljanju polja.
        val response = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, review, duration))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            historyDao.insert(item.toEntity(userId))
            item
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String?): Result<HistoryItem> = safeApiCall {
        val response = userApiService.updateHistoryItem(userId, tmdbId, UpdateHistoryBody(rating, review))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            historyDao.insert(item.toEntity(userId))
            item
        } else if (response.code() == 404) {
            // Bugfix (E1.1, 2. septembar 2026) - odbrambeni sloj uz backend samoisceljujući PUT
            // (videti UserActionsRouting.kt komentar). Lokalni uiState je mislio da je film već
            // "odgledan" (npr. stari keš iz vremena pre backend fix-a, ili druga verzija backend-a
            // dok se ne redeploy-uje) pa je pozvan PUT umesto POST - ako backend ipak vrati 404,
            // probamo POST kao fallback umesto da odmah prijavimo grešku korisniku.
            val addResponse = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, review, null))
            if (addResponse.isSuccessful) {
                val item = addResponse.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
                historyDao.insert(item.toEntity(userId))
                item
            } else {
                throw HttpException(addResponse)
            }
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromHistory(userId, tmdbId)
        if (response.isSuccessful) {
            historyDao.delete(userId, tmdbId)
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun searchUsers(query: String): Result<List<UserSearchResult>> = safeApiCall {
        val response = userApiService.searchUsers(query)
        if (response.isSuccessful) {
            response.body()?.users ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>> = safeApiCall {
        val response = userApiService.getPendingFriendRequests(userId)
        if (response.isSuccessful) {
            val requests = response.body()?.requests ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(pendingCacheKey(userId), cacheGson.toJson(requests)))
            requests
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getSentFriendRequests(userId: String): Result<List<PendingRequest>> = safeApiCall {
        val response = userApiService.getSentFriendRequests(userId)
        if (response.isSuccessful) {
            val requests = response.body()?.requests ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(sentCacheKey(userId), cacheGson.toJson(requests)))
            requests
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFriends(userId: String): Result<List<FriendInfo>> = safeApiCall {
        val response = userApiService.getFriends(userId)
        if (response.isSuccessful) {
            val friends = response.body()?.friends ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(friendsCacheKey(userId), cacheGson.toJson(friends)))
            friends
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("add"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("accept"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("reject"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.removeFriend(userId, friendId)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse> = safeApiCall {
        val response = userApiService.getFriendProfile(userId, friendId)
        if (response.isSuccessful) {
            val profile = response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            genericCacheDao.put(GenericCacheEntity(friendProfileCacheKey(userId, friendId), cacheGson.toJson(profile)))
            profile
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun cacheFriendProfile(userId: String, friendId: String, profile: FriendProfileResponse) {
        genericCacheDao.put(GenericCacheEntity(friendProfileCacheKey(userId, friendId), cacheGson.toJson(profile)))
    }

    override suspend fun updateCachedFriendshipStatus(userId: String, friendId: String, status: String) {
        val key = friendProfileCacheKey(userId, friendId)
        val existing = genericCacheDao.get(key)?.jsonPayload
            ?.let { json -> runCatching { cacheGson.fromJson(json, FriendProfileResponse::class.java) }.getOrNull() }
            ?: return
        genericCacheDao.put(GenericCacheEntity(key, cacheGson.toJson(existing.copy(friendshipStatus = status))))
    }

    override suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>> = safeApiCall {
        val response = userApiService.getTasteProfile(userId)
        if (response.isSuccessful) {
            val genres = response.body()?.genres ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(tasteCacheKey(userId), cacheGson.toJson(genres)))
            genres
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>> = safeApiCall {
        val response = userApiService.getFriendActivity(userId)
        if (response.isSuccessful) {
            val activities = response.body()?.activities ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(activityCacheKey(userId), cacheGson.toJson(activities)))
            activities
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int): Result<MovieNightResponse> {
        // page je deo ključa - "Osveži predloge" traži sledeću stranu pod drugim ključem, pa
        // prirodno zaobilazi keš (stvaran mrežni poziv) bez posebnog "forceRefresh" parametra.
        val cacheKey = "$userId:$friendId:$page"
        movieNightCache[cacheKey]?.let { return Result.success(it) }

        // Faza C - drugi nivo keša u Room-u (memorija se prazni na svaki restart procesa/app-a,
        // Room ostaje) - ako postoji, ubaci i nazad u memoriju da sledeći poziti u istoj sesiji
        // budu instant bez ponovnog Room čitanja.
        genericCacheDao.get(movieNightCacheKey(userId, friendId, page))?.jsonPayload?.let { json ->
            val cached = runCatching {
                cacheGson.fromJson(json, MovieNightResponse::class.java)
            }.getOrNull()
            if (cached != null) {
                movieNightCache[cacheKey] = cached
                return Result.success(cached)
            }
        }

        return safeApiCall {
            val response = userApiService.getMovieNightSuggestions(userId, friendId, page)
            if (response.isSuccessful) {
                val body = response.body() ?: MovieNightResponse()
                movieNightCache[cacheKey] = body
                genericCacheDao.put(GenericCacheEntity(movieNightCacheKey(userId, friendId, page), cacheGson.toJson(body)))
                body
            } else {
                throw HttpException(response)
            }
        }
    }

    override suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String) {
        val cacheKey = "$userId:$friendId:1"
        if (movieNightCache.containsKey(cacheKey)) return
        getMovieNightSuggestions(userId, friendId, page = 1)
    }
}
