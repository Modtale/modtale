package net.modtale.service.media;

import net.modtale.service.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.function.Consumer;

@Service
public class MediaUploadService {

    private final StorageService storageService;

    public MediaUploadService(StorageService storageService) {
        this.storageService = storageService;
    }

    public String uploadPublicUrl(MultipartFile file, String pathPrefix, Consumer<MultipartFile> validator) {
        return uploadPublicUrl(file, pathPrefix, validator, null);
    }

    public String uploadPublicUrl(MultipartFile file, String pathPrefix, Consumer<MultipartFile> validator, Runnable beforeUpload) {
        storageService.validateUploadSize(file);
        validator.accept(file);
        if (beforeUpload != null) {
            beforeUpload.run();
        }
        return storageService.getPublicUrl(storageService.upload(file, pathPrefix));
    }
}
