package com.stevanovicm32.mobilnoracunarstvo

import android.app.Application
import com.stevanovicm32.mobilnoracunarstvo.data.api.ApiClient
import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import com.stevanovicm32.mobilnoracunarstvo.data.repository.AuthRepository
import com.stevanovicm32.mobilnoracunarstvo.data.repository.DropRepository
import com.stevanovicm32.mobilnoracunarstvo.domain.LocationTracker

class GameApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var dropRepository: DropRepository
        private set
    lateinit var locationTracker: LocationTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        tokenStore = TokenStore(this)
        val api = ApiClient.create(tokenStore)
        authRepository = AuthRepository(api, tokenStore)
        dropRepository = DropRepository(api)
        locationTracker = LocationTracker(this)
    }

    companion object {
        lateinit var instance: GameApp
            private set
    }
}
