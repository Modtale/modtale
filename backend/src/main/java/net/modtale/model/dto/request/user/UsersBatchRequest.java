package net.modtale.model.dto.request.user;

import java.util.List;

public class UsersBatchRequest {
    private List<String> userIds;

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}
