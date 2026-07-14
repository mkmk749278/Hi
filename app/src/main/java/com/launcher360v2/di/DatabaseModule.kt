package com.launcher360v2.di

import android.content.Context
import androidx.room.Room
import com.launcher360v2.data.db.FolderDao
import com.launcher360v2.data.db.HomeCellDao
import com.launcher360v2.data.db.LauncherDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LauncherDatabase =
        Room.databaseBuilder(context, LauncherDatabase::class.java, "launcher_db")
            .fallbackToDestructiveMigration()   // OK for personal use
            .build()

    @Provides fun provideHomeCellDao(db: LauncherDatabase): HomeCellDao = db.homeCellDao()
    @Provides fun provideFolderDao(db: LauncherDatabase): FolderDao = db.folderDao()
}
