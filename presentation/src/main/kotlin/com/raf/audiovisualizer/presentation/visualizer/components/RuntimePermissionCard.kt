package com.raf.audiovisualizer.presentation.visualizer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raf.audiovisualizer.presentation.R

/**
 * Runtime permission prompt requesting `RECORD_AUDIO` directly through Android's standard runtime
 * permission dialog (no ADB required for end users).
 */
@Composable
fun RuntimePermissionCard(
    modifier: Modifier = Modifier,
    primaryColor: Color,
    onRequestAudioPermission: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF141422).copy(alpha = 0.90f))
            .border(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(24.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.title_permission_audio),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.msg_permission_audio_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.74f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRequestAudioPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = Color.Black,
            ),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.action_grant_audio),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
