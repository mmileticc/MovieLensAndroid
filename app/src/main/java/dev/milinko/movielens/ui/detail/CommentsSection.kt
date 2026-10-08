package dev.milinko.movielens.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.CastMember
import dev.milinko.movielens.data.remote.movie.MovieComment
import dev.milinko.movielens.data.remote.movie.Showtime
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.FullScreenImageDialog
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.RatingStars
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.components.YouTubeTrailerPlayer

/**
 * Javni komentari na film (E2.2 dorada, 3. septembar 2026) - zamenjuje stari privatan "komentar
 * uz ocenu". Sadrži polje za pisanje novog komentara + listu postojećih ("prijatelji" uvek prvi,
 * sortiranje je već urađeno na backendu - videti CommentsRepository.getForMovie) + "Prikaži više/
 * manje" dugmad. `uiState`/`viewModel` se prosleđuju direktno (umesto da se svaka funkcija
 * pojedinačno prosledi) jer sekcija treba dosta različitih polja/akcija odjednom - isti kompromis
 * kao drugi veći item blokovi na ovom ekranu.
 */
@Composable
internal fun CommentsSection(
    tmdbId: Int,
    uiState: MovieDetailUiState,
    viewModel: MovieDetailViewModel,
    onRequireSignIn: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(16.dp)) {
        SectionHeader(
            title = stringResource(R.string.movie_detail_comments_header),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var commentText by remember { mutableStateOf("") }
        // Server nije prihvatio komentar (optimistična objava) - tekst se vraća u polje.
        LaunchedEffect(uiState.failedCommentText) {
            uiState.failedCommentText?.let { failed ->
                if (commentText.isBlank()) commentText = failed
                Toast.makeText(context, context.getString(R.string.movie_detail_comment_post_failed), Toast.LENGTH_LONG).show()
                viewModel.consumeFailedComment()
            }
        }
        OutlinedTextField(
            value = commentText,
            onValueChange = { if (it.length <= 1000) commentText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.movie_detail_comment_label)) },
            minLines = 2,
            maxLines = 4
        )
        if (commentText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                text = stringResource(R.string.movie_detail_post_comment),
                variant = AppButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    focusManager.clearFocus()
                    val submittedText = commentText
                    val startedRequest = viewModel.postComment(tmdbId, submittedText)
                    if (startedRequest) {
                        commentText = ""
                    } else {
                        Toast.makeText(context, context.getString(R.string.movie_detail_comment_post_signin_toast), Toast.LENGTH_SHORT).show()
                        onRequireSignIn()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dorada (3. septembar 2026, korisnički zahtev - "pre brisanja komentara treba samo da
        // pita da li je siguran") - isti obrazac kao friendPendingRemoval u ProfileScreen.kt.
        var commentPendingDeletion by remember { mutableStateOf<MovieComment?>(null) }

        if (uiState.isLoadingComments) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.comments.isEmpty()) {
            Text(
                text = stringResource(R.string.movie_detail_comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.comments.take(uiState.commentsVisibleCount).forEach { comment ->
                    CommentCard(
                        comment = comment,
                        onLikeClick = { viewModel.toggleCommentLike(comment.id) },
                        onDeleteClick = { commentPendingDeletion = comment }
                    )
                }
            }

            val canShowMore = uiState.commentsVisibleCount < uiState.comments.size || uiState.commentsHasMoreOnServer
            val canShowLess = uiState.commentsVisibleCount > MovieDetailViewModel.COMMENTS_PAGE_SIZE
            if (canShowMore || canShowLess) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (canShowMore) {
                        TextButton(
                            onClick = { viewModel.showMoreComments(tmdbId) },
                            enabled = !uiState.isLoadingMoreComments
                        ) {
                            if (uiState.isLoadingMoreComments) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(stringResource(R.string.movie_detail_comments_show_more))
                            }
                        }
                    }
                    if (canShowLess) {
                        TextButton(onClick = { viewModel.showLessComments() }) {
                            Text(stringResource(R.string.movie_detail_comments_show_less))
                        }
                    }
                }
            }
        }

        commentPendingDeletion?.let { comment ->
            AlertDialog(
                onDismissRequest = { commentPendingDeletion = null },
                title = { Text(stringResource(R.string.movie_detail_comment_delete_dialog_title)) },
                text = { Text(stringResource(R.string.movie_detail_comment_delete_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteComment(comment.id)
                        commentPendingDeletion = null
                    }) {
                        Text(stringResource(R.string.movie_detail_comment_delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { commentPendingDeletion = null }) {
                        Text(stringResource(R.string.movie_detail_comment_delete_cancel))
                    }
                }
            )
        }
    }
}

/**
 * Jedan komentar - avatar/ime (+ "Prijatelj" bedž ako je isFriend), tekst, i lajk/brisanje na dnu.
 * Kanta za smeće se prikazuje samo za `isOwn` komentare; srce za lajk je onemogućeno za sopstveni
 * komentar (backend ionako odbija - videti CommentsRepository.toggleLike - ovde je samo vizuelna
 * potvrda iste odluke, da korisnik ne klikne pa dobije tihu grešku).
 */
@Composable
internal fun CommentCard(comment: MovieComment, onLikeClick: () -> Unit, onDeleteClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bugfix (3. septembar 2026, korisnički bug report - "slika ljudi koji su
                // komentarisali se ne prikazuje") - avatar polje je često `data:image/...;base64,...`
                // (videti util/AvatarImage.kt), a plain AsyncImage(model=url) (Coil 2.x) nema podršku
                // za `data:` šemu pa je tiho ništa ne prikazivao. RemoteAvatarImage je deljena
                // komponenta koja već rešava ovo za ostatak app-a (feed prijatelja, profil, itd.).
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    RemoteAvatarImage(
                        url = comment.avatarUrl,
                        contentDescription = comment.username,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = comment.username, style = MaterialTheme.typography.titleSmall)
                    if (comment.isFriend) {
                        Spacer(modifier = Modifier.width(6.dp))
                        GenreChip(text = stringResource(R.string.movie_detail_comment_friend_badge))
                    }
                }
                if (comment.isOwn) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.movie_detail_comment_delete_content_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !comment.isOwn, onClick = onLikeClick)
            ) {
                Icon(
                    imageVector = if (comment.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.movie_detail_comment_like_content_description),
                    modifier = Modifier.size(16.dp),
                    tint = if (comment.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${comment.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
