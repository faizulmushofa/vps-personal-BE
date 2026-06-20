package io.github.faizul.storage.download.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.faizul.storage.download.service.DownloadStorageService;
import io.github.faizul.storage.download.local.service.impl.DownloadStorageServiceImpl;

/**
 * Test konektivitas untuk DownloadStorageService (gRPC microservice).
 * StorageNode adalah microservice terpisah, sehingga kita hanya menguji
 * apakah interface dan implementasi tersedia, bukan fungsionalitas penuh.
 */
class DownloadStorageTest {

    @Test
    @DisplayName("DownloadStorageService interface harus tersedia")
    void interfaceExists() {
        assertThat(DownloadStorageService.class).isInterface();
    }

    @Test
    @DisplayName("DownloadStorageServiceImpl harus mengimplementasikan DownloadStorageService")
    void implementationExists() {
        assertThat(DownloadStorageService.class).isAssignableFrom(DownloadStorageServiceImpl.class);
    }

    @Test
    @DisplayName("DownloadStorageServiceImpl harus memiliki method downloadFile")
    void hasMethods() throws NoSuchMethodException {
        // downloadFile(Long, UUID)
        assertThat(DownloadStorageServiceImpl.class.getMethod("downloadFile",
                Long.class, java.util.UUID.class))
                .isNotNull();
    }
}
