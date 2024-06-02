package org.jitsi.jibri.config

import io.minio.MinioClient
import org.jitsi.jibri.MainConfig


class MinioClientConfig {

    private var minioClient: MinioClient

    init {
        minioClient = MinioClient.builder().endpoint(MainConfig.minioUrl).credentials(MainConfig.minioAccessKey, MainConfig.minioSecretKey).build()

    }

    fun getClient(): MinioClient {
        return minioClient
    }

    companion object {

        val BUCKET = "files"

        val STORE = "docker-gmeet/gss"
    }
}