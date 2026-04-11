package com.torpedoes.smartsales.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.torpedoes.smartsales.data.db.AppDatabase
import com.torpedoes.smartsales.data.db.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "smartsales_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()

    @Provides @Singleton
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides @Singleton
    fun provideOrderDao(db: AppDatabase): OrderDao = db.orderDao()

    @Provides @Singleton
    fun provideCustomerDao(db: AppDatabase): CustomerDao = db.customerDao()
}