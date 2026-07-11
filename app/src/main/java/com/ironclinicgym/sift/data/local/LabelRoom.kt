package com.ironclinicgym.sift.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ironclinicgym.sift.core.domain.SiftLabel
import com.ironclinicgym.sift.core.domain.ports.LabelStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: String,
    val mappingId: String,
    val name: String,
    val colorHex: String,
    val icon: String,
)

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE mappingId = :mappingId ORDER BY name")
    fun observe(mappingId: String): Flow<List<LabelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LabelEntity)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM labels WHERE id = :id")
    suspend fun get(id: String): LabelEntity?
}

class RoomLabelStore(private val dao: LabelDao) : LabelStore {
    override fun observe(mappingId: String): Flow<List<SiftLabel>> =
        dao.observe(mappingId).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(label: SiftLabel) = dao.insert(label.toEntity())
    override suspend fun update(label: SiftLabel) = dao.insert(label.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun get(id: String): SiftLabel? = dao.get(id)?.toDomain()

    private fun SiftLabel.toEntity() = LabelEntity(id, mappingId, name, colorHex, icon)
    private fun LabelEntity.toDomain() = SiftLabel(id, mappingId, name, colorHex, icon)
}
