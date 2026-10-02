package net.modtale.service.admin.review;

import org.bson.types.ObjectId;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Base64;

public final class ModerationQueueCursor {
    private ModerationQueueCursor() {}
    public static String encode(ModerationQueuePageReader.Cursor cursor) {
        if(cursor==null)return null;
        Object id=cursor.projectId();String type=id instanceof ObjectId?"o":"s";
        String value=id instanceof ObjectId objectId?objectId.toHexString():(String)id;
        if(!StandardCharsets.UTF_8.newEncoder().canEncode(value))throw new IllegalArgumentException("Invalid queue cursor");
        String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return (cursor.filter()==ModerationQueuePageReader.Filter.ALL?"1.":"2."+cursor.filter().name()+".")+type+"."+cursor.versionIndex()+"."+encoded;
    }
    public static ModerationQueuePageReader.Cursor decode(String token) {
        if(token==null)return null;
        if(token.length()>800 || !token.matches("(1|2\\.(SECURITY|OPERATIONS))\\.[os]\\.(0|[1-9][0-9]{0,7})\\.[A-Za-z0-9_-]{1,684}"))throw new IllegalArgumentException("Invalid queue cursor");
        try {
            boolean filtered=token.startsWith("2.");
            var parts=token.split("\\.");
            var filter=filtered?ModerationQueuePageReader.Filter.valueOf(parts[1]):ModerationQueuePageReader.Filter.ALL;
            var fields=filtered?new String[]{parts[0],parts[2],parts[3],parts[4]}:parts;
            byte[] bytes=Base64.getUrlDecoder().decode(fields[3]);
            String value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            Object id=value;
            if(fields[1].equals("o")) {
                if(!value.matches("[0-9a-f]{24}"))throw new IllegalArgumentException("Invalid queue cursor");
                id=new ObjectId(value);
            }
            var cursor=new ModerationQueuePageReader.Cursor(id,Long.parseLong(fields[2]),filter);
            if(!encode(cursor).equals(token))throw new IllegalArgumentException("Invalid queue cursor");
            return cursor;
        } catch(CharacterCodingException | IllegalArgumentException invalid) {throw new IllegalArgumentException("Invalid queue cursor");}
    }
}
