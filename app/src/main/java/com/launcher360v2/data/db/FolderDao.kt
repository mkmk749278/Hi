package com.launcher360v2.data.db

import androidx.room.*
import com.launcher360v2.data.model.FolderApp
import com.launcher360v2.data.model.FolderEntity
import com.launcher360v2.data.model.FolderWithApps
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Transaction
    @Query("SELECT * FROM folders")
    fun observeAllFolders(): Flow<List<FolderWithApps>>

    @Transaction
    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderWithApps(folderId: Long): FolderWithApps?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolderApp(app: FolderApp): Long

    @Delete
    suspend fun removeFolderApp(app: FolderApp)

    @Query("DELETE FROM folder_apps WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)
}
