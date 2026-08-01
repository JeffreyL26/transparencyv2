package com.jbateam.scanconvert

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Foto-Medien-Berechtigung über die verschiedenen API-Level (minSdk 26 … targetSdk 36):
 *  - API ≤ 32: READ_EXTERNAL_STORAGE
 *  - API 33:   READ_MEDIA_IMAGES
 *  - API 34+:  READ_MEDIA_IMAGES (voll) ODER READ_MEDIA_VISUAL_USER_SELECTED (Teilzugriff)
 */
object MediaPermissions {

    /** Welche Permissions beim Nutzer angefragt werden. */
    fun requested(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    /** Vollzugriff auf alle Foto-Medien. */
    fun hasFullAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> granted(context, Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /** Teilzugriff „Ausgewählte Fotos“ (nur Android 14+, nur ohne Vollzugriff). */
    fun hasPartialAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 34 &&
            !hasFullAccess(context) &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

    /** Galerie ist nutzbar, sobald Voll- ODER Teilzugriff besteht. */
    fun hasAnyAccess(context: Context): Boolean =
        hasFullAccess(context) || hasPartialAccess(context)
}
