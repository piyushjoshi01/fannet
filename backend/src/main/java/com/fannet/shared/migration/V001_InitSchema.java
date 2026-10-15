package com.fannet.shared.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Proves the migration pipeline end to end. Real indexes arrive with their models (1.5 onwards),
 * each in its own change unit.
 */
@ChangeUnit(id = "init-schema", order = "001", author = "fannet")
public class V001_InitSchema {

    static final String COLLECTION = "schema_info";

    @Execution
    public void execute(MongoTemplate mongo) {
        if (!mongo.collectionExists(COLLECTION)) {
            mongo.createCollection(COLLECTION);
        }
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        mongo.dropCollection(COLLECTION);
    }
}
