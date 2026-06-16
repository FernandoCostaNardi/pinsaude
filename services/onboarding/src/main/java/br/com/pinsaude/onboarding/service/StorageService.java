package br.com.pinsaude.onboarding.service;

import io.minio.*;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Service
public class StorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:pinsaude-documentos}")
    private String bucket;

    public StorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public String upload(UUID medicoId, String tipoDocumento, MultipartFile arquivo) {
        ensureBucket();
        String objectName = "documentos/%s/%s/%d-%s"
            .formatted(medicoId, tipoDocumento, Instant.now().toEpochMilli(),
                sanitize(arquivo.getOriginalFilename()));
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
