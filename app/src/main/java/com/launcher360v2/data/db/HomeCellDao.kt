package com.launcher360v2.data.db

import androidx.room.*
import com.launcher360v2.data.model.HomeCell
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeCellDao {

    @Query("SELECT * FROM home_cells ORDER BY page ASC, row ASC, col ASC")
    fun observeAll(): Flow<List<HomeCell>>

    @Query("SELECT * FROM home_cells WHERE page = :page ORDER BY row ASC, col ASC")
    suspend fun getCellsOnPage(page: Int): List<HomeCell>

    @Query("SELECT COUNT(DISTINCT page) FROM home_cells")
    suspend fun pageCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCell(cell: HomeCell): Long

    @Update
    suspend fun updateCell(cell: HomeCell)

    @Delete
    suspend fun deleteCell(cell: HomeCell)

    @Query("DELETE FROM home_cells WHERE componentFlat = :componentFlat")
    suspend fun deleteCellByComponent(componentFlat: String)

    @Query("UPDATE home_cells SET page = :newPage, row = :newRow, col = :newCol WHERE id = :id")
    suspend fun moveCell(id: Long, newPage: Int, newRow: Int, newCol: Int)

    @Query("SELECT * FROM home_cells WHERE page = :page AND row = :row AND col = :col LIMIT 1")
    suspend fun getCellAt(page: Int, row: Int, col: Int): HomeCell?
}
