package com.example.food_sharing.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.food_sharing.model.Message
import com.example.food_sharing.R
import com.example.food_sharing.ui.theme.DarkGrey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(navController: NavController, channelId: String, channelName: String) {
    Scaffold(
        containerColor = Color.Black
    ) {
        val viewModel: ChatViewModel = hiltViewModel()
        val chooserDialog = remember {
            mutableStateOf(false)
        }

        val cameraImageUri = remember {
            mutableStateOf<Uri?>(null)
        }

        val cameraImageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                cameraImageUri.value?.let {
                    viewModel.sendImageMessage(it, channelId)
                }
            }
        }

        val imageLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { viewModel.sendImageMessage(it, channelId) }
        }


        fun createImageUri(): Uri {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = ContextCompat.getExternalFilesDirs(
                navController.context, Environment.DIRECTORY_PICTURES
            ).first()
            return FileProvider.getUriForFile(navController.context,
                "${navController.context.packageName}.provider",
                File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
                    cameraImageUri.value = Uri.fromFile(this)
                })
        }

        val permissionLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    cameraImageLauncher.launch(createImageUri())
                }
            }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            LaunchedEffect(key1 = true) {
                viewModel.listenForMessages(channelId)
            }
            val messages = viewModel.message.collectAsState()
            ChatMessages(
                messages = messages.value,
                onSendMessage = { message ->
                    viewModel.sendMessage(channelId, message)
                },
                onImageClicked = {
                    chooserDialog.value = true
                },
                channelName = channelName,
                viewModel = viewModel,
                channelID = channelId
            )
        }

        if (chooserDialog.value) {
            ContentSelectionDialog(onCameraSelected = {
                chooserDialog.value = false
                if (navController.context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraImageLauncher.launch(createImageUri())
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }, onGallerySelected = {
                chooserDialog.value = false
                imageLauncher.launch("image/*")
            })
        }
    }
}


@Composable
fun ContentSelectionDialog(onCameraSelected: () -> Unit, onGallerySelected: () -> Unit) {
    AlertDialog(onDismissRequest = { },
        confirmButton = {
            TextButton(onClick = onCameraSelected) {
                Text(text = "Camera")
            }
        },
        dismissButton = {
            TextButton(onClick = onGallerySelected) {
                Text(text = "Gallery")
            }
        },
        title = { Text(text = "Select your source?") },
        text = { Text(text = "Would you like to pick an image from the gallery or use the") })
}
@Composable
fun ChatMessages(
    channelName: String,
    channelID: String,
    messages: List<Message>,
    onSendMessage: (String) -> Unit,
    onImageClicked: () -> Unit,
    viewModel: ChatViewModel
) {
    val hideKeyboardController = LocalSoftwareKeyboardController.current
    val msg = remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                ChannelItem(channelName = channelName, Modifier, true, onClick = {})
            }
            items(messages) { message ->
                message.text?.let { ChatBubble(message = it, isCurrentUser = message.senderId == viewModel.currentUserId) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkGrey)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                msg.value = ""
                onImageClicked()
            }) {
                Image(
                    painter = painterResource(id = R.drawable.attach),
                    contentDescription = "attach"
                )
            }

            TextField(
                value = msg.value,
                onValueChange = { msg.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Type a message") },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    hideKeyboardController?.hide()
                }),
                colors = TextFieldDefaults.colors().copy(
                    focusedContainerColor = DarkGrey,
                    unfocusedContainerColor = DarkGrey,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.White,
                    unfocusedPlaceholderColor = Color.White
                )
            )
            IconButton(onClick = {
                onSendMessage(msg.value)
                msg.value = ""
            }) {
                Image(
                    painter = painterResource(id = R.drawable.send),
                    contentDescription = "send"
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: String, isCurrentUser: Boolean) {
    val bubbleColor = if (isCurrentUser) Color.Blue else Color.Gray
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        Text(
            text = message,
            color = Color.White,
            modifier = Modifier
                .background(bubbleColor, shape = RoundedCornerShape(8.dp))
                .padding(16.dp)
        )
    }
}

@Composable
fun ChannelItem(
    channelName: String,
    modifier: Modifier,
    shouldShowCallButtons: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkGrey)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.Yellow.copy(alpha = 0.3f))
            ) {
                Text(
                    text = channelName[0].uppercase(),
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 35.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Text(
                text = channelName,
                modifier = Modifier.padding(8.dp),
                color = Color.White
            )
        }
    }
}
