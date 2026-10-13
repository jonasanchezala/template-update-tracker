package com.example.engagementupdate;

import static com.example.engagementupdate.World.E;
import static com.example.engagementupdate.World.FIRM_A;
import static com.example.engagementupdate.World.T;
import static com.example.engagementupdate.World.v;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.RecordSource;
import com.example.engagementupdate.store.InMemoryEngagementStore;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The store's conditional writes: what R1 and R6 lean on. */
class InMemoryEngagementStoreTest {

    private final InMemoryEngagementStore store = new InMemoryEngagementStore();

    private static EngagementRecord record(int applied, long seq) {
        return new EngagementRecord(
                FIRM_A, E, T, v(applied), Optional.empty(), EngagementStatus.ACTIVE, seq, RecordSource.HOOK);
    }

    @Test
    void putIfAbsentWritesOnlyOnce() {
        assertThat(store.putIfAbsent(record(1, 1))).isTrue();
        assertThat(store.putIfAbsent(record(9, 9))).isFalse();
        assertThat(store.find(FIRM_A, E)).contains(record(1, 1));
    }

    @Test
    void replaceIfSeqNewerNeedsStrictlyNewerSeq() {
        store.putIfAbsent(record(1, 5));

        assertThat(store.replaceIfSeqNewer(record(2, 5))).isFalse();
        assertThat(store.replaceIfSeqNewer(record(2, 4))).isFalse();
        assertThat(store.find(FIRM_A, E)).contains(record(1, 5));

        assertThat(store.replaceIfSeqNewer(record(2, 6))).isTrue();
        assertThat(store.find(FIRM_A, E)).contains(record(2, 6));
    }

    @Test
    void replaceIfSeqNewerDoesNotCreate() {
        assertThat(store.replaceIfSeqNewer(record(1, 1))).isFalse();
        assertThat(store.find(FIRM_A, E)).isEmpty();
    }
}
