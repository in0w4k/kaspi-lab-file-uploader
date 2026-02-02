package kaspi.lab.finalproject1.controller;

import kaspi.lab.finalproject1.entity.FileMetadata;
import kaspi.lab.finalproject1.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
    private final FileService fileService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Mono<FileMetadata> upload(@RequestPart("file") FilePart file) {
        return fileService.uploadFile(file);
    }
}
