package com.transactionaloutbox.library.leadership;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticLeadershipProvider implements LeadershipProvider {

    private final boolean leader;

    public StaticLeadershipProvider(@Value("${outbox.leader:false}") boolean leader) {
        this.leader = leader;
    }

    @Override
    public boolean isLeader() {
        return leader;
    }
}
