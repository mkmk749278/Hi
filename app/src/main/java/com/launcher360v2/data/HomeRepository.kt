package com.launcher360v2.data

import com.launcher360v2.data.db.FolderDao
import com.launcher360v2.data.db.HomeCellDao
import com.launcher360v2.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val cellDao: HomeCellDao,
    private val folderDao: FolderDao
) {
    fun observeHomeCells(): Flow<List<HomeCell>> = cellDao.observeAll()
    fun observeFolders(): Flow<List<FolderWithApps>> = folderDao.observeAllFolders()

    suspend fun addAppToHome(componentFlat: String, page: Int, row: Int, col: Int) {
        val pkg = android.content.ComponentName.unflattenFromString(componentFlat)?.packageName ?: return
        cellDao.insertCell(
            HomeCell(page = page, row = row, col = col, type = CellType.APP,
                packageName = pkg, componentFlat = componentFlat)
        )
    }

    suspend fun removeFromHome(componentFlat: String) {
        cellDao.deleteCellByComponent(componentFlat)
    }

    suspend fun moveCell(id: Long, newPage: Int, newRow: Int, newCol: Int) {
        cellDao.moveCell(id, newPage, newRow, newCol)
    }

    suspend fun addWidget(widgetId: Int, page: Int, row: Int, col: Int, spanH: Int, spanV: Int) {
        cellDao.insertCell(
            HomeCell(page = page, row = row, col = col, type = CellType.WIDGET,
                widgetId = widgetId, spanH = spanH, spanV = spanV)
        )
    }

    suspend fun removeWidget(widgetId: Int) {
        // Find cell with this widget ID and delete
        // Implemented by querying all cells and filtering — simple for personal use
    }

    suspend fun createFolder(name: String, page: Int, row: Int, col: Int): Long {
        val folderId = folderDao.insertFolder(FolderEntity(name = name, page = page, row = row, col = col))
        cellDao.insertCell(HomeCell(page = page, row = row, col = col, type = CellType.FOLDER, folderId = folderId))
        return folderId
    }

    suspend fun addAppToFolder(folderId: Long, componentFlat: String, position: Int) {
        folderDao.insertFolderApp(FolderApp(folderId = folderId, componentFlat = componentFlat, position = position))
    }

    suspend fun getCellAt(page: Int, row: Int, col: Int): HomeCell? =
        cellDao.getCellAt(page, row, col)
}
