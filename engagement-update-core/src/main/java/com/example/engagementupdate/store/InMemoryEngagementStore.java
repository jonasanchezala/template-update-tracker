package com.example.engagementupdate.store;

import com.example.engagementupdate.domain.DecisionRecord;
import com.example.engagementupdate.domain.EngagementId;
import com.example.engagementupdate.domain.EngagementRecord;
import com.example.engagementupdate.domain.FirmId;
import com.example.engagementupdate.domain.TemplateId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory stand-in for the database. {@code putIfAbsent} and {@code replaceIfSeqNewer} are atomic
 * per engagement because they run inside {@link ConcurrentHashMap} per-key operations, the way a
 * conditional write is atomic in DynamoDB.
 */
public final class InMemoryEngagementStore implements EngagementStore {

    private record Key(FirmId firmId, EngagementId engagementId) {}

    private final ConcurrentHashMap<Key, EngagementRecord> records = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<DecisionRecord> decisions = new CopyOnWriteArrayList<>();

    @Override
    public Optional<EngagementRecord> find(FirmId firmId, EngagementId engagementId) {
        return Optional.ofNullable(records.get(new Key(firmId, engagementId)));
    }

    @Override
    public boolean putIfAbsent(EngagementRecord record) {
        return records.putIfAbsent(new Key(record.firmId(), record.engagementId()), record) == null;
    }

    @Override
    public boolean replaceIfSeqNewer(EngagementRecord updated) {
        AtomicBoolean replaced = new AtomicBoolean(false);
        records.computeIfPresent(new Key(updated.firmId(), updated.engagementId()), (key, stored) -> {
            if (updated.lastSeq() > stored.lastSeq()) {
                replaced.set(true);
                return updated;
            }
            return stored;
        });
        return replaced.get();
    }

    @Override
    public List<EngagementRecord> findByFirm(FirmId firmId) {
        return records.values().stream().filter(r -> r.firmId().equals(firmId)).toList();
    }

    @Override
    public List<EngagementRecord> findByTemplate(TemplateId templateId) {
        return records.values().stream().filter(r -> r.templateId().equals(templateId)).toList();
    }

    @Override
    public void appendDecision(DecisionRecord decision) {
        decisions.add(decision);
    }

    @Override
    public List<DecisionRecord> decisionsByFirm(FirmId firmId) {
        return decisions.stream().filter(d -> d.firmId().equals(firmId)).toList();
    }
}
