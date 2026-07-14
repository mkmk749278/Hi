package com.launcher360v2.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val page: Int,
    val row: Int,
    val col: Int
)

@Entity(tableName = "folder_apps")
data class FolderApp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderId: Long,
    val componentFlat: String,
    val position: Int
)

// POJO for querying folders with their apps
data class FolderWithApps(
    @Embedded val folder: FolderEntity,
    @Relation(parentColumn = "id", entityColumn = "folderId")
    val apps: List<FolderApp>
)
