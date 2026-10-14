package net.modtale.service.security.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.modtale.model.project.ProjectDependency;
import java.security.MessageDigest;
import java.util.*;

/** Immutable declaration inventory. Resolution is not a security clearance or proof of current storage bytes. */
public final class DependencyReviewGraph {
    public record Reference(String projectId, String versionNumber) {
        public Reference { text(projectId,128); text(versionNumber,128); }
    }
    public record Dependency(ProjectDependency.Source source, ProjectDependency.DependencyType type, Reference reference) {
        public Dependency { Objects.requireNonNull(source); Objects.requireNonNull(type); Objects.requireNonNull(reference); }
    }
    public record RecordedScan(String artifactSha256, String contentSha256, String policyVersion) {
        public RecordedScan { digest(artifactSha256); digest(contentSha256); text(policyVersion,256); }
    }
    public record Snapshot(String projectId, String versionId, String versionNumber, String fileReference,
            String artifactSha256, String contextSha256, RecordedScan recordedScan,
            boolean supplementalContent, List<Dependency> dependencies) {
        public Snapshot {
            text(projectId,128); text(versionId,128); text(versionNumber,128); text(fileReference,2048);
            digest(artifactSha256); if(contextSha256!=null)digest(contextSha256);
            dependencies=List.copyOf(dependencies);
            if(dependencies.size()>256)throw new IllegalArgumentException("Dependency inventory exceeds limit");
        }
        Reference reference(){return new Reference(projectId,versionNumber);}
        Key key(){return new Key(projectId,versionId);}
    }
    public record Key(String projectId,String versionId) {}
    public enum State { FOUND, MISSING, AMBIGUOUS, UNAVAILABLE }
    public record Lookup(State state, Snapshot snapshot) {
        public Lookup {
            Objects.requireNonNull(state);
            if((state==State.FOUND)!=(snapshot!=null))throw new IllegalArgumentException("Invalid dependency lookup");
        }
    }
    @FunctionalInterface public interface Source { Lookup read(Reference reference); }
    public record Limits(int nodes,int edges,int depth,int reads) {
        public Limits {
            if(nodes<1||nodes>64||edges<1||edges>256||depth<0||depth>8||reads<1||reads>128)
                throw new IllegalArgumentException("Invalid dependency graph limits");
        }
        public static Limits defaults(){return new Limits(64,256,8,128);}
    }
    public enum Reason { MISSING, AMBIGUOUS, UNAVAILABLE, EXTERNAL, CYCLE, DEPTH_LIMIT, NODE_LIMIT,
        EDGE_LIMIT, READ_LIMIT, CHANGED, INVALID_IDENTITY, UNRESOLVED_CONTEXT, SUPPLEMENTAL_CONTENT }
    public record Gap(Key from,Reference reference,Reason reason) {}
    public record Edge(Key from,int declaration,Dependency dependency,Key target) {}
    public record Inventory(Key root,List<Snapshot> nodes,List<Edge> edges,List<Gap> gaps,String identity,int reads) {
        public Inventory {nodes=List.copyOf(nodes);edges=List.copyOf(edges);gaps=List.copyOf(gaps);}
        public boolean resolved(){return gaps.isEmpty()&&identity!=null;}
    }
    private DependencyReviewGraph() {}
    public static Inventory inspect(Snapshot root,Source source,Limits limits) {
        Objects.requireNonNull(root);Objects.requireNonNull(source);Objects.requireNonNull(limits);
        return new Walker(source,limits).inspect(root);
    }
    private static final class Walker {
        final Source source;final Limits limits;
        final Map<Key,Snapshot> nodes=new LinkedHashMap<>();
        final Map<Reference,Lookup> lookups=new LinkedHashMap<>();
        final List<Edge> edges=new ArrayList<>();final List<Gap> gaps=new ArrayList<>();
        int reads;
        Walker(Source source,Limits limits){this.source=source;this.limits=limits;}
        Inventory inspect(Snapshot root) {
            lookups.put(root.reference(),new Lookup(State.FOUND,root));
            visit(root,0,new HashSet<>());
            checkSharedPathDepth(root.key());
            // Re-read every resolved pin, including the root. Callers must still bind this inventory at publication.
            for(var entry:lookups.entrySet()) {
                if(entry.getValue().state()!=State.FOUND)continue;
                var fresh=read(entry.getKey(),root.key());
                if(fresh==null)break;
                if(!entry.getValue().equals(fresh))gap(root.key(),entry.getKey(),Reason.CHANGED);
            }
            var ordered=nodes.values().stream().sorted(Comparator.comparing(Snapshot::projectId).thenComparing(Snapshot::versionId)).toList();
            String identity=null;
            if(gaps.isEmpty()) {
                try {
                    byte[] canonical=new ObjectMapper().writeValueAsBytes(List.of("dependency-inventory-1",root.key(),ordered,edges));
                    identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
                }catch(Exception failure){throw new IllegalStateException("Cannot bind dependency inventory",failure);}
            }
            return new Inventory(root.key(),ordered,edges,gaps,identity,reads);
        }
        void checkSharedPathDepth(Key root) {
            // A shared node may first be visited through a shorter path. Validate all retained paths,
            // without expanding a diamond graph exponentially or duplicating its declarations.
            Map<Key,Integer> depths=new HashMap<>();depths.put(root,0);
            for(int round=0;round<=limits.depth();round++) {
                Map<Key,Integer> next=new HashMap<>(depths);
                boolean changed=false;
                for(var edge:edges) {
                    Integer parent=depths.get(edge.from());
                    if(parent==null||edge.target()==null)continue;
                    int depth=parent+1;
                    if(depth>limits.depth()) {
                        gap(edge.from(),edge.dependency().reference(),Reason.DEPTH_LIMIT);return;
                    }
                    if(depth>next.getOrDefault(edge.target(),-1)) {
                        next.put(edge.target(),depth);changed=true;
                    }
                }
                depths=next;if(!changed)return;
            }
        }
        void visit(Snapshot node,int depth,Set<Key> path) {
            if(path.contains(node.key())){gap(node.key(),node.reference(),Reason.CYCLE);return;}
            var previous=nodes.get(node.key());
            if(previous!=null){if(!previous.equals(node))gap(node.key(),node.reference(),Reason.CHANGED);return;}
            if(nodes.size()>=limits.nodes()){gap(node.key(),node.reference(),Reason.NODE_LIMIT);return;}
            nodes.put(node.key(),node);path.add(node.key());
            if(node.contextSha256()==null)gap(node.key(),node.reference(),Reason.UNRESOLVED_CONTEXT);
            if(node.supplementalContent())gap(node.key(),node.reference(),Reason.SUPPLEMENTAL_CONTENT);
            if(node.recordedScan()!=null&&!node.artifactSha256().equals(node.recordedScan().artifactSha256()))
                gap(node.key(),node.reference(),Reason.INVALID_IDENTITY);
            for(int i=0;i<node.dependencies().size();i++) {
                var dependency=node.dependencies().get(i);
                if(edges.size()>=limits.edges()){gap(node.key(),dependency.reference(),Reason.EDGE_LIMIT);break;}
                int edge=edges.size();edges.add(new Edge(node.key(),i,dependency,null));
                if(dependency.source()!=ProjectDependency.Source.MODTALE){gap(node.key(),dependency.reference(),Reason.EXTERNAL);continue;}
                if(depth>=limits.depth()){gap(node.key(),dependency.reference(),Reason.DEPTH_LIMIT);continue;}
                var lookup=lookups.get(dependency.reference());
                if(lookup==null) {
                    lookup=read(dependency.reference(),node.key());
                    if(lookup==null)break;
                    lookups.put(dependency.reference(),lookup);
                }
                if(lookup.state()!=State.FOUND){gap(node.key(),dependency.reference(),Reason.valueOf(lookup.state().name()));continue;}
                var child=lookup.snapshot();
                if(!child.projectId().equals(dependency.reference().projectId())
                        || !child.versionNumber().equalsIgnoreCase(dependency.reference().versionNumber())) {
                    gap(node.key(),dependency.reference(),Reason.INVALID_IDENTITY);continue;
                }
                edges.set(edge,new Edge(node.key(),i,dependency,child.key()));
                visit(child,depth+1,path);
            }
            path.remove(node.key());
        }
        Lookup read(Reference reference,Key from) {
            if(reads>=limits.reads()){gap(from,reference,Reason.READ_LIMIT);return null;}
            reads++;
            try {return Objects.requireNonNull(source.read(reference));}
            catch(RuntimeException failure){return new Lookup(State.UNAVAILABLE,null);}
        }
        void gap(Key from,Reference reference,Reason reason){gaps.add(new Gap(from,reference,reason));}
    }
    private static void text(String value,int max) {
        if(value==null||value.isBlank()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid dependency identity");
    }
    private static void digest(String value){if(value==null||!value.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid dependency digest");}
}
