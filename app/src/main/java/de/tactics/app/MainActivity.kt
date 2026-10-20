package de.tactics.app

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import de.tactics.app.ui.LockScreen
import de.tactics.app.ui.TacticsApp
import de.tactics.app.ui.TacticsTheme

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(true)
    private var lastStop = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val lockEnabled = AppPrefs(this).lockEnabled
        applySecureFlag(lockEnabled)
        unlocked = !lockEnabled
        setContent {
            TacticsTheme {
                if (unlocked) TacticsApp() else LockScreen(onUnlock = { authenticate() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = AppPrefs(this)
        if (!prefs.lockEnabled) {
            unlocked = true
            return
        }
        // Nach mehr als einer Minute im Hintergrund erneut sperren
        if (unlocked && lastStop > 0 && System.currentTimeMillis() - lastStop > 60_000) unlocked = false
        if (!unlocked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    private fun authenticate() {
        Security.prompt(
            this, "Futlytix entsperren",
            onSuccess = { unlocked = true },
            onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        )
    }
}
