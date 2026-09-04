# R8 (isMinifyEnabled = true, isShrinkResources = true) — Play verlangt Verschleierung
# für den Release-Build. Diese Datei enthält NUR Regeln für Stellen, die R8 ohne
# explizites Keep nachweislich brechen kann; alles andere (Compose, CameraX, ML Kit,
# Play Billing, Play Services Ads, UMP) bringt seine eigenen consumer-proguard-rules
# in der jeweiligen AAR mit und braucht hier keine Ergänzung.
#
# Bewusst NICHT gekeept, weil unnötig:
# - BillingRepository/Ads-Callbacks (AdListener, PurchasesUpdatedListener,
#   RewardedAdLoadCallback, FullScreenContentCallback, ConsentManager): normale
#   Interface-/Abstract-Class-Overrides, die das SDK virtuell aufruft — keine
#   Reflection, R8 benennt Aufrufer und Override konsistent um.
# - PickerSlot/CreateMode/PaywallContext (enum class): werden nur direkt verglichen
#   (when/==), nie über valueOf(String) oder Serialisierung by-name aufgelöst.
# - MainActivity/ScanConvertApp/FileProvider: in AndroidManifest.xml deklariert;
#   die Standard-AGP-Regeln keepen alle dort referenzierten Komponenten automatisch.
# - NativeAdView-Inflation aus res/layout/native_ad_card.xml: deckt bereits die in
#   proguard-android-optimize.txt enthaltene Standardregel für View-Subklassen ab.
# - Flaggen-PNGs (assets/flags/<code>.png): Assets werden von shrinkResources nicht
#   angefasst, der Zugriff läuft ohnehin über einen Datei-Pfad, keine Resource-ID.

# Room (data/db/Db.kt): Room.databaseBuilder() sucht die vom Compiler generierte
# FxDatabase_Impl zur Laufzeit über den Klassennamen (Reflection). Ohne Keep würde
# R8 die RoomDatabase-Subklasse umbenennen/entfernen → Crash beim ersten DB-Zugriff.
# Entities (TravelListEntity/ListItemEntity) und ListsDao brauchen dagegen KEIN
# eigenes Keep: Der Zugriff darauf ist vom Room-Compiler generierter, direkt
# verlinkter Code — R8 benennt Aufrufer (DAO-Impl) und Ziel (Entity-Felder) konsistent
# um, das ist kein Reflection-Zugriff.
-keep class * extends androidx.room.RoomDatabase

# kotlinx.serialization (data/CustomCurrency.kt): Prefs.kt liest/schreibt die
# Custom-Währungen-Liste über Json.decodeFromString<List<CustomCurrency>>(...) bzw.
# encodeToString(...) — reified Generics, die zur Laufzeit den generierten
# $serializer sowie den Companion-Zugriff der Datenklasse brauchen. Ohne Keep kann
# R8 diese als scheinbar unbenutzt entfernen → Absturz/Datenverlust beim Laden der
# Custom-Währungen. Regel bewusst auf die einzige @Serializable-Klasse der App
# beschränkt (kein App-weiter Wildcard).
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.jbateam.scanconvert.data.CustomCurrency$$serializer { *; }
-keepclassmembers class com.jbateam.scanconvert.data.CustomCurrency {
    *** Companion;
}
-keepclasseswithmembers class com.jbateam.scanconvert.data.CustomCurrency {
    kotlinx.serialization.KSerializer serializer(...);
}
