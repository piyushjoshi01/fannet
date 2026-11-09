package com.fannet.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fannet.support.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

class MongoMigrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    MongoTemplate mongo;

    @Autowired
    MongoTransactionManager txManager;

    @Test
    void firstMigrationRanAndIsRecorded() {
        assertThat(mongo.collectionExists("schema_info")).isTrue();
        assertThat(mongo.getCollection("mongockChangeLog")
                .countDocuments(new org.bson.Document("changeId", "init-schema")))
                .isEqualTo(1);
    }

    @Test
    void transactionsWorkOnTheReplicaSet() {
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                mongo.insert(new org.bson.Document("k", "v"), "tx_probe"));
        assertThat(mongo.count(new Query(), "tx_probe")).isEqualTo(1);
    }
}
