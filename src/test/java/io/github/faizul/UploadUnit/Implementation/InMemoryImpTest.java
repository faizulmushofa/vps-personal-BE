package io.github.faizul.UploadUnit.Implementation;

import io.github.faizul.UploadUnit.UploadUnitTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryImpTest {

    private InMemoryImp tracker;

    @BeforeEach
    void setUp() {
        tracker = new InMemoryImp();
    }

    @Nested
    @DisplayName("markReceived / isReceived / countReceived")
    class ReceiveTests {

        @Test
        @DisplayName("should mark chunk as received and verify")
        void markAndCheck() {
            UUID fileId = UUID.randomUUID();

            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 1);

            assertThat(tracker.isReceived(fileId, 0)).isTrue();
            assertThat(tracker.isReceived(fileId, 1)).isTrue();
            assertThat(tracker.isReceived(fileId, 2)).isFalse();
            assertThat(tracker.countReceived(fileId)).isEqualTo(2);
        }

        @Test
        @DisplayName("should return false for unknown file")
        void isReceived_unknownFile() {
            UUID unknownId = UUID.randomUUID();
            assertThat(tracker.isReceived(unknownId, 0)).isFalse();
            assertThat(tracker.countReceived(unknownId)).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("isComplete")
    class IsCompleteTests {

        @Test
        @DisplayName("should return true when all chunks are received")
        void isComplete_true() {
            UUID fileId = UUID.randomUUID();
            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 1);
            tracker.markReceived(fileId, 2);

            assertThat(tracker.isComplete(fileId, 3)).isTrue();
        }

        @Test
        @DisplayName("should return false when not all chunks are received")
        void isComplete_false() {
            UUID fileId = UUID.randomUUID();
            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 1);

            assertThat(tracker.isComplete(fileId, 3)).isFalse();
        }
    }

    @Nested
    @DisplayName("claimBatch")
    class ClaimBatchTests {

        @Test
        @DisplayName("should claim batch successfully first time")
        void claimBatch_firstTime() {
            UUID fileId = UUID.randomUUID();
            assertThat(tracker.claimBatch(fileId, 0)).isTrue();
        }

        @Test
        @DisplayName("should reject duplicate batch claim")
        void claimBatch_duplicate() {
            UUID fileId = UUID.randomUUID();
            assertThat(tracker.claimBatch(fileId, 0)).isTrue();
            assertThat(tracker.claimBatch(fileId, 0)).isFalse();
        }

        @Test
        @DisplayName("should allow different batch indices")
        void claimBatch_differentIndices() {
            UUID fileId = UUID.randomUUID();
            assertThat(tracker.claimBatch(fileId, 0)).isTrue();
            assertThat(tracker.claimBatch(fileId, 1)).isTrue();
        }
    }

    @Nested
    @DisplayName("claimCompletion")
    class ClaimCompletionTests {

        @Test
        @DisplayName("should claim completion successfully first time")
        void claimCompletion_firstTime() {
            UUID fileId = UUID.randomUUID();
            assertThat(tracker.claimCompletion(fileId)).isTrue();
        }

        @Test
        @DisplayName("should reject duplicate completion claim")
        void claimCompletion_duplicate() {
            UUID fileId = UUID.randomUUID();
            assertThat(tracker.claimCompletion(fileId)).isTrue();
            assertThat(tracker.claimCompletion(fileId)).isFalse();
        }
    }

    @Nested
    @DisplayName("isBatchReady")
    class IsBatchReadyTests {

        @Test
        @DisplayName("should return true when all batch chunks received")
        void isBatchReady_true() {
            UUID fileId = UUID.randomUUID();
            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 1);
            tracker.markReceived(fileId, 2);

            assertThat(tracker.isBatchReady(fileId, 0, 2)).isTrue();
        }

        @Test
        @DisplayName("should return false when some batch chunks missing")
        void isBatchReady_false() {
            UUID fileId = UUID.randomUUID();
            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 2);

            assertThat(tracker.isBatchReady(fileId, 0, 2)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown file")
        void isBatchReady_unknownFile() {
            UUID unknownId = UUID.randomUUID();
            assertThat(tracker.isBatchReady(unknownId, 0, 2)).isFalse();
        }
    }

    @Nested
    @DisplayName("cleanup")
    class CleanupTests {

        @Test
        @DisplayName("should remove all tracking data for file")
        void cleanup_removesEverything() {
            UUID fileId = UUID.randomUUID();

            tracker.markReceived(fileId, 0);
            tracker.markReceived(fileId, 1);
            tracker.claimBatch(fileId, 0);
            tracker.claimCompletion(fileId);

            tracker.cleanup(fileId);

            assertThat(tracker.isReceived(fileId, 0)).isFalse();
            assertThat(tracker.countReceived(fileId)).isEqualTo(0);
            assertThat(tracker.claimBatch(fileId, 0)).isTrue(); // can claim again
            assertThat(tracker.claimCompletion(fileId)).isTrue(); // can claim again
        }

        @Test
        @DisplayName("should not affect other files")
        void cleanup_doesNotAffectOtherFiles() {
            UUID fileA = UUID.randomUUID();
            UUID fileB = UUID.randomUUID();

            tracker.markReceived(fileA, 0);
            tracker.markReceived(fileB, 0);

            tracker.cleanup(fileA);

            assertThat(tracker.isReceived(fileA, 0)).isFalse();
            assertThat(tracker.isReceived(fileB, 0)).isTrue();
        }
    }
}
