package io.github.faizul.storage.uploadunit.service.impl;

import io.github.faizul.storage.uploadunit.tracker.UploadUnitTracker;
import io.github.faizul.storage.uploadunit.service.UploadUnitWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UploadUnitServiceTest {

    @Mock private UploadUnitWriter uploadUnitWriter;
    @Mock private UploadUnitTracker inMemoryTracker;
    @Mock private FilePart filePart;

    @InjectMocks
    private UploadUnitServiceImpl uploadUnitService;

    @Nested
    @DisplayName("receiveUnit")
    class ReceiveUnitTests {

        @Test
        @DisplayName("should write chunk and mark as received")
        void receiveUnit_success() {
            UUID fileId = UUID.randomUUID();

            when(uploadUnitWriter.write(fileId, 0, filePart)).thenReturn(Mono.empty());

            StepVerifier.create(uploadUnitService.receiveUnit(fileId, 0, filePart))
                    .verifyComplete();

            verify(uploadUnitWriter).write(fileId, 0, filePart);
            verify(inMemoryTracker).markReceived(fileId, 0);
        }
    }

    @Nested
    @DisplayName("isUnitReceived")
    class IsUnitReceivedTests {

        @Test
        @DisplayName("should return true when chunk is received")
        void isUnitReceived_true() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.isReceived(fileId, 0)).thenReturn(true);

            StepVerifier.create(uploadUnitService.isUnitReceived(fileId, 0))
                    .assertNext(result -> assertThat(result).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return false when chunk is not received")
        void isUnitReceived_false() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.isReceived(fileId, 5)).thenReturn(false);

            StepVerifier.create(uploadUnitService.isUnitReceived(fileId, 5))
                    .assertNext(result -> assertThat(result).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getReceivedUnit")
    class GetReceivedUnitTests {

        @Test
        @DisplayName("should return count of received chunks")
        void getReceivedUnit_count() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.countReceived(fileId)).thenReturn(5);

            StepVerifier.create(uploadUnitService.getReceivedUnit(fileId))
                    .assertNext(count -> assertThat(count).isEqualTo(5))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("isComplete")
    class IsCompleteTests {

        @Test
        @DisplayName("should return true when all chunks are received")
        void isComplete_true() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.isComplete(fileId, 10)).thenReturn(true);

            StepVerifier.create(uploadUnitService.isComplete(fileId, 10))
                    .assertNext(result -> assertThat(result).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("claimBatch / claimCompletion")
    class ClaimTests {

        @Test
        @DisplayName("should delegate claimBatch to tracker")
        void claimBatch() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.claimBatch(fileId, 0)).thenReturn(true);

            StepVerifier.create(uploadUnitService.claimBatch(fileId, 0))
                    .assertNext(result -> assertThat(result).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("should delegate claimCompletion to tracker")
        void claimCompletion() {
            UUID fileId = UUID.randomUUID();
            when(inMemoryTracker.claimCompletion(fileId)).thenReturn(true);

            StepVerifier.create(uploadUnitService.claimCompletion(fileId))
                    .assertNext(result -> assertThat(result).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("cleanupMemory")
    class CleanupTests {

        @Test
        @DisplayName("should delegate cleanup to tracker")
        void cleanupMemory() {
            UUID fileId = UUID.randomUUID();

            StepVerifier.create(uploadUnitService.cleanupMemory(fileId))
                    .verifyComplete();

            verify(inMemoryTracker).cleanup(fileId);
        }
    }
}
