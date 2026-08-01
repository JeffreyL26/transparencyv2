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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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

            // Foto-Medien-Zugriff für die In-App-Galerie. Nach der Anfrage sowie beim
            // Rückkehr aus den System-Einstellungen (ON_START) neu auswerten.
            var hasMedia by remember { mutableStateOf(MediaPermissions.hasFullAccess(this)) }
            var mediaPartial by remember { mutableStateOf(MediaPermissions.hasPartialAccess(this)) }
            fun refreshMedia() {
                hasMedia = MediaPermissions.hasAnyAccess(this)
                mediaPartial = MediaPermissions.hasPartialAccess(this)
            }
            val mediaLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { refreshMedia() }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) refreshMedia()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AppRoot(
                vm = vm,
                hasCameraPermission = hasCamera,
                hasMediaPermission = hasMedia,
                mediaPartialAccess = mediaPartial,
                onRequestMediaPermission = { mediaLauncher.launch(MediaPermissions.requested()) },
                onSetLanguage = { lang ->
                    LocaleStore.setLang(this@MainActivity, lang)
                    recreate()
                },
            )
        }
    }
}
