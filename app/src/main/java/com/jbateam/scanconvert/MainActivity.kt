package com.jbateam.scanconvert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jbateam.scanconvert.data.LocaleStore
import com.jbateam.scanconvert.ui.AppRoot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    /** Wendet die gewählte App-Sprache an, bevor Ressourcen geladen werden (§F5). */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Consent einmal pro frischem Start (§8.3) — nicht bei Recreate (z. B.
        // Sprachwechsel), sonst würde der Session-Zähler hochlaufen und das Formular
        // erneut erscheinen. Danach lädt das VM Ads NUR, wenn erlaubt, nicht werbefrei
        // und nicht in der ersten Session (§5/§11).
        if (savedInstanceState == null) {
            val container = (application as ScanConvertApp).container
            lifecycleScope.launch {
                val launchNo = container.prefs.incrementLaunchCount()
                container.consentManager.gatherConsent(this@MainActivity) { canRequestAds ->
                    vm.onConsentResolved(canRequestAds, firstSession = launchNo == 1)
                }
            }
        }

        setContent {
            var hasCamera by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                )
            }
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted -> hasCamera = granted }

            // Kamera-Berechtigung erst nach dem Onboarding anfragen (Scan-Screen folgt direkt).
            val onboarded by vm.onboarded.collectAsState()
            LaunchedEffect(onboarded) {
                if (onboarded == true && !hasCamera) {
                    launcher.launch(Manifest.permission.CAMERA)
                }
            }

            AppRoot(
                vm = vm,
                hasCameraPermission = hasCamera,
                onSetLanguage = { lang ->
                    LocaleStore.setLang(this@MainActivity, lang)
                    recreate()
                },
            )
        }
    }
}
