package com.agentic.aichat.data.ai.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.agentic.aichat.data.ai.ModelDto
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

	@Query("SELECT * FROM models")
//	@Query("SELECT * FROM models ORDER BY name ASC")
	fun observeAll(): Flow<List<ModelDto>>

	@Query("SELECT * FROM models ORDER BY name ASC")
	suspend fun getAll(): List<ModelDto>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(models: List<ModelDto>)

	@Query("DELETE FROM models")
	suspend fun deleteAll()

	@Query("DELETE FROM models WHERE id NOT IN (:ids)")
	suspend fun deleteMissing(ids: List<String>)

	@Transaction
	suspend fun replaceAll(models: List<ModelDto>) {
		if (models.isEmpty()) {
			deleteAll()
		} else {
			upsertAll(models)
			deleteMissing(models.map { it.id })
		}
	}
}
