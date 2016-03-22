package refactor;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.neo4j.driver.v1.Values.parameters;
import static org.neo4j.harness.TestServerBuilders.newInProcessBuilder;

public class GraphRefactoringTest {
    @Rule
    public Neo4jSession session = new Neo4jSession(newInProcessBuilder().withProcedure(GraphRefactoring.class));

    @Test
    public void shouldAllowCloningNodes() throws Throwable {
        long nodeId = session.run("CREATE (p:User {name:'Brookreson'}) RETURN id(p)")
                .single().get(0).asLong();

        StatementResult refactored = session.run("CALL refactor.cloneNodes([{id}], true)", parameters("id", nodeId));
        Record record = refactored.single();
        long source = record.get("source").asLong();
        long target = record.get("target").asLong();
        assertThat(source, equalTo(nodeId));
        assertThat(target, not(equalTo(nodeId)));

        long other = session.run("MATCH (p:User {name:'Brookreson'}) WHERE id(p) <> {id} RETURN id(p)",
                parameters("id", nodeId)).single().get(0).asLong();
        assertThat(other, not(equalTo(nodeId)));
        assertThat(other, equalTo(target));
    }
}
