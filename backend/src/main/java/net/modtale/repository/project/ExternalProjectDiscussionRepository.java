package net.modtale.repository.project;

import net.modtale.model.project.ExternalProjectDiscussion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ExternalProjectDiscussionRepository extends MongoRepository<ExternalProjectDiscussion, String> {
    @Query("{ 'comments._id': ?0 }")
    java.util.Optional<ExternalProjectDiscussion> findByCommentsId(String commentId);
}
