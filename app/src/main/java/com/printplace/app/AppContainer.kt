package com.printplace.app

import android.content.Context
import com.printplace.app.data.database.PrintPlaceDatabase
import com.printplace.app.data.repository.ModelRepository
import com.printplace.app.data.repository.ModelRepositoryImpl
import com.printplace.app.data.storage.ModelFileStorage

/**
 * Hand-rolled dependency container. A DI framework (Hilt/Koin) would be
 * overkill for the handful of singletons this app needs -- see project
 * guidance to keep third-party dependencies minimal.
 */
class AppContainer(context: Context) {

    private val database = PrintPlaceDatabase.create(context)
    private val fileStorage = ModelFileStorage(context)

    val modelRepository: ModelRepository = ModelRepositoryImpl(
        appContext = context,
        dao = database.modelDao(),
        storage = fileStorage,
    )
}
