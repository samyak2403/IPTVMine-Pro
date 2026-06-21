package com.samyak.iptvminepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.samyak.iptvminepro.ui.screens.tv.SearchScreen
import com.samyak.iptvminepro.ui.theme.IPTVMineProTheme

class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVMineProTheme {
                val context = androidx.compose.ui.platform.LocalContext.current
                SearchScreen(
                    onChannelClick = { channel ->
                        if (channel.streamUrl.isEmpty()) {
                            android.widget.Toast.makeText(context, "This match is not live yet!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            com.samyak.player.PlayerActivity.start(context, channel.name, channel.streamUrl)
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
