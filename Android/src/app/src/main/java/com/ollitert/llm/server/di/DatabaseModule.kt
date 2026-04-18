/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
