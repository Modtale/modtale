package net.modtale.service.security.scan;

import net.modtale.model.project.ProjectDependency;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static net.modtale.service.security.scan.DependencyReviewGraph.*;
import static org.junit.jupiter.api.Assertions.*;

class DependencyReviewGraphTest {
    private static final String HASH="a".repeat(64),CONTEXT="b".repeat(64);
    private Snapshot node(String id,Dependency... dependencies) {
        return new Snapshot(id,id+"-version","1.0","files/"+id,HASH,CONTEXT,null,false,List.of(dependencies));
    }
    private Dependency dependency(String id) {return dependency(id,ProjectDependency.DependencyType.REQUIRED);}
    private Dependency dependency(String id,ProjectDependency.DependencyType type) {
        return new Dependency(ProjectDependency.Source.MODTALE,type,new Reference(id,"1.0"));
    }
    private Source source(Snapshot... snapshots) {
        var values=new HashMap<Reference,Snapshot>();for(var snapshot:snapshots)values.put(snapshot.reference(),snapshot);
        return ref->{var snapshot=values.get(ref);return new Lookup(snapshot==null?State.MISSING:State.FOUND,snapshot);};
    }
    private Inventory inspect(Snapshot root,Snapshot... nodes){return DependencyReviewGraph.inspect(root,source(nodes),Limits.defaults());}
    private void reason(Inventory inventory,Reason reason) {
        assertFalse(inventory.resolved());assertNull(inventory.identity());assertTrue(inventory.gaps().stream().anyMatch(g->g.reason()==reason),inventory.gaps().toString());
    }
    @Test void diamondInventoryRetainsEveryDeclarationAndReadsSharedVersionsOnceBeforeRevalidation() {
        var shared=node("shared");var left=node("left",dependency("shared"));var right=node("right",dependency("shared"));
        var root=node("root",dependency("left"),dependency("right",ProjectDependency.DependencyType.OPTIONAL),dependency("shared",ProjectDependency.DependencyType.EMBEDDED));
        var graph=inspect(root,root,left,right,shared);
        assertTrue(graph.resolved());assertEquals(4,graph.nodes().size());assertEquals(5,graph.edges().size());assertEquals(7,graph.reads());
        assertEquals(Set.of(ProjectDependency.DependencyType.REQUIRED,ProjectDependency.DependencyType.OPTIONAL,ProjectDependency.DependencyType.EMBEDDED),
                new HashSet<>(graph.edges().stream().map(e->e.dependency().type()).toList()));
        assertEquals(graph.identity(),inspect(root,shared,right,left,root).identity());
        assertThrows(UnsupportedOperationException.class,()->graph.nodes().clear());
    }
    @Test void changedArtifactAndContextProduceDifferentInventoriesEvenWithSameVersionLabel() {
        var child=node("child");var root=node("root",dependency("child"));var original=inspect(root,root,child);
        var bytes=new Snapshot(child.projectId(),child.versionId(),child.versionNumber(),child.fileReference(),"c".repeat(64),CONTEXT,null,false,List.of());
        var context=new Snapshot(child.projectId(),child.versionId(),child.versionNumber(),child.fileReference(),HASH,"d".repeat(64),null,false,List.of());
        assertNotEquals(original.identity(),inspect(root,root,bytes).identity());
        assertNotEquals(original.identity(),inspect(root,root,context).identity());
    }
    @Test void missingAmbiguousAndUnavailablePinsNeverReceiveAnIdentity() {
        var root=node("root",dependency("missing"));
        for(var state:List.of(State.MISSING,State.AMBIGUOUS,State.UNAVAILABLE)) {
            var graph=DependencyReviewGraph.inspect(root,ref->ref.equals(root.reference())?new Lookup(State.FOUND,root):new Lookup(state,null),Limits.defaults());
            reason(graph,Reason.valueOf(state.name()));
        }
        reason(DependencyReviewGraph.inspect(root,ref->{throw new IllegalStateException("Unavailable");},Limits.defaults()),Reason.UNAVAILABLE);
    }
    @Test void externalReferencesAreRepresentedWithoutCallingTheirSource() {
        var root=node("root",new Dependency(ProjectDependency.Source.WEBSITE,ProjectDependency.DependencyType.REQUIRED,new Reference("external","1.0")));
        var calls=new ArrayList<Reference>();var graph=DependencyReviewGraph.inspect(root,ref->{calls.add(ref);return new Lookup(State.FOUND,root);},Limits.defaults());
        reason(graph,Reason.EXTERNAL);assertEquals(List.of(root.reference()),calls);assertNull(graph.edges().getFirst().target());
    }
    @Test void cyclesAndEveryStructuralLimitAreExplicit() {
        var root=node("root",dependency("child"));var child=node("child",dependency("root"));
        reason(inspect(root,root,child),Reason.CYCLE);
        reason(DependencyReviewGraph.inspect(root,source(root,child),new Limits(1,256,8,128)),Reason.NODE_LIMIT);
        reason(DependencyReviewGraph.inspect(root,source(root,child),new Limits(64,256,0,128)),Reason.DEPTH_LIMIT);
        var many=node("root",dependency("a"),dependency("b"));
        reason(DependencyReviewGraph.inspect(many,source(many,node("a"),node("b")),new Limits(64,1,8,128)),Reason.EDGE_LIMIT);
        var limited=DependencyReviewGraph.inspect(root,source(root,node("child")),new Limits(64,256,8,1));
        reason(limited,Reason.READ_LIMIT);assertEquals(1,limited.reads());
    }
    @Test void sharedSubtreeCannotHideAPathBeyondTheDepthLimit() {
        var leaf=node("leaf");var shared=node("shared",dependency("leaf"));
        var detour=node("detour",dependency("shared"));
        var root=node("root",dependency("shared"),dependency("detour"));
        var graph=DependencyReviewGraph.inspect(root,source(root,shared,detour,leaf),new Limits(64,256,2,128));
        reason(graph,Reason.DEPTH_LIMIT);
        assertEquals(4,graph.edges().size());
        assertTrue(DependencyReviewGraph.inspect(root,source(root,shared,detour,leaf),new Limits(64,256,3,128)).resolved());
    }
    @Test void rootReplacementDuringInspectionInvalidatesTheInventory() {
        var root=node("root");
        var replacement=new Snapshot("root","different-version","1.0","file",HASH,CONTEXT,null,false,List.of());
        reason(DependencyReviewGraph.inspect(root,ref->new Lookup(State.FOUND,replacement),Limits.defaults()),Reason.CHANGED);
    }
    @Test void identityMismatchAndMutationDuringInspectionCannotResolve() {
        var root=node("root",dependency("child"));var child=node("child");
        reason(DependencyReviewGraph.inspect(root,ref->new Lookup(State.FOUND,root),Limits.defaults()),Reason.INVALID_IDENTITY);
        var reads=new AtomicInteger();
        var graph=DependencyReviewGraph.inspect(root,ref->{
            if(ref.equals(root.reference()))return new Lookup(State.FOUND,root);
            return reads.incrementAndGet()==1?new Lookup(State.FOUND,child):new Lookup(State.MISSING,null);
        },Limits.defaults());reason(graph,Reason.CHANGED);
        var wrongScan=new Snapshot("root","root-version","1.0","file",HASH,CONTEXT,new RecordedScan("e".repeat(64),HASH,"policy"),false,List.of());
        reason(inspect(wrongScan,wrongScan),Reason.INVALID_IDENTITY);
    }
    @Test void unresolvedContextAndSupplementalInputsRemainExplicit() {
        var root=new Snapshot("root","v","1.0","file",HASH,null,null,true,List.of());
        var graph=inspect(root,root);reason(graph,Reason.UNRESOLVED_CONTEXT);reason(graph,Reason.SUPPLEMENTAL_CONTENT);
    }
    @Test void inputCollectionsAreCopiedAndMalformedIdentitiesRejected() {
        var dependencies=new ArrayList<Dependency>();var root=new Snapshot("root","v","1.0","file",HASH,CONTEXT,null,false,dependencies);
        dependencies.add(dependency("unexpected"));assertTrue(root.dependencies().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new Reference("root",""));
        assertThrows(IllegalArgumentException.class,()->new Reference("root\nother","1"));
        assertThrows(IllegalArgumentException.class,()->new Limits(65,256,8,128));
        assertThrows(IllegalArgumentException.class,()->new Lookup(State.FOUND,null));
        assertThrows(IllegalArgumentException.class,()->new Snapshot("root","v","1","file","wrong",CONTEXT,null,false,List.of()));
    }
}
