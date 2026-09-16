package net.modtale.service.security.scan;

import net.modtale.service.storage.StorageService;
import java.io.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Bounded observations of stored bytes, never a review decision or publication capability. */
public final class DependencyArtifactVerifier {
    public enum State { MATCHED, MISMATCH, UNAVAILABLE, BYTE_LIMIT, TIME_LIMIT, BUSY, UNRESOLVED }
    public record Artifact(String fileReference,String expectedSha256,String actualSha256,long bytes,State state) {}
    public record Result(String inventoryIdentity,List<Artifact> artifacts,State state,long bytes) {
        public Result {artifacts=List.copyOf(artifacts);}
        public boolean matched(){return state==State.MATCHED;}
    }
    @FunctionalInterface interface Open {InputStream open(String reference)throws IOException;}
    private static final Semaphore CAPACITY=new Semaphore(2);
    private final Open open;
    private final Semaphore capacity;
    public DependencyArtifactVerifier(StorageService storage){this(storage::getStream,CAPACITY);}
    DependencyArtifactVerifier(Open open,Semaphore capacity){this.open=Objects.requireNonNull(open);this.capacity=capacity;}
    public Result verify(DependencyReviewGraph.Inventory inventory) {
        return verify(inventory,128L*1024*1024,Duration.ofSeconds(15));
    }
    Result verify(DependencyReviewGraph.Inventory inventory,long maxBytes,Duration timeout) {
        Objects.requireNonNull(inventory);
        long nanos=timeout.toNanos();
        if(maxBytes<1||maxBytes>128L*1024*1024||nanos<1||nanos>TimeUnit.SECONDS.toNanos(15))throw new IllegalArgumentException("Invalid byte inspection limits");
        if(!inventory.resolved()||inventory.nodes().isEmpty()||inventory.nodes().size()>64)
            return new Result(inventory.identity(),List.of(),State.UNRESOLVED,0);
        if(!capacity.tryAcquire())return new Result(inventory.identity(),List.of(),State.BUSY,0);
        var future=new CompletableFuture<Result>();
        long started=System.nanoTime();
        Thread worker;
        try {
            worker=Thread.ofVirtual().name("dependency-byte-inspection").start(()->{
                try {future.complete(read(inventory,maxBytes,started,nanos));}
                catch(Exception failure){future.complete(new Result(inventory.identity(),List.of(),State.UNAVAILABLE,0));}
                finally {capacity.release();}
            });
        } catch(RuntimeException failure){capacity.release();throw failure;}
        try {
            var result=future.get(Math.max(1,nanos-(System.nanoTime()-started)),TimeUnit.NANOSECONDS);
            return expired(started,nanos)?new Result(inventory.identity(),List.of(),State.TIME_LIMIT,0):result;
        }
        catch(TimeoutException failure){worker.interrupt();return new Result(inventory.identity(),List.of(),State.TIME_LIMIT,0);}
        catch(InterruptedException failure){worker.interrupt();Thread.currentThread().interrupt();return new Result(inventory.identity(),List.of(),State.TIME_LIMIT,0);}
        catch(ExecutionException failure){return new Result(inventory.identity(),List.of(),State.UNAVAILABLE,0);}
        // A stalled stream retains its capacity slot until it actually exits, even after caller timeout.
    }
    private Result read(DependencyReviewGraph.Inventory inventory,long maxBytes,long started,long nanos)throws Exception {
        var artifacts=new ArrayList<Artifact>();var seen=new HashMap<String,Artifact>();long total=0;
        for(var node:inventory.nodes()) {
            if(expired(started,nanos))return new Result(inventory.identity(),artifacts,State.TIME_LIMIT,total);
            var previous=seen.get(node.fileReference());
            if(previous!=null) {
                if(!previous.expectedSha256().equals(node.artifactSha256())) {
                    artifacts.add(new Artifact(node.fileReference(),node.artifactSha256(),previous.actualSha256(),0,State.MISMATCH));
                    return new Result(inventory.identity(),artifacts,State.MISMATCH,total);
                }
                continue;
            }
            if(total>=maxBytes)return new Result(inventory.identity(),artifacts,State.BYTE_LIMIT,total);
            long bytes=0;String actual=null;State state;
            InputStream stream=null;
            try {
                stream=open.open(node.fileReference());
                var digest=MessageDigest.getInstance("SHA-256");var buffer=new byte[64*1024];
                while(true) {
                    if(expired(started,nanos))throw new TimeLimit();
                    int read=stream.read(buffer,0,(int)Math.min(buffer.length,maxBytes-total-bytes+1));
                    if(expired(started,nanos))throw new TimeLimit();
                    if(read<0)break;
                    if(read==0)throw new IOException("Stored artifact stream made no progress");
                    bytes+=read;
                    if(bytes>maxBytes-total)throw new ByteLimit();
                    digest.update(buffer,0,read);
                }
                actual=HexFormat.of().formatHex(digest.digest());
                state=actual.equals(node.artifactSha256())?State.MATCHED:State.MISMATCH;
            } catch(ByteLimit failure){state=State.BYTE_LIMIT;}
            catch(TimeLimit failure){state=State.TIME_LIMIT;}
            catch(Exception failure){state=State.UNAVAILABLE;}
            finally {
                if(stream!=null) {
                    // S3 close may drain the remaining object; abort instead to preserve transfer bounds.
                    if(stream instanceof software.amazon.awssdk.core.ResponseInputStream<?> response)response.abort();
                    else stream.close();
                }
            }
            total+=bytes;var artifact=new Artifact(node.fileReference(),node.artifactSha256(),actual,bytes,state);
            artifacts.add(artifact);seen.put(node.fileReference(),artifact);
            if(state!=State.MATCHED)return new Result(inventory.identity(),artifacts,state,total);
        }
        return new Result(inventory.identity(),artifacts,State.MATCHED,total);
    }
    private static boolean expired(long started,long nanos){return Thread.currentThread().isInterrupted()||System.nanoTime()-started>=nanos;}
    private static class ByteLimit extends IOException {}
    private static class TimeLimit extends IOException {}
}
