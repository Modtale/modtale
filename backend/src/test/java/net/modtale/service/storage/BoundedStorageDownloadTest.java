package net.modtale.service.storage;

import net.modtale.config.properties.AppR2Properties;
import net.modtale.exception.StorageDownloadException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.ResponseInputStream;
import java.io.ByteArrayInputStream;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundedStorageDownloadTest {
    @Test void boundedDownloadChecksDeclaredAndActualLength()throws Exception {
        var s3=mock(S3Client.class);var storage=new StorageService(s3,new AppR2Properties("bucket","","","",""),null);
        var exact=spy(new ResponseInputStream<>(GetObjectResponse.builder().contentLength(3L).build(),new ByteArrayInputStream(new byte[]{1,2,3})));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(exact);assertArrayEquals(new byte[]{1,2,3},storage.downloadBounded("key",3));verify(exact).close();
        var tooLarge=spy(new ResponseInputStream<>(GetObjectResponse.builder().contentLength(4L).build(),new ByteArrayInputStream(new byte[]{1,2,3,4})));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(tooLarge);assertThrows(StorageDownloadException.class,()->storage.downloadBounded("key",3));verify(tooLarge).abort();
        var truncated=spy(new ResponseInputStream<>(GetObjectResponse.builder().contentLength(3L).build(),new ByteArrayInputStream(new byte[]{1})));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(truncated);assertThrows(StorageDownloadException.class,()->storage.downloadBounded("key",3));verify(truncated).abort();
    }
    @Test void missingLengthCannotBypassActualByteLimit() {
        var s3=mock(S3Client.class);var storage=new StorageService(s3,new AppR2Properties("bucket","","","",""),null);
        var response=spy(new ResponseInputStream<>(GetObjectResponse.builder().build(),new ByteArrayInputStream(new byte[]{1,2,3,4})));
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(response);assertThrows(StorageDownloadException.class,()->storage.downloadBounded("key",3));verify(response).abort();
        assertThrows(IllegalArgumentException.class,()->storage.downloadBounded("key",0));
    }
}
