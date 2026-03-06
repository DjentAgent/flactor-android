package com.psycode.spotiflac.domain.repository

interface LocalFileRepository {
    suspend fun deleteByUri(uri: String): Boolean
}
