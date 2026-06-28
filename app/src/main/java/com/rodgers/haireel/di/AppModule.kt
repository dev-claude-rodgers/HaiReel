package com.rodgers.haireel.di

import android.content.Context
import android.content.SharedPreferences
import com.rodgers.haireel.db.AppDatabase
import com.rodgers.haireel.db.GeocodingCacheDao
import com.rodgers.haireel.db.KnownAddressDao
import com.rodgers.haireel.db.TenkoDao
import com.rodgers.haireel.db.WorkRecordDao
import com.rodgers.haireel.util.GeocodingApi
import com.rodgers.haireel.util.GeocodingClient
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideTenkoDao(db: AppDatabase): TenkoDao = db.tenkoDao()

    @Provides
    @Singleton
    fun provideWorkRecordDao(db: AppDatabase): WorkRecordDao = db.workRecordDao()

    @Provides
    @Singleton
    fun provideGeocodingCacheDao(db: AppDatabase): GeocodingCacheDao = db.geocodingCacheDao()

    @Provides
    @Singleton
    fun provideKnownAddressDao(db: AppDatabase): KnownAddressDao = db.knownAddressDao()

    @Provides
    @Singleton
    fun provideGeocodingApi(): GeocodingApi = GeocodingClient
}
