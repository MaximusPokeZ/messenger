package org.example.backend.models;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Date;
import java.util.Map;

public record UserClaimsFromToken(
        String token,
        String subject,
        Map<String, Claim> claims,
        Date expiresAt,
        Date issuedAt
) {
    public UserClaimsFromToken(DecodedJWT decodedJWT) {
        this(
                decodedJWT.getToken(),
                decodedJWT.getSubject(),
                decodedJWT.getClaims(),
                decodedJWT.getExpiresAt(),
                decodedJWT.getIssuedAt()
        );
    }
}
