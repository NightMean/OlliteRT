package com.ollitert.llm.server.di

import android.content.Context
import androidx.room.Room
import com.ollitert.llm.server.data.db.OlliteDatabase
import com.ollitert.llm.server.data.db.RequestLogDao
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt module providing the Room database and related dependencies. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

  @Provides
  @Singleton
  fun provideDatabase(@ApplicationContext context: Context): OlliteDatabase =
    Room.databaseBuilder(context, OlliteDatabase::class.java, "ollite.db")
      .fallbackToDestructiveMigration(dropAllTables = true) // safe for a log DB — hybrid schema avoids migrations normally
      .build()

  @Provides
  fun provideRequestLogDao(db: OlliteDatabase): RequestLogDao = db.requestLogDao()

  @Provides
  @Singleton
  fun provideMoshi(): Moshi = Moshi.Builder().build()
}
