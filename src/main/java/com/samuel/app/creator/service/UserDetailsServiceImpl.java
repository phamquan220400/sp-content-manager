package com.samuel.app.creator.service;

import com.samuel.app.creator.model.User;
import com.samuel.app.creator.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserDetailsServiceImpl(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Called by AuthenticationManager during login — email is used as the lookup key.
     * Uses generic error message to prevent user enumeration attacks.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Authentication failed"));

        return buildUserDetails(user);
    }

    /**
     * Called by JwtAuthenticationFilter after token validation — userId is the JWT subject.
     * Token-based lookup failures indicate system issues, not user enumeration attempts.
     */
    public UserDetails loadUserById(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        return buildUserDetails(user);
    }

    private UserDetails buildUserDetails(User user) {
        if (user.getStatus() == User.UserStatus.PENDING) {
            throw new DisabledException("Please verify your email address before logging in.");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now(clock))) {
            throw new LockedException("Account temporarily locked. Please try again later.");
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getId())
                .password(user.getPassword())
                .roles("CREATOR")
                .build();
    }
}
