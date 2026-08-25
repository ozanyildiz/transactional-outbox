package com.transactionaloutbox.library.leadership;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticLeadershipProviderTest {

    @Test
    void reportsLeaderWhenConfiguredAsLeader() {
        StaticLeadershipProvider provider = new StaticLeadershipProvider(true);

        assertThat(provider.isLeader()).isTrue();
    }

    @Test
    void reportsNotLeaderWhenConfiguredAsFollower() {
        StaticLeadershipProvider provider = new StaticLeadershipProvider(false);

        assertThat(provider.isLeader()).isFalse();
    }
}
