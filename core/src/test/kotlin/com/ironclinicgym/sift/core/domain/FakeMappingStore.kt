package com.ironclinicgym.sift.core.domain

import com.ironclinicgym.sift.core.domain.ports.MappingStore
import com.ironclinicgym.sift.core.mapping.MappingSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [MappingStore] for tests. */
class FakeMappingStore(initial: MappingSet = MappingSet.EMPTY) : MappingStore {
    private val state = MutableStateFlow(initial)
    override suspend fun load(): MappingSet = state.value
    override suspend fun save(set: MappingSet) { state.value = set }
    override fun observe(): Flow<MappingSet> = state
}
