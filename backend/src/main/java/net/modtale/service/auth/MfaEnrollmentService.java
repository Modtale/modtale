package net.modtale.service.auth;

import java.util.ArrayList;
import java.util.List;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.User;
import net.modtale.util.MongoIdUtils;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MfaEnrollmentService {
    private final MongoTemplate mongo;
    private final TwoFactorService factors;

    public MfaEnrollmentService(MongoTemplate mongo, TwoFactorService factors) {
        this.mongo = mongo;
        this.factors = factors;
    }

    public String begin(String userId) {
        Document current = capture(userId);
        requireDisabled(current);
        String secret = factors.generateNewSecret();
        if (secret == null || secret.isBlank()) throw new IllegalStateException("Could not create an authentication factor.");
        update(current, new Document("mfaSecret", secret));
        return secret;
    }

    public void verify(String userId, String code) {
        Document current = capture(userId);
        requireDisabled(current);
        String secret = current.getString("mfaSecret");
        if (secret == null || secret.isBlank() || !factors.isOtpValid(secret, code)) {
            throw new InvalidAuthenticationRequestException("That verification code was not accepted, so two-factor authentication was not enabled.");
        }
        update(current, new Document("mfaEnabled", true));
    }

    private Document capture(String id) {
        if (id == null || id.isBlank()) throw new ResourceNotFoundException("User not found.");
        var matches = mongo.getCollection(mongo.getCollectionName(User.class))
                .find(new Document("_id", new Document("$in", MongoIdUtils.expandIds(List.of(id)))))
                .limit(2).into(new ArrayList<>());
        if (matches.size() != 1 || matches.getFirst().get("deletedAt") != null)
            throw new ResourceNotFoundException("User not found.");
        return matches.getFirst();
    }

    private void requireDisabled(Document current) {
        Object enabled = current.get("mfaEnabled");
        if (enabled != null && !Boolean.FALSE.equals(enabled)) {
            throw new InvalidAuthenticationRequestException("Two-factor authentication is already enabled or requires account recovery.");
        }
    }

    private void update(Document current, Document fields) {
        var result = mongo.getCollection(mongo.getCollectionName(User.class)).updateOne(
                new Document("_id", current.get("_id")).append("$expr",
                        new Document("$eq", List.of("$$ROOT", new Document("$literal", current)))),
                new Document("$set", fields));
        if (result.getMatchedCount() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Account changed during two-factor setup. Refresh and retry.");
    }
}
