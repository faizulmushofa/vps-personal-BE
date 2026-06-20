package io.github.faizul.storage.upload.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.faizul.storage.upload.service.UploadStorageService;
import io.github.faizul.storage.upload.local.service.impl.UploadStorageServiceImpl;

/**
 * Test konektivitas untuk UploadStorageService (gRPC microservice).
 * StorageNode adalah microservice terpisah, sehingga kita hanya menguji
 * apakah interface dan implementasi tersedia, bukan fungsionalitas penuh.
 */
class UploadStorageTest {

    @Test
    @DisplayName("UploadStorageService interface harus tersedia")
    void interfaceExists() {
        assertThat(UploadStorageService.class).isInterface();
    }

    @Test
    @DisplayName("UploadStorageServiceImpl harus mengimplementasikan UploadStorageService")
    void implementationExists() {
        assertThat(UploadStorageService.class).isAssignableFrom(UploadStorageServiceImpl.class);
    }

    @Test
    @DisplayName("UploadStorageServiceImpl harus memiliki method sendBatch, sendFinalSignal, dan deleteFile")
    void hasMethods() throws NoSuchMethodException {
        // sendBatch(Long, UUID, int, int)
        assertThat(UploadStorageServiceImpl.class.getMethod("sendBatch",
                Long.class, java.util.UUID.class, int.class, int.class))
                .isNotNull();

        // sendFinalSignal(Long, UUID, int)
        assertThat(UploadStorageServiceImpl.class.getMethod("sendFinalSignal",
                Long.class, java.util.UUID.class, int.class))
                .isNotNull();

        // deleteFile(Long, String)
        assertThat(UploadStorageServiceImpl.class.getMethod("deleteFile",
                Long.class, String.class))
                .isNotNull();
    }
}
