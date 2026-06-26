package com.rodgers.routist.di

import android.content.Context
import android.content.SharedPreferences
import com.rodgers.routist.db.AppDatabase
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.rodgers.routist.db.GeocodingCacheDao
import com.rodgers.routist.db.KnownAddressDao
import com.rodgers.routist.db.TenkoDao
import com.rodgers.routist.db.WorkRecordDao
import com.rodgers.routist.util.DeliveryGeofenceManager
import com.rodgers.routist.util.GeocodingApi
import com.rodgers.routist.util.GeocodingClient
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
    fun provideGeofencingClient(@ApplicationContext ctx: Context): GeofencingClient =
        LocationServices.getGeofencingClient(ctx)

    @Provides
    @Singleton
    fun provideDeliveryGeofenceManager(
        @ApplicationContext ctx: Context,
        client: GeofencingClient
    ): DeliveryGeofenceManager = DeliveryGeofenceManager(ctx, client)

    @Provides
    @Singleton
    fun provideGeocodingApi(): GeocodingApi = GeocodingClient
}
