package org.example.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.exeption.ParseTokenException;
import org.example.backend.models.UserClaimsFromToken;
import org.example.backend.utils.TokenUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenFilterImpl extends OncePerRequestFilter {
    private static final String TOKEN_HEADER = "Authorization";

    private final JwtVerifierService jwtVerifierService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (isPublicEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = getHeader(request);

        String token;
        try{
            token = TokenUtils.extractToken(authHeader);
        } catch(ParseTokenException e) {
            log.warn("error with extraction token: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (token == null || !jwtVerifierService.verify(token)) {
            log.warn("токен битый или пустой");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        DecodedJWT decodedJWT = JWT.decode(token);
        UserClaimsFromToken userAllDataFromToken;
        try{
            userAllDataFromToken = new UserClaimsFromToken(decodedJWT);
        } catch (IllegalArgumentException e) {
            log.warn("date is incorrect!");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String username = userAllDataFromToken.subject();
        log.info("Извлечённый username из токена: {}", username);

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            TokenAuthentication tokenAuthentication = new TokenAuthentication(username);
            SecurityContextHolder.getContext().setAuthentication(tokenAuthentication);
            log.info("Аутентификация установлена для пользователя: {}", username);
        } else {
            log.warn("Субъект (username) из токена равен null");
        }


        filterChain.doFilter(request, response);
    }

    private String getHeader(HttpServletRequest request) {
        return request.getHeader(TOKEN_HEADER);
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) ||
                SecurityConstants.PUBLIC_ENDPOINTS.stream()
                        .anyMatch(pattern -> new AntPathRequestMatcher(pattern).matches(request));
    }

}
