package net.modtale.service.security.scan;

import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ProjectDependency;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import static net.modtale.service.security.scan.DependencyReviewGraph.*;

/** One bounded inspection session. Recorded declarations do not authorize publication. */
public final class DependencyReviewSource implements Source {
    private static final int MAX_BYTES=256*1024, MAX_VERSIONS=4096;
    private final MongoCollection<Document> projects;
    private final LongSupplier clock;
    private final long started,budget;
    private int queries;
    public DependencyReviewSource(MongoTemplate mongo) {this(mongo,System::nanoTime,TimeUnit.SECONDS.toNanos(5));}
    DependencyReviewSource(MongoTemplate mongo,LongSupplier clock,long budget) {
        if(budget<=0||budget>TimeUnit.SECONDS.toNanos(5))throw new IllegalArgumentException("Invalid inspection budget");
        this.projects=mongo.getCollection("projects").withReadConcern(ReadConcern.MAJORITY).withReadPreference(ReadPreference.primary());
        this.clock=Objects.requireNonNull(clock);this.started=clock.getAsLong();this.budget=budget;
    }
    @Override public Lookup read(Reference reference) {return lookup(reference.projectId(),reference.versionNumber(),false);}
    public Lookup readRoot(String projectId,String versionId) {
        new Reference(projectId,versionId);
        return lookup(projectId,versionId,true);
    }
    private Lookup lookup(String projectId,String selector,boolean byId) {
        try {
            var ids=new ArrayList<Object>();ids.add(projectId);if(ObjectId.isValid(projectId))ids.add(new ObjectId(projectId));
            var projection=new Document("_id",1).append("versions",new Document("$map",new Document("input",new Document("$slice",List.of("$versions",MAX_VERSIONS+1)))
                    .append("as","v").append("in",new Document("id","$$v._id").append("label","$$v.versionNumber").append("unique",uniqueNames("$$v")))));
            var roots=query(new Document("_id",new Document("$in",ids)),projection);
            if(roots.isEmpty())return absent(State.MISSING);
            if(roots.size()!=1)return absent(State.AMBIGUOUS);
            var root=roots.getFirst();var versions=list(root.get("versions"));
            if(versions.size()>MAX_VERSIONS)return absent(State.UNAVAILABLE);
            int selected=-1;String id=null,label=null;Set<String> versionIds=new HashSet<>();
            for(int i=0;i<versions.size();i++) {
                var v=document(versions.get(i));if(!Boolean.TRUE.equals(v.get("unique")))throw new AmbiguousRecord();String candidateId=string(v,"id"),candidateLabel=string(v,"label");
                new Reference(projectId,candidateId);new Reference(projectId,candidateLabel);
                if(!versionIds.add(candidateId))return absent(State.AMBIGUOUS);
                if(byId?candidateId.equals(selector):candidateLabel.equalsIgnoreCase(selector)) {
                    if(selected>=0)return absent(State.AMBIGUOUS);
                    selected=i;id=candidateId;label=candidateLabel;
                }
            }
            if(selected<0)return absent(State.MISSING);
            var records=query(new Document("_id",root.get("_id")),new Document("_id",1)
                    .append("version",new Document("$arrayElemAt",List.of("$versions",selected))));
            if(records.size()!=1)return absent(State.UNAVAILABLE);
            var v=document(records.getFirst().get("version"));
            if(!id.equals(string(v,"_id"))||!label.equals(string(v,"versionNumber")))return absent(State.UNAVAILABLE);
            var result=snapshot(projectId,v);
            remaining();
            return new Lookup(State.FOUND,result);
        } catch(AmbiguousRecord ambiguous) {return absent(State.AMBIGUOUS);}
        catch(RuntimeException malformedOrUnavailable) {return absent(State.UNAVAILABLE);}
    }
    private List<Document> query(Document match,Document projection) {
        if(++queries>256)throw new IllegalStateException("Inspection query limit exceeded");
        long millis=Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining()));
        // Size-check on the server before a document crosses the wire. Missing/malformed arrays fail closed.
        var bounded=new Document("$replaceWith",new Document("$cond",List.of(
                new Document("$lte",List.of(new Document("$bsonSize","$$ROOT"),MAX_BYTES)),"$$ROOT",new Document("oversized",true))));
        // Projection would otherwise erase duplicate root fields before the driver could detect them.
        projection.append("sourceUnique",uniqueNames("$$ROOT"));
        var raw=projects.withDocumentClass(org.bson.RawBsonDocument.class).withTimeout(millis,TimeUnit.MILLISECONDS).aggregate(List.of(new Document("$match",match),
                new Document("$limit",2),new Document("$project",projection),bounded))
                .collation(Collation.builder().locale("simple").build()).maxTime(millis,TimeUnit.MILLISECONDS).into(new ArrayList<>());
        remaining();
        var result=new ArrayList<Document>();
        for(var stored:raw) {
            if(stored.getByteBuffer().remaining()>MAX_BYTES)throw new IllegalStateException("Inspection record exceeds limit");
            // Validate raw names before DocumentCodec can collapse repeated fields into map entries.
            try(var reader=new org.bson.BsonBinaryReader(stored.getByteBuffer().asNIO())) {validateDocument(reader,0);}
            var decoded=stored.decode(new org.bson.codecs.DocumentCodec());
            if(Boolean.FALSE.equals(decoded.get("sourceUnique")))throw new AmbiguousRecord();
            result.add(decoded);
        }
        if(result.stream().anyMatch(d->d.containsKey("oversized")))throw new IllegalStateException("Inspection record exceeds limit");
        return result;
    }
    private static Document uniqueNames(String expression) {
        var names=new Document("$map",new Document("input",new Document("$objectToArray",expression)).append("as","field").append("in","$$field.k"));
        return new Document("$eq",List.of(new Document("$size",names),new Document("$size",new Document("$setUnion",List.of(names,List.of())))));
    }
    private void validateDocument(org.bson.BsonBinaryReader reader,int depth) {
        if(depth>64)throw new IllegalArgumentException("Inspection document depth exceeds limit");
        remaining();reader.readStartDocument();var names=new HashSet<String>();
        while(reader.readBsonType()!=org.bson.BsonType.END_OF_DOCUMENT) {
            remaining();if(!names.add(reader.readName()))throw new AmbiguousRecord();validateValue(reader,depth+1);
        }
        reader.readEndDocument();
    }
    private void validateValue(org.bson.BsonBinaryReader reader,int depth) {
        if(depth>64)throw new IllegalArgumentException("Inspection document depth exceeds limit");
        remaining();
        if(reader.getCurrentBsonType()==org.bson.BsonType.DOCUMENT)validateDocument(reader,depth);
        else if(reader.getCurrentBsonType()==org.bson.BsonType.ARRAY) {
            reader.readStartArray();
            while(reader.readBsonType()!=org.bson.BsonType.END_OF_DOCUMENT)validateValue(reader,depth+1);
            reader.readEndArray();
        } else reader.skipValue();
    }
    private static final class AmbiguousRecord extends IllegalArgumentException {}
    private long remaining() {
        long remaining=budget-(clock.getAsLong()-started);
        if(remaining<=0)throw new IllegalStateException("Inspection deadline exceeded");return remaining;
    }
    private static Snapshot snapshot(String projectId,Document v) {
        var model=new ProjectVersion();model.setManifestId(optionalString(v,"manifestId"));model.setManifestVersion(optionalString(v,"manifestVersion"));
        var games=new ArrayList<String>();
        if(v.get("gameVersions")!=null)for(var game:list(v.get("gameVersions"))) {
            if(!(game instanceof String value)||value.length()>256)throw new IllegalArgumentException("Invalid game version");games.add(value);
        }
        model.setGameVersions(games);
        var dependencies=new ArrayList<Dependency>();var contextDependencies=new ArrayList<ProjectDependency>();
        if(v.get("dependencies")!=null)for(var item:list(v.get("dependencies"))) {
            var d=document(item);
            var source=d.get("source")==null?ProjectDependency.Source.MODTALE:ProjectDependency.Source.valueOf(string(d,"source"));
            var type=d.get("dependencyType")==null?ProjectDependency.DependencyType.REQUIRED:ProjectDependency.DependencyType.valueOf(string(d,"dependencyType"));
            String target=string(d,"projectId"),pin=string(d,"versionNumber");
            dependencies.add(new Dependency(source,type,new Reference(target,pin)));
            var context=new ProjectDependency(target,null,pin,type);context.setSource(source);contextDependencies.add(context);
        }
        model.setDependencies(contextDependencies);
        String override=optionalString(v,"overrideFileUrl");
        boolean supplemental=override!=null&&!override.isBlank()||v.get("modpackConfigs")!=null&&!list(v.get("modpackConfigs")).isEmpty();
        RecordedScan scan=null;
        if(v.get("scanResult")!=null) {
            var stored=document(v.get("scanResult"));
            if(stored.get("securityEvidence")!=null) {
                var evidence=document(stored.get("securityEvidence"));
                scan=new RecordedScan(string(evidence,"artifactSha256"),string(evidence,"contentSha256"),string(evidence,"policyVersion"));
            }
        }
        return new Snapshot(projectId,string(v,"_id"),string(v,"versionNumber"),string(v,"fileUrl"),string(v,"hash"),
                ArtifactReviewContext.fingerprint(model),scan,supplemental,dependencies);
    }
    private static Lookup absent(State state){return new Lookup(state,null);}
    private static Document document(Object value){if(value instanceof Document document)return document;throw new IllegalArgumentException("Invalid inspection record");}
    private static List<?> list(Object value){if(value instanceof List<?> list)return list;throw new IllegalArgumentException("Invalid inspection list");}
    private static String string(Document d,String key){if(d.get(key) instanceof String s)return s;throw new IllegalArgumentException("Invalid inspection identity");}
    private static String optionalString(Document d,String key){return d.get(key)==null?null:string(d,key);}
}
