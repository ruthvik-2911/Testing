package org.jackfruit.keliri.repository;

import org.jackfruit.keliri.model.AuditLogEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends MongoRepository<AuditLogEntry, String> {
}
