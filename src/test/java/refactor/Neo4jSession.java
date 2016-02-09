package refactor;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.neo4j.driver.v1.*;
import org.neo4j.harness.ServerControls;
import org.neo4j.harness.TestServerBuilder;

import java.util.Map;

import static org.neo4j.bolt.BoltKernelExtension.Settings.connector;
import static org.neo4j.bolt.BoltKernelExtension.Settings.enabled;

public class Neo4jSession implements TestRule, Session {
    private final TestServerBuilder builder;
    private Session session;

    public Neo4jSession(TestServerBuilder builder) {
        this.builder = builder;
    }

    @Override
    public org.junit.runners.model.Statement apply(org.junit.runners.model.Statement statement, Description description) {
        return new org.junit.runners.model.Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (ServerControls sc = builder.withConfig(connector(0, enabled), "true").newServer();
                     Driver driver = GraphDatabase.driver(sc.boltURI());
                     Session s = session = driver.session()) {
                    statement.evaluate();
                }
            }
        };
    }

    @Override
    public Transaction beginTransaction() {
        return session.beginTransaction();
    }

    @Override
    public boolean isOpen() {
        return session.isOpen();
    }

    @Override
    public void close() {
        session.close();
    }

    @Override
    public ResultCursor run(String s, Map<String, Value> map) {
        return session.run(s, map);
    }

    @Override
    public ResultCursor run(String s) {
        return session.run(s);
    }

    @Override
    public ResultCursor run(Statement statement) {
        return session.run(statement);
    }

    @Override
    @Experimental
    public TypeSystem typeSystem() {
        return session.typeSystem();
    }
}
