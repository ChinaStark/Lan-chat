package com.example.lan_chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.lan_chat.api.RetrofitClient
import com.example.lan_chat.data.Message
import com.example.lan_chat.data.MessageType
import com.example.lan_chat.data.UserPreferences
import com.example.lan_chat.ui.theme.LanchatTheme
import com.example.lan_chat.viewmodel.ChatViewModel
import com.example.lan_chat.viewmodel.ChatViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModelFactory by lazy { ChatViewModelFactory() }
    private val viewModel: ChatViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create the user preferences helper
        val userPrefs = UserPreferences(applicationContext)

        setContent {
            LanchatTheme {
                // State to hold the username. Initially, we try to load it from preferences.
                var username by remember { mutableStateOf(userPrefs.getUsername()) }

                // Based on whether the username exists, we show one screen or the other.
                if (username.isNullOrBlank()) {
                    // --- USER HAS NO NAME: SHOW PROFILE SCREEN ---
                    UserProfileScreen(onUsernameSet = {
                        userPrefs.saveUsername(it)
                        username = it
                    })
                } else {
                    // --- USER HAS A NAME: SHOW CHAT SCREEN ---
                    val currentUsername = username!!

                    // This effect handles the initial connection.
                    DisposableEffect(currentUsername) { // Re-run if username ever changes
                        viewModel.connect(username = currentUsername)
                        onDispose { }
                    }

                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                                else -> {}
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    ChatScreen(viewModel = viewModel, currentUsername = currentUsername)
                }
            }
        }
    }
}


@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    currentUsername: String,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val showManualReconnectButton by viewModel.showManualReconnectButton.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.uploadFile(context, it)
        }
    }

    ChatScreenContent(
        messages = messages,
        isLoading = !isConnected && messages.isEmpty(),
        error = if (isConnected) null else error,
        text = text,
        onTextChange = { text = it },
        onSendMessage = {
            viewModel.sendMessage(text)
            text = ""
        },
        currentUsername = currentUsername,
        onAttachFileClick = {
            filePickerLauncher.launch("*/*")
        },
        showManualReconnectButton = showManualReconnectButton,
        onManualReconnectClick = { viewModel.manualReconnect() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    messages: List<Message>,
    isLoading: Boolean,
    error: String?,
    text: String,
    onTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    currentUsername: String,
    onAttachFileClick: () -> Unit,
    showManualReconnectButton: Boolean,
    onManualReconnectClick: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        topBar = { TopAppBar(title = { Text("LAN Chat") }) },
        bottomBar = {
            Column {
                if (showManualReconnectButton) {
                    Button(
                        onClick = onManualReconnectClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text("Connection Failed. Tap to Retry.")
                    }
                }

                if (error != null && !isLoading && !showManualReconnectButton) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onAttachFileClick) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File"
                        )
                    }

                    Button(
                        onClick = onSendMessage,
                        shape = RoundedCornerShape(24.dp),
                        enabled = text.isNotBlank()
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message, currentUsername = currentUsername)
                }
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}


@Composable
fun MessageBubble(message: Message, currentUsername: String) {
    val isFromMe = message.sender == currentUsername
    val arrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 0.dp,
                bottomEnd = if (isFromMe) 0.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isFromMe) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (message.type) {
                    MessageType.TEXT -> TextMessageContent(message, textColor)
                    MessageType.IMAGE -> ImageMessageContent(message)
                    MessageType.FILE -> FileMessageContent(message, textColor)
                }

                Text(
                    text = formatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun TextMessageContent(message: Message, textColor: Color) {
    message.content?.let {
        Text(
            text = it,
            color = textColor
        )
    }
}

@Composable
fun ImageMessageContent(message: Message) {
    val imageUrl = message.attachment?.url?.let { RetrofitClient.BASE_URL + it }
    if (imageUrl != null) {
        Spacer(modifier = Modifier.height(4.dp))

        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = "Sent image: ${message.attachment?.name}",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.FillWidth,
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AsyncImagePainter.State.Error -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                    ) {
                        Text("Could not load image", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    SubcomposeAsyncImageContent()
                }
            }
        }
    }
}

@Composable
fun FileMessageContent(message: Message, textColor: Color) {
    val context = LocalContext.current
    val fileUrl = message.attachment?.url?.let { RetrofitClient.BASE_URL + it }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = fileUrl != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fileUrl))
                context.startActivity(intent)
            }
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = "File icon",
            modifier = Modifier.size(40.dp),
            tint = textColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = message.attachment?.name ?: "Unknown file",
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(message.attachment?.size ?: 0),
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun formatTime(isoString: String): String {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.US)
            val date = parser.parse(isoString) ?: continue
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatter.format(date)
        } catch (e: Exception) {
            // Try next pattern
        }
    }
    return "--:--"
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeInBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeInBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    LanchatTheme {
        val messages = listOf(
            Message(1, "general", "Alice", MessageType.TEXT, "Hey!", null, "2023-01-01T10:00:00Z"),
            Message(2, "general", "AndroidUser", MessageType.TEXT, "Hi there!", null, "2023-01-01T10:01:00.123456Z"),
            Message(3, "general", "Alice", MessageType.TEXT, "This message uses the new format.", null, "2025-11-08T06:26:31.437591+00:00")
        )

        ChatScreenContent(
            messages = messages,
            isLoading = false,
            error = null,
            text = "Some text in the input field",
            onTextChange = {},
            onSendMessage = {},
            currentUsername = "AndroidUser",
            onAttachFileClick = {},
            showManualReconnectButton = true,
            onManualReconnectClick = {}
        )
    }
}


@Preview(showBackground = true)
@Composable
fun UserProfileScreenPreview() {
    LanchatTheme {
        UserProfileScreen(onUsernameSet = {})
    }
} 
