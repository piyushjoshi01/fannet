package com.fannet.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need MongoDB. One shared single-node replica set per JVM,
 * so transactions and change streams behave as in production.
 */
@SpringBootTest
public abstract class AbstractMongoIntegrationTest {

    @ServiceConnection
    static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:8"));

    static {
        MONGO.start();
    }
}
