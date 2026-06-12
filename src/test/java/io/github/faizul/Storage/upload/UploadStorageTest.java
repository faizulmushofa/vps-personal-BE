package io.github.faizul.Storage.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("UploadStorageImp harus mengimplementasikan UploadStorageService")
    void implementationExists() {
        assertThat(UploadStorageService.class).isAssignableFrom(UploadStorageImp.class);
    }

    @Test
    @DisplayName("UploadStorageImp harus memiliki method sendBatch, sendFinalSignal, dan deleteFile")
    void hasMethods() throws NoSuchMethodException {
        // sendBatch(Long, UUID, int, int)
        assertThat(UploadStorageImp.class.getMethod("sendBatch",
                Long.class, java.util.UUID.class, int.class, int.class))
                .isNotNull();

        // sendFinalSignal(Long, UUID, int)
        assertThat(UploadStorageImp.class.getMethod("sendFinalSignal",
                Long.class, java.util.UUID.class, int.class))
                .isNotNull();

        // deleteFile(Long, String)
        assertThat(UploadStorageImp.class.getMethod("deleteFile",
                Long.class, String.class))
                .isNotNull();
    }
}
