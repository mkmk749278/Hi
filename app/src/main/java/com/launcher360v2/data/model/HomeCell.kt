package com.launcher360v2.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one cell on the home screen grid.
 * page: 0-indexed page number (page 0 = Google Feed, page 1 = first home page)
 * row/col: 0-indexed position in the grid
 * span: number of cells the widget occupies (1 = normal icon)
 */
@Entity(tableName = "home_cells")
data class HomeCell(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val page: Int,
    val row: Int,
    val col: Int,
    val type: CellType,
    val packageName: String? = null,
    val componentFlat: String? = null,   // ComponentName.flattenToString()
    val widgetId: Int = -1,              // AppWidget ID, -1 if not a widget
    val folderId: Long = -1L,            // Room ID of FolderEntity, -1 if not a folder
    val spanH: Int = 1,                  // horizontal span (cells)
    val spanV: Int = 1                   // vertical span (cells)
)

enum class CellType { APP, WIDGET, FOLDER }
