package dev.milinko.geminiwrapper.ui.chat

import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.core.content.FileProvider
import dev.milinko.geminiwrapper.data.local.ChatMessage
import dev.milinko.geminiwrapper.ui.components.AiLoadingItem
import dev.milinko.geminiwrapper.ui.components.ChatInputBar
import dev.milinko.geminiwrapper.ui.components.ChatMessageItem
import dev.milinko.geminiwrapper.ui.components.LoadingDots
import dev.milinko.geminiwrapper.ui.components.SettingsDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var cameraImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImageSelected(uri?.toString()) }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                viewModel.onImageSelected(cameraImageUri?.toString())
            }
        }
    )

    fun openCamera() {
        try {
            val directory = File(context.cacheDir, "images")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val tempFile = File(directory, "camera_image_${System.currentTimeMillis()}.jpg")
            
            val authority = "${context.packageName}.fileprovider"
            
            val uri = FileProvider.getUriForFile(
                context,
                authority,
                tempFile
            )
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("ChatScreen", "Error opening camera", e)
            Toast.makeText(context, "Greška pri otvaranju kamere: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                showPermissionRationale = true
            }
        }
    )

    // Automatski skrol na dno kada stigne nova poruka
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentUrl = baseUrl,
            onDismiss = { showSettingsDialog = false },
            onSave = { 
                viewModel.updateBaseUrl(it)
                showSettingsDialog = false
            }
        )
    }

    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = { Text("Brisanje istorije") },
            text = { Text("Da li ste sigurni da želite da obrišete sve poruke?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChat()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Obriši", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Otkaži")
                }
            }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Dozvola za kameru") },
            text = { Text("Kamera je neophodna za direktno slikanje i slanje slika u čet. Molimo vas da odobrite pristup u podešavanjima aplikacije.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Idi u podešavanja")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Otkaži")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Chat Wrapper", fontSize = 18.sp)
                        Text(baseUrl, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Backend Settings")
                    }
                    IconButton(onClick = { showClearChatDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                enabled = !isLoading,
                selectedImageUri = selectedImageUri,
                onImagePick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCameraCapture = {
                    val permission = android.Manifest.permission.CAMERA
                    if (checkSelfPermission(context, permission) == PERMISSION_GRANTED) {
                        openCamera()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                onRemoveImage = { viewModel.onImageSelected(null) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages) { message ->
                    ChatMessageItem(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (isLoading) {
                    item {
                        AiLoadingItem()
                    }
                }
            }
        }
    }
}
