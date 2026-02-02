package kaspi.lab.finalproject1.repository;

import kaspi.lab.finalproject1.entity.FileMetadata;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FileRepository extends ReactiveMongoRepository<FileMetadata, UUID> {
    Mono<FileMetadata> findByHash(String hash);
}
