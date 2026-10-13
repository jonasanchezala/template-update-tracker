package com.example.engagementupdate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.engagementupdate.domain.EngagementStatus;
import com.example.engagementupdate.domain.PendingRule;
import com.example.engagementupdate.domain.Version;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** R7: the pending table, including the edges. */
class PendingRuleTest {

    private static Arguments row(
            String name, EngagementStatus status, Integer latest, int applied, Integer declined, boolean pending) {
        return Arguments.of(name, status, latest, applied, declined, pending);
    }

    static Stream<Arguments> table() {
        EngagementStatus active = EngagementStatus.ACTIVE;
        return Stream.of(
                row("latest ahead of applied, no decline", active, 4, 2, null, true),
                row("latest equals applied", active, 2, 2, null, false),
                row("latest behind applied", active, 1, 2, null, false),
                row("declined through latest", active, 4, 2, 4, false),
                row("declined through beyond latest", active, 4, 2, 5, false),
                row("latest passes the declined version", active, 5, 2, 4, true),
                row("declined version below applied is ignored", active, 3, 2, 1, true),
                row("declined equals applied, latest equal", active, 2, 2, 2, false),
                row("latest ahead by one", active, 3, 2, null, true),
                row("latest unknown", active, null, 2, null, false),
                row("latest unknown, with a decline", active, null, 2, 3, false),
                row("archived, would otherwise be pending", EngagementStatus.ARCHIVED, 4, 2, null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    @DisplayName("R7 pending table")
    void pendingTable(String name, EngagementStatus status, Integer latest, int applied, Integer declined, boolean pending) {
        boolean actual = PendingRule.isPending(
                status,
                Optional.ofNullable(latest).map(Version::of),
                Version.of(applied),
                Optional.ofNullable(declined).map(Version::of));

        assertThat(actual).isEqualTo(pending);
    }
}
