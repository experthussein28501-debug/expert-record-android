package com.khabir.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khabir.app.data.local.entity.ExpertProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpertProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(profile: ExpertProfileEntity)
    @Query("SELECT * FROM expert_profile WHERE id = :id LIMIT 1") fun observe(id: Int = ExpertProfileEntity.SINGLE_ROW_ID): Flow<ExpertProfileEntity?>
    @Query("SELECT * FROM expert_profile WHERE id = :id LIMIT 1") suspend fun get(id: Int = ExpertProfileEntity.SINGLE_ROW_ID): ExpertProfileEntity?
}

