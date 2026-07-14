package com.launcher360v2.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.launcher360v2.data.model.FolderApp
import com.launcher360v2.data.model.FolderEntity
import com.launcher360v2.data.model.HomeCell

@Database(
    entities = [HomeCell::class, FolderEntity::class, FolderApp::class],
    version = 1,
    exportSchema = false
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun homeCellDao(): HomeCellDao
    abstract fun folderDao(): FolderDao
}
