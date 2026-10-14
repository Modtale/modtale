package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewDiscovery;

/** A scoped read position, not permission to modify the referenced record. */
public final class ReviewStateDiagnosticCursor {
    private ReviewStateDiagnosticCursor() {}
    public static String encode(RemoteReviewDiscovery.Cursor cursor) {
        if(cursor==null)return null;
        return "d1."+(cursor.afterProject()?"a.":"v.")+ModerationQueueCursor.encode(
                new ModerationQueuePageReader.Cursor(cursor.projectId(),cursor.offset()));
    }
    public static RemoteReviewDiscovery.Cursor decode(String token) {
        if(token==null)return null;
        if(token.length()>810 || !(token.startsWith("d1.a.1.") || token.startsWith("d1.v.1.")))throw new IllegalArgumentException("Invalid diagnostic cursor");
        try {
            var position=ModerationQueueCursor.decode(token.substring(5));
            var cursor=new RemoteReviewDiscovery.Cursor(position.projectId(),Math.toIntExact(position.versionIndex()),token.charAt(3)=='a');
            if(!encode(cursor).equals(token))throw new IllegalArgumentException();
            return cursor;
        } catch(IllegalArgumentException | ArithmeticException invalid) {throw new IllegalArgumentException("Invalid diagnostic cursor");}
    }
}
