package com.stevanovicm32.mobilnoracunarstvo

import android.app.Application
import com.stevanovicm32.mobilnoracunarstvo.BuildConfig
import com.stevanovicm32.mobilnoracunarstvo.data.api.ApiClient
import com.stevanovicm32.mobilnoracunarstvo.data.auth.SessionManager
import com.stevanovicm32.mobilnoracunarstvo.data.local.TokenStore
import com.stevanovicm32.mobilnoracunarstvo.data.repository.AuthRepository
import com.stevanovicm32.mobilnoracunarstvo.data.repository.DropRepository
import com.stevanovicm32.mobilnoracunarstvo.data.repository.LeaderboardRepository
import com.stevanovicm32.mobilnoracunarstvo.domain.LocationTracker
import org.osmdroid.config.Configuration

class GameApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var dropRepository: DropRepository
        private set
    lateinit var leaderboardRepository: LeaderboardRepository
        private set
    lateinit var locationTracker: LocationTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        tokenStore = TokenStore(this)
        sessionManager = SessionManager(tokenStore)
        val api = ApiClient.create(tokenStore, sessionManager)
        authRepository = AuthRepository(api, tokenStore, sessionManager)
        dropRepository = DropRepository(api)
        leaderboardRepository = LeaderboardRepository(api)
        locationTracker = LocationTracker(this)
    }

    companion object {
        lateinit var instance: GameApp
            private set
    }
}
