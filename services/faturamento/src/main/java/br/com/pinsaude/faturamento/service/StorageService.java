package br.com.pinsaude.faturamento.service;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:pinsaude-documentos}")
    private String bucket;

    public StorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String upload(String prefix, MultipartFile arquivo) {
        ensureBucket();
        String objectName = "%s/%d-%s"
            .formatted(prefix, Instant.now().toEpochMilli(), sanitize(arquivo.getOriginalFilename()));
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(arquivo.getInputStream(), arquivo.getSize(), -1)
                .contentType(arquivo.getContentType())
                .build());
            return objectName;
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Falha ao armazenar documento: " + e.getMessage());
        }
    }

    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
        } catch (Exception ignored) {
            // objeto órfão — não bloqueia a operação
        }
    }

    public String getPresignedUrl(String objectName) {
        ensureBucket();
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(bucket)
                .object(objectName)
                .expiry(1, TimeUnit.HOURS)
                .build());
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Não foi possível gerar URL de download: " + e.getMessage());
        }
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Storage indisponível: " + e.getMessage());
        }
    }

    private String sanitize(String filename) {
        if (filename == null) return "arquivo";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
