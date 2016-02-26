package refactor;

import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.Node;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Relationship;
import org.neo4j.driver.v1.ResultCursor;

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

        ResultCursor refactored = session.run("CALL refactor.cloneNodes([{id}], true)", parameters("id", nodeId));
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

    @Test
    public void testSwitchNodes() throws Throwable {
        long relId = session.run("CREATE (s:SwitchNode {id:1})-[r:CONNECTS {v:1}]->(s2:SwitchNode {id:2}) RETURN id(r)")
                .single().get(0).asLong();
        ResultCursor refactored = session.run("CALL refactor.switchNodes({id})", parameters("id", relId));
        Record record = refactored.single();
        long source = record.get("source").asLong();
        long target = record.get("target").asLong();
        assertThat(source, equalTo(relId));
        assertThat(target, not(equalTo(relId)));

        ResultCursor result = session.run("MATCH (s:SwitchNode)-[r:CONNECTS]->(s2:SwitchNode) RETURN r, s2");
        Record record1 = result.single();
        Relationship newRel = record1.get("r").asRelationship();
        Node node = record1.get("s2").asNode();
        assertThat(newRel.type(), equalTo("CONNECTS"));
        assertThat(1, equalTo(node.get("id").asInt()));
    }
}
