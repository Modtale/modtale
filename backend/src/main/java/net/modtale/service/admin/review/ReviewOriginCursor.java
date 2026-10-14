package net.modtale.service.admin.review;

import net.modtale.service.security.scan.RemoteReviewDiscovery;

/** Origin inventory positions cannot be reused as scan-diagnostic positions. */
public final class ReviewOriginCursor {
    private ReviewOriginCursor() {}
    public static String encode(RemoteReviewDiscovery.Cursor cursor) {
        String encoded=ReviewStateDiagnosticCursor.encode(cursor);
        return encoded==null?null:"o1."+encoded.substring(3);
    }
    public static RemoteReviewDiscovery.Cursor decode(String token) {
        if(token==null)return null;
        if(token.length()>810 || !token.startsWith("o1."))throw new IllegalArgumentException("Invalid origin cursor");
        return ReviewStateDiagnosticCursor.decode("d1."+token.substring(3));
    }
}
