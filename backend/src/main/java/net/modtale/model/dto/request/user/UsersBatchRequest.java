package net.modtale.model.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UsersBatchRequest {
    @Size(max = 100, message = "You can request at most 100 users at a time.")
    private List<@NotBlank(message = "User ids in a batch request cannot be blank.") String> userIds;

    public List<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<String> userIds) {
        this.userIds = userIds;
    }
}
