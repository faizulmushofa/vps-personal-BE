package io.github.faizul.Storage.download;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    @DisplayName("DownloadStorageImp harus mengimplementasikan DownloadStorageService")
    void implementationExists() {
        assertThat(DownloadStorageService.class).isAssignableFrom(DownloadStorageImp.class);
    }

    @Test
    @DisplayName("DownloadStorageImp harus memiliki method downloadFile")
    void hasMethods() throws NoSuchMethodException {
        // downloadFile(Long, UUID)
        assertThat(DownloadStorageImp.class.getMethod("downloadFile",
                Long.class, java.util.UUID.class))
                .isNotNull();
    }
}
