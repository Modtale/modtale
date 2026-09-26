package net.modtale.service.security.scan;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static net.modtale.service.security.scan.DependencyReviewGraph.*;
import static net.modtale.service.security.scan.DependencyArtifactVerifier.State.*;
import static org.junit.jupiter.api.Assertions.*;

class DependencyArtifactVerifierTest {
    String hash(byte[] value)throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    Snapshot node(String id,String path,byte[] bytes)throws Exception {return new Snapshot("p",id,id,path,hash(bytes),"b".repeat(64),null,false,List.of());}
    Inventory inventory(Snapshot... nodes){return new Inventory(nodes[0].key(),List.of(nodes),List.of(),List.of(),"c".repeat(64),1);}
    @Test void verifiesStoredBytesWithSharedFileDeduplication()throws Exception {
        byte[] bytes={1,2,3};var calls=new AtomicInteger();
        var verifier=new DependencyArtifactVerifier(path->{calls.incrementAndGet();return new ByteArrayInputStream(bytes);},new Semaphore(2));
        var result=verifier.verify(inventory(node("a","file",bytes),node("b","file",bytes)),3,Duration.ofSeconds(1));
        assertTrue(result.matched());assertEquals(3,result.bytes());assertEquals(1,calls.get());assertEquals(hash(bytes),result.artifacts().getFirst().actualSha256());
        assertEquals("c".repeat(64),result.inventoryIdentity());
    }
    @Test void changedBytesAndConflictingSharedIdentitiesCannotMatch()throws Exception {
        byte[] bytes={1,2,3};var verifier=new DependencyArtifactVerifier(path->new ByteArrayInputStream(bytes),new Semaphore(2));
        var wrong=verifier.verify(inventory(node("a","file",new byte[]{4})),10,Duration.ofSeconds(1));
        assertEquals(MISMATCH,wrong.state());assertEquals(hash(bytes),wrong.artifacts().getFirst().actualSha256());
        var conflict=verifier.verify(inventory(node("a","file",bytes),node("b","file",new byte[]{4})),10,Duration.ofSeconds(1));
        assertEquals(MISMATCH,conflict.state());
    }
    @Test void enforcesCumulativeByteBudgetAndAbortsOversizedS3Streams()throws Exception {
        byte[] bytes={1,2,3};var streams=new ArrayList<software.amazon.awssdk.core.ResponseInputStream<?>>();
        var verifier=new DependencyArtifactVerifier(path->{
            var stream=org.mockito.Mockito.spy(new software.amazon.awssdk.core.ResponseInputStream<>(
                    software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build(),new ByteArrayInputStream(bytes)));
            streams.add(stream);return stream;
        },new Semaphore(2));
        var result=verifier.verify(inventory(node("a","first",bytes),node("b","second",bytes)),5,Duration.ofSeconds(2));
        assertEquals(BYTE_LIMIT,result.state());assertEquals(6,result.bytes());assertNull(result.artifacts().getLast().actualSha256());
        for(var stream:streams){org.mockito.Mockito.verify(stream).abort();org.mockito.Mockito.verify(stream,org.mockito.Mockito.never()).close();}
    }
    @Test void rejectsUnresolvedInventoriesWithoutOpeningStorage()throws Exception {
        var calls=new AtomicInteger();var verifier=new DependencyArtifactVerifier(path->{calls.incrementAndGet();throw new IOException();},new Semaphore(2));
        var root=node("a","file",new byte[]{1});
        var inventory=new Inventory(root.key(),List.of(root),List.of(),List.of(new Gap(root.key(),root.reference(),Reason.MISSING)),null,1);
        assertEquals(UNRESOLVED,verifier.verify(inventory).state());assertEquals(0,calls.get());
    }
    @Test void unavailableTruncatedAndNonProgressingStreamsDoNotVerify()throws Exception {
        var input=inventory(node("a","file",new byte[]{1,2}));
        var unavailable=new DependencyArtifactVerifier(path->{throw new IOException("Unavailable");},new Semaphore(2));
        assertEquals(UNAVAILABLE,unavailable.verify(input).state());
        var truncated=new DependencyArtifactVerifier(path->new ByteArrayInputStream(new byte[]{1}),new Semaphore(2));
        assertEquals(MISMATCH,truncated.verify(input).state());
        var zero=new DependencyArtifactVerifier(path->new InputStream(){public int read(){return 0;}public int read(byte[] b,int off,int len){return 0;}},new Semaphore(2));
        assertEquals(UNAVAILABLE,zero.verify(input).state());
    }
    @Test void callerTimeoutDoesNotReleaseCapacityForAStillBlockedRead()throws Exception {
        var release=new CountDownLatch(1);var closed=new CountDownLatch(1);var entered=new CountDownLatch(1);var capacity=new Semaphore(1);
        var verifier=new DependencyArtifactVerifier(path->new InputStream(){
            public int read(){
                entered.countDown();boolean finished=false;
                while(!finished)try{release.await();finished=true;}catch(InterruptedException ignored){}
                return -1;
            }
            public void close(){closed.countDown();}
        },capacity);
        var input=inventory(node("a","file",new byte[0]));
        try {
            assertEquals(TIME_LIMIT,verifier.verify(input,10,Duration.ofMillis(250)).state());
            assertTrue(entered.await(1,TimeUnit.SECONDS));assertEquals(0,capacity.availablePermits());
            assertEquals(BUSY,verifier.verify(input,10,Duration.ofMillis(250)).state());
        } finally {release.countDown();}
        assertTrue(closed.await(2,TimeUnit.SECONDS));assertTrue(capacity.tryAcquire(2,TimeUnit.SECONDS));capacity.release();
    }
    @Test void lateStorageOpenIsClosedWithoutReadingAfterTimeout()throws Exception {
        var release=new CountDownLatch(1);var closed=new CountDownLatch(1);var capacity=new Semaphore(1);var reads=new AtomicInteger();
        var verifier=new DependencyArtifactVerifier(path->{
            boolean finished=false;while(!finished)try{release.await();finished=true;}catch(InterruptedException ignored){}
            return new ByteArrayInputStream(new byte[]{1}){
                public synchronized int read(byte[] b,int off,int len){reads.incrementAndGet();return super.read(b,off,len);}
                public void close(){closed.countDown();}
            };
        },capacity);
        try {assertEquals(TIME_LIMIT,verifier.verify(inventory(node("a","file",new byte[]{1})),10,Duration.ofMillis(250)).state());}
        finally {release.countDown();}
        assertTrue(closed.await(2,TimeUnit.SECONDS));assertTrue(capacity.tryAcquire(2,TimeUnit.SECONDS));capacity.release();assertEquals(0,reads.get());
    }
    @Test void realStorageSdkStreamsMatchThenRecoverAfterAStalledResponse() throws Exception {
        byte[] bytes={1,2,3};var stall=new java.util.concurrent.atomic.AtomicBoolean();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/",exchange->{
            try {
                exchange.sendResponseHeaders(200,bytes.length);
                if(stall.get()) {exchange.getResponseBody().write(bytes[0]);exchange.getResponseBody().flush();entered.countDown();
                    try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                    exchange.getResponseBody().write(bytes,1,2);
                } else exchange.getResponseBody().write(bytes);
            } finally {exchange.close();}
        });server.start();
        try(var s3=software.amazon.awssdk.services.s3.S3Client.builder()
                .endpointOverride(java.net.URI.create("http://127.0.0.1:"+server.getAddress().getPort()))
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                        software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test-key","test-secret"))).build()) {
            var storage=new net.modtale.service.storage.StorageService(s3,new net.modtale.config.properties.AppR2Properties("bucket","","","",""),null);
            var capacity=new Semaphore(1);var verifier=new DependencyArtifactVerifier(storage::getStream,capacity);
            var input=inventory(node("a","file",bytes));
            assertTrue(verifier.verify(input,10,Duration.ofSeconds(5)).matched());
            stall.set(true);
            assertEquals(TIME_LIMIT,verifier.verify(input,10,Duration.ofMillis(500)).state());
            assertTrue(entered.await(2,TimeUnit.SECONDS));release.countDown();
            assertTrue(capacity.tryAcquire(3,TimeUnit.SECONDS));capacity.release();stall.set(false);
            assertTrue(verifier.verify(input,10,Duration.ofSeconds(5)).matched());
        } finally {release.countDown();server.stop(0);}
    }
    @Test void largeInputIsReadInSmallChunksAndClosed()throws Exception {
        byte[] bytes=new byte[1024*1024];new Random(123).nextBytes(bytes);var max=new AtomicInteger();var closed=new AtomicInteger();
        var verifier=new DependencyArtifactVerifier(path->new ByteArrayInputStream(bytes){
            public synchronized int read(byte[] b,int off,int len){max.accumulateAndGet(len,Math::max);return super.read(b,off,len);}
            public void close(){closed.incrementAndGet();}
        },new Semaphore(2));
        assertTrue(verifier.verify(inventory(node("a","file",bytes)),bytes.length,Duration.ofSeconds(2)).matched());
        assertTrue(max.get()<=64*1024);assertEquals(1,closed.get());
    }
}
