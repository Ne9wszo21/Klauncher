package com.example.m3launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.m3launcher.data.AppsRepo
import com.example.m3launcher.data.LauncherState
import com.example.m3launcher.ui.HomeScreen
import com.example.m3launcher.ui.LauncherTheme

class HomeActivity : ComponentActivity() {
    private lateinit var state: LauncherState
    private lateinit var repo: AppsRepo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = LauncherState(applicationContext)
        repo = AppsRepo(applicationContext)
        setContent { LauncherTheme(state) { HomeScreen(state, repo) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) state.homeTick++
    }

    override fun onDestroy() {
        repo.close()
        super.onDestroy()
    }
}
