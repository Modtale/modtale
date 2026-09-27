package net.modtale.service.security.scan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.model.project.ModpackConfigReference;
import net.modtale.model.project.ProjectDependency;
import org.junit.jupiter.api.Test;
import static net.modtale.service.security.scan.ModpackOverrideInspector.State.*;
import static org.junit.jupiter.api.Assertions.*;

class ModpackOverrideInspectorTest {
    private static final String PATH="overrides/Universe/mods/Example/config.json";
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static byte[] archive(String content) throws Exception {
        String hash=sha(content.getBytes(StandardCharsets.UTF_8));
        String manifest="{\"format\":\"modtale-configs\",\"formatVersion\":1,\"configs\":[{\"projectId\":\"mod-1\",\"source\":\"MODTALE\",\"path\":\""
                +PATH+"\",\"sha256\":\""+hash+"\"}]}";
        var out=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(PATH));zip.write(content.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("modtale.configs.json"));zip.write(manifest.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
        }
        return out.toByteArray();
    }
    private static List<ModpackConfigReference> refs(String content) throws Exception {
        return List.of(new ModpackConfigReference("mod-1","MODTALE",PATH,sha(content.getBytes(StandardCharsets.UTF_8))));
    }
    private static List<ProjectDependency> owners() {
        return List.of(new ProjectDependency("mod-1",null,"1"));
    }
    @Test void verifiesTheEntireStoredArchiveAndAllConfigAssociations() throws Exception {
        byte[] bytes=archive("{}");
        var inspector=new ModpackOverrideInspector(ref->new ByteArrayInputStream(bytes),new Semaphore(1));
        var result=inspector.inspect("override.zip",sha(bytes),refs("{}"),owners());
        assertEquals(MATCHED,result.state());assertEquals(sha(bytes),result.observedArchiveSha256());
        assertEquals(bytes.length,result.archiveBytes());assertEquals(PATH,result.files().getFirst().path());
        assertEquals("mod-1",result.files().getFirst().projectId());
        assertEquals(2,result.files().getFirst().bytes());
    }
    @Test void returnsBoundedUnicodeSafeConfigWindowsOnlyAfterFullArchiveMatch() throws Exception {
        String content="\"a😀b\"";byte[] bytes=archive(content);
        var inspector=new ModpackOverrideInspector(ref->new ByteArrayInputStream(bytes),new Semaphore(1));
        var first=inspector.inspectWindow("override.zip",sha(bytes),refs(content),owners(),PATH,0,3);
        assertEquals(MATCHED,first.state());assertEquals("\"a😀",first.window().content());
        assertEquals(0,first.window().start());assertEquals(3,first.window().end());
        assertEquals(content.codePointCount(0,content.length()),first.window().totalCharacters());
        assertEquals(refs(content).getFirst().sha256(),first.window().sha256());
        var next=inspector.inspectWindow("override.zip",sha(bytes),refs(content),owners(),PATH,3,2);
        assertEquals("b\"",next.window().content());
        assertEquals(INVALID_WINDOW,inspector.inspectWindow("override.zip",sha(bytes),refs(content),owners(),PATH,99,2).state());
        assertEquals(INVALID_WINDOW,inspector.inspectWindow("override.zip",sha(bytes),refs(content),owners(),"missing",0,2).state());
        var wrong=inspector.inspectWindow("override.zip","a".repeat(64),refs(content),owners(),PATH,0,2);
        assertEquals(HASH_MISMATCH,wrong.state());assertNull(wrong.window());
        assertThrows(IllegalArgumentException.class,
                ()->inspector.inspectWindow("override.zip",sha(bytes),refs(content),owners(),PATH,-1,2));
    }
    @Test void changedStorageConfigsOrOwnersCannotMatch() throws Exception {
        byte[] bytes=archive("{}");
        var inspector=new ModpackOverrideInspector(ref->new ByteArrayInputStream(bytes),new Semaphore(1));
        assertEquals(HASH_MISMATCH,inspector.inspect("override.zip","a".repeat(64),refs("{}"),owners()).state());
        assertEquals(CONFIG_MISMATCH,inspector.inspect("override.zip",sha(bytes),refs("{\"changed\":true}"),owners()).state());
        assertEquals(OWNER_MISMATCH,inspector.inspect("override.zip",sha(bytes),refs("{}"),List.of()).state());
        assertEquals(INVALID_ARCHIVE,new ModpackOverrideInspector(ref->new ByteArrayInputStream(new byte[]{1,2,3}),new Semaphore(1))
                .inspect("override.zip",sha(new byte[]{1,2,3}),refs("{}"),owners()).state());
        assertEquals(UNAVAILABLE,new ModpackOverrideInspector(ref->{throw new java.io.IOException("offline");},new Semaphore(1))
                .inspect("override.zip",sha(bytes),refs("{}"),owners()).state());
        assertEquals(UNAVAILABLE,new ModpackOverrideInspector(ref->new InputStream() {
            public int read() throws java.io.IOException { throw new java.io.IOException("storage read failed"); }
            public int read(byte[] b,int off,int len) throws java.io.IOException { throw new java.io.IOException("storage read failed"); }
        },new Semaphore(1)).inspect("override.zip",sha(bytes),refs("{}"),owners()).state());
    }
    @Test void timedOutStorageReadRetainsCapacityUntilTheWorkerExits() throws Exception {
        byte[] bytes=archive("{}");var release=new CountDownLatch(1);var entered=new CountDownLatch(1);var closed=new CountDownLatch(1);
        var capacity=new Semaphore(1);
        var inspector=new ModpackOverrideInspector(ref->new InputStream() {
            public int read() { return -1; }
            public int read(byte[] b,int off,int len) {
                entered.countDown();boolean done=false;
                while(!done)try { release.await();done=true; } catch(InterruptedException ignored) {}
                return -1;
            }
            public void close() { closed.countDown(); }
        },capacity);
        try {
            assertEquals(TIME_LIMIT,inspector.inspect("override.zip",sha(bytes),refs("{}"),owners(),Duration.ofMillis(200)).state());
            assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertEquals(BUSY,inspector.inspect("override.zip",sha(bytes),refs("{}"),owners(),Duration.ofMillis(200)).state());
        } finally { release.countDown(); }
        assertTrue(closed.await(2,TimeUnit.SECONDS));assertTrue(capacity.tryAcquire(2,TimeUnit.SECONDS));capacity.release();
    }
}
