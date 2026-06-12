package com.transparency.fxlens

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.transparency.fxlens.data.ListsRepository
import com.transparency.fxlens.data.Prefs
import com.transparency.fxlens.data.RatesRepository
import com.transparency.fxlens.data.db.FxDatabase

class FxLensApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val prefs = Prefs(context)
    val ratesRepository = RatesRepository(context)
    private val db = Room.databaseBuilder(context, FxDatabase::class.java, "fxlens.db").build()
    val listsRepository = ListsRepository(db.listsDao())
}
