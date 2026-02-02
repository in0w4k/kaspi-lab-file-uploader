package kaspi.lab.finalproject1.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "files")
public record FileMetadata (
        @Id UUID id,
        String fileName,
        String contentType,
        long size,
        String s3Key,

        @Indexed(unique = true)
        String hash,

        LocalDateTime createdAt
) {}