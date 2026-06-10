package net.modtale.validation;

import net.modtale.exception.InvalidAccountRequestException;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.exception.InvalidOrganizationRequestException;

public final class AccountNameRules {

    public static final String HANDLE_REGEX = "^[a-zA-Z0-9_.-]+$";
    public static final int MIN_HANDLE_LENGTH = 3;
    public static final int MAX_HANDLE_LENGTH = 30;

    private AccountNameRules() {
    }

    public static void validateRegistrationUsername(String username) {
        if (!hasAllowedCharacters(username) || !hasAllowedLength(username)) {
            throw new InvalidAuthenticationRequestException(
                    "Usernames must be between 3 and 30 characters and can only contain letters, numbers, periods, underscores, and hyphens."
            );
        }
    }

    public static void validateUsernameUpdate(String username) {
        if (!hasAllowedCharacters(username)) {
            throw new InvalidAccountRequestException("Username can only contain letters, numbers, hyphens, underscores, and periods.");
        }
        if (!hasAllowedLength(username)) {
            throw new InvalidAccountRequestException("Username must be between 3 and 30 characters.");
        }
    }

    public static void validateOrganizationName(String name) {
        if (!hasAllowedCharacters(name)) {
            throw new InvalidOrganizationRequestException("Organization names can only contain letters, numbers, periods, underscores, and hyphens.");
        }
        if (!hasAllowedLength(name)) {
            throw new InvalidOrganizationRequestException("Organization names must be between 3 and 30 characters.");
        }
    }

    public static boolean hasAllowedCharacters(String value) {
        return value != null && value.matches(HANDLE_REGEX);
    }

    public static boolean hasAllowedLength(String value) {
        return value != null && value.length() >= MIN_HANDLE_LENGTH && value.length() <= MAX_HANDLE_LENGTH;
    }
}
