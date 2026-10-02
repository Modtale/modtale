package net.modtale.model.project;

public record RemoteReviewOrigin(String deploymentId,String callerScope) {
    public RemoteReviewOrigin {
        if(deploymentId==null || !deploymentId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || callerScope==null || !callerScope.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid remote review origin");
    }
}
