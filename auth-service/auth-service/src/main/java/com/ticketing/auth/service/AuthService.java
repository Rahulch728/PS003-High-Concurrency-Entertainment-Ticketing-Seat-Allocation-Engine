package com.ticketing.auth.service;

import com.ticketing.auth.dto.LoginRequest;
import com.ticketing.auth.dto.RegisterRequest;
import com.ticketing.auth.entity.User;
import com.ticketing.auth.exception.ApiException;
import com.ticketing.auth.repository.UserRepository;
import com.ticketing.auth.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String register(RegisterRequest request) {

        if (isBlank(request.getName()) ||
                isBlank(request.getEmail()) ||
                isBlank(request.getPassword())) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Name, email and password are required");
        }

        if (request.getPassword().length() < 6) {

            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 6 characters");
        }

        if (userRepository.existsByEmail(request.getEmail())) {

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Email already registered");
        }

        User user = new User();

        user.setName(request.getName());
        user.setEmail(request.getEmail());

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        user.setRole("USER");

        userRepository.save(user);

        return "User registered successfully";
    }

    public String login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid email or password"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        return jwtService.generateToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}