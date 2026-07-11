package com.ironclinicgym.sift.core.domain.ports

import com.ironclinicgym.sift.core.domain.SiftLabel
import kotlinx.coroutines.flow.Flow

interface LabelStore {
    fun observe(mappingId: String): Flow<List<SiftLabel>>
    suspend fun insert(label: SiftLabel)
    suspend fun update(label: SiftLabel)
    suspend fun delete(id: String)
    suspend fun get(id: String): SiftLabel?
}
