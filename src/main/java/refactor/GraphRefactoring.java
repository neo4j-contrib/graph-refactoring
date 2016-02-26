package refactor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.PerformsWrites;
import org.neo4j.procedure.Procedure;

import static org.neo4j.helpers.collection.MapUtil.stringMap;

/**
 * This class contains a number of graph-refactorings
 * TODO batching
 */
public class GraphRefactoring {
    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, normally found under `data/log/console.log`
    @Context
    public Log log;

    /**
     * this procedure takes a list of node-id's and clones them with their labels and properties
     */
    @Procedure
    @PerformsWrites
    public Stream<Result> cloneNodes(@Name("nodeIds") List<Long> nodeIds, @Name("withRelationships") boolean withRelationships) {
        return nodeIds.stream().map((nodeId) -> {
            Result result = new Result(nodeId);
            try {
                Node node = db.getNodeById(nodeId);
                Node copy = copyProperties(node, copyLabels(node, db.createNode()));
                if (withRelationships) {
                    copyRelationships(node, copy,false);
                }
                return result.withOther(copy.getId());
            } catch (Exception e) {
                return result.withError(e);
            }
        });
    }

    /**
     * Merges the nodes with the same label, property and value onto the first found node.
     * The other nodes and relationships are deleted.
     */
    @Procedure
    @PerformsWrites
    public Stream<Result> mergeNodes(@Name("label") String label, @Name("property") String property, @Name("value") Object value) {
        ResourceIterator<Node> nodes = db.findNodes(Label.label(label), property, value);
        if (!nodes.hasNext()) return Stream.empty();
        Node first = nodes.next();
        if (!nodes.hasNext()) {
            return Stream.of(new Result(first.getId()));
        }
        return nodes.stream().map( (other) -> {
            Result result = new Result(first.getId());
            try {
                mergeNodes(other, first, true);
                return result.withOther(other.getId());
            } catch (Exception e) {
                return result.withError(e);
            }
        });
    }

    /**
     * Changes the relationship-type of a relationship by creating a new one between the two nodes
     * and deleting the old.
     */
    @Procedure
    @PerformsWrites
    public Stream<Result> changeType(@Name("relationshipId") long relId, @Name("newType") String newType) {
        Relationship rel = db.getRelationshipById(relId);
        Result result = new Result(rel.getId());
        try {
            Relationship newRel = rel.getStartNode().createRelationshipTo(rel.getEndNode(), RelationshipType.withName(newType));
            copyProperties(rel, newRel);
            rel.delete();
            return Stream.of(result.withOther(newRel.getId()));
        } catch (Exception e) {
            return Stream.of(result.withError(e));
        }
    }

    /**
     * Redirects a relationships to a new target node.
     */
    @Procedure
    @PerformsWrites
    public Stream<Result> redirectRelationship(@Name("relationshipId") long relId, @Name("newNodeId") long newNodeId, @Name("isEndNode") boolean isEndNode) {
        Relationship rel = db.getRelationshipById(relId);
        Node newNode = db.getNodeById(newNodeId);
        Result result = new Result(rel.getId());
        try {
            Relationship newRel = isEndNode ?
                    rel.getStartNode().createRelationshipTo(newNode, rel.getType()) :
                    rel.getEndNode().createRelationshipTo(newNode, rel.getType());
            copyProperties(rel,newRel);
            rel.delete();
            return Stream.of(result.withOther(newRel.getId()));
        } catch (Exception e) {
            return Stream.of(result.withError(e));
        }
    }

    @Procedure
    @PerformsWrites
    public Stream<Result> switchNodes(@Name("relId") long relId) {
        Relationship rel = db.getRelationshipById(relId);
        Node start = rel.getStartNode();
        Node end = rel.getEndNode();
        Relationship newRel = end.createRelationshipTo(start, RelationshipType.withName(rel.getType().toString()));
        copyProperties(rel, newRel);
        rel.delete();
        Result result = new Result(relId);
        result.withOther(newRel.getId());

        return Stream.of(result);
    }

    private Node mergeNodes(Node source, Node target, boolean delete) {
        copyRelationships(source, copyProperties(source, copyLabels(source, target)), delete);
        if (delete) source.delete();
        return target;
    }

    private Node copyRelationships(Node source, Node target, boolean delete) {
        for (Relationship rel : source.getRelationships()) {
            copyRelationship(rel, source, target);
            if (delete) rel.delete();
        }
        return target;
    }

    private Node copyLabels(Node source, Node target) {
        for (Label label : source.getLabels()) target.addLabel(label);
        return target;
    }

    private <T extends PropertyContainer> T copyProperties(T source, T target) {
        for (Map.Entry<String, Object> prop : source.getAllProperties().entrySet())
            target.setProperty(prop.getKey(), prop.getValue());
        return target;
    }

    private Relationship copyRelationship(Relationship rel, Node source, Node newSource) {
        return copyProperties(rel,newSource.createRelationshipTo(rel.getOtherNode(source), rel.getType()));
    }

    public static class Result {
        public long source;
        public long target;
        public String error;

        public Result(Long nodeId) {
            this.source = nodeId;
        }

        public Result withError(Exception e) {
            this.error = e.getMessage();
            return this;
        }

        public Result withOther(long nodeId) {
            this.target = nodeId;
            return this;
        }
    }
}
