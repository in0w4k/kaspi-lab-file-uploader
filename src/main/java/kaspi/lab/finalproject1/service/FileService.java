package kaspi.lab.finalproject1.service;

import com.mongodb.DuplicateKeyException;
import kaspi.lab.finalproject1.entity.FileMetadata;
import kaspi.lab.finalproject1.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    private final S3AsyncClient s3Client;
    private final FileRepository fileRepository;

    @Value("${s3.bucket}")
    private String bucketName;

    public Mono<FileMetadata> uploadFile(FilePart filePart) {
        return Mono.fromCallable(() -> Files.createTempFile("upload_", "_" + filePart.filename()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(tempFile -> filePart.transferTo(tempFile)
                        .then(processFile(tempFile, filePart.filename(), Objects.requireNonNull(filePart.headers().getContentType()).toString()))
                        .doFinally(signal -> deleteTempFile(tempFile)));
    }

    private Mono<FileMetadata> processFile(Path tempFile, String originalFileName, String contentType) {
        return Mono.fromCallable(() -> {
                    String hash = calculateSha256(tempFile);
                    long size = Files.size(tempFile);
                    return new FileInfo(hash, size);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(fileInfo -> checkAndUpload(tempFile, originalFileName, contentType, fileInfo));
    }

    private Mono<FileMetadata> checkAndUpload(Path tempFile, String originalFileName, String contentType, FileInfo fileInfo) {
        return fileRepository.findByHash(fileInfo.hash())
                .flatMap(existing -> {
                    log.info("Duplicate detected by content hash: {}", fileInfo.hash());
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() -> uploadToS3AndSave(tempFile, originalFileName, contentType, fileInfo)));
    }

    private Mono<FileMetadata> uploadToS3AndSave(Path tempFile, String originalFileName, String contentType, FileInfo fileInfo) {
        String s3Key = UUID.randomUUID() + "_" + originalFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(fileInfo.size())
                .build();

        return Mono.fromFuture(() -> s3Client.putObject(putObjectRequest, AsyncRequestBody.fromFile(tempFile)))
                .flatMap(response -> {
                    log.info("Upload successful. Size: {} bytes", fileInfo.size());
                    FileMetadata metadata = new FileMetadata(
                            UUID.randomUUID(),
                            originalFileName,
                            contentType,
                            fileInfo.size(),
                            s3Key,
                            fileInfo.hash(),
                            LocalDateTime.now()
                    );
                    return fileRepository.save(metadata);
                })
                .onErrorResume(e -> {
                    if (e instanceof DuplicateKeyException || (e.getMessage() != null && e.getMessage().contains("E11000"))) {
                        log.warn("Race condition for hash {}. Cleaning up S3.", fileInfo.hash());
                        return cleanupS3(s3Key).then(fileRepository.findByHash(fileInfo.hash()));
                    }
                    log.error("Upload failed: {}", e.getMessage());
                    return cleanupS3(s3Key).then(Mono.error(e));
                });
    }

    private Mono<Void> cleanupS3(String key) {
        return Mono.fromFuture(s3Client.deleteObject(d -> d.bucket(bucketName).key(key))).then();
    }

    private void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", path, e);
        }
    }

    private String calculateSha256(Path path) throws IOException, java.security.NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private record FileInfo(String hash, long size) {}
}