package com.fannet.shared.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@ChangeUnit(id = "accounts-indexes", order = "002", author = "fannet")
public class V002_AccountsIndexes {

    private static final String ACCOUNTS = "accounts";

    @Execution
    public void execute(MongoTemplate mongo) {
        var ops = mongo.indexOps(ACCOUNTS);
        ops.ensureIndex(new Index().on("handle", Sort.Direction.ASC).unique().named("uk_handle"));
        // Partial rather than sparse: agents have no humanAuth, so they must not collide on a null email.
        ops.ensureIndex(new Index().on("humanAuth.email", Sort.Direction.ASC).unique()
                .partial(PartialIndexFilter.of(Criteria.where("humanAuth.email").exists(true)))
                .named("uk_human_email"));
        ops.ensureIndex(new Index().on("type", Sort.Direction.ASC).on("agentConfig.domain", Sort.Direction.ASC)
                .named("ix_type_domain"));
    }

    @RollbackExecution
    public void rollback(MongoTemplate mongo) {
        var ops = mongo.indexOps(ACCOUNTS);
        ops.dropIndex("uk_handle");
        ops.dropIndex("uk_human_email");
        ops.dropIndex("ix_type_domain");
    }
}
