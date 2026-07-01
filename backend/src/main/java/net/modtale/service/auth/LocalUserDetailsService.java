package net.modtale.service.auth;

import net.modtale.exception.ReservedAccountAccessException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.access.AdminAuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class LocalUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final ReservedAccountGuardService reservedAccountGuardService;

    public LocalUserDetailsService(
            UserRepository userRepository,
            ReservedAccountGuardService reservedAccountGuardService
    ) {
        this.userRepository = userRepository;
        this.reservedAccountGuardService = reservedAccountGuardService;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameIgnoreCase(login)
                .or(() -> userRepository.findByEmail(login))
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + login));
        try {
            reservedAccountGuardService.rejectReservedUserInProduction(user);
        } catch (ReservedAccountAccessException ex) {
            throw new UsernameNotFoundException("User not found with identifier: " + login);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword() == null ? "" : user.getPassword(),
                AdminAuthorityUtils.authoritiesFor(user)
        );
    }
}
