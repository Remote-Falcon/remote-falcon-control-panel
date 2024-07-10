package com.remotefalcon.controlpanel.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.remotefalcon.library.documents.Show;
import com.remotefalcon.controlpanel.dto.TokenDTO;
import com.remotefalcon.library.enums.ShowRole;
import com.remotefalcon.library.enums.StatusResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthUtil {
  @Value("${jwt.user}")
  String jwtSignKey;

  public TokenDTO tokenDTO;

  public String signJwt(Show show) {
    Map<String, Object> jwtPayload = new HashMap<String, Object>();
    jwtPayload.put("showToken", show.getShowToken());
    jwtPayload.put("email", show.getEmail());
    jwtPayload.put("showSubdomain", show.getShowSubdomain());
    jwtPayload.put("showRole", show.getShowRole().name());
    try {
      Algorithm algorithm = Algorithm.HMAC256(jwtSignKey);
      return JWT.create().withClaim("user-data", jwtPayload)
              .withIssuer("remotefalcon")
              .withExpiresAt(Date.from(ZonedDateTime.now().plusDays(30).toInstant()))
              .sign(algorithm);
    } catch (JWTCreationException e) {
      log.error("Error creating JWT", e);
      return null;
    }
  }

  public TokenDTO getJwtPayload() {
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    String token = this.getTokenFromRequest(request);
    try {
      DecodedJWT decodedJWT = JWT.decode(token);
      Map<String, Object> userDataMap = decodedJWT.getClaim("user-data").asMap();
      return TokenDTO.builder()
              .showToken((String) userDataMap.get("showToken"))
              .email((String) userDataMap.get("email"))
              .showSubdomain((String) userDataMap.get("showSubdomain"))
              .showRole(ShowRole.valueOf((String) userDataMap.get("showRole")))
              .build();
    }catch (JWTDecodeException jde) {
      throw new RuntimeException(StatusResponse.INVALID_JWT.name());
    }
  }

  public Boolean isJwtValid(HttpServletRequest httpServletRequest) throws JWTVerificationException {
    try {
      String token = this.getTokenFromRequest(httpServletRequest);
      if (StringUtils.isEmpty(token)) {
        throw new RuntimeException(StatusResponse.INVALID_JWT.name());
      }
      Algorithm algorithm = Algorithm.HMAC256(jwtSignKey);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("remotefalcon").build();
      verifier.verify(token);
      this.tokenDTO = getJwtPayload();
      return true;
    } catch (JWTVerificationException e) {
      throw new RuntimeException(StatusResponse.INVALID_JWT.name());
    }
  }

  public Boolean isAdminJwtValid(HttpServletRequest httpServletRequest) throws JWTVerificationException {
    try {
      String token = this.getTokenFromRequest(httpServletRequest);
      if (StringUtils.isEmpty(token)) {
        throw new RuntimeException(StatusResponse.INVALID_JWT.name());
      }
      Algorithm algorithm = Algorithm.HMAC256(jwtSignKey);
      JWTVerifier verifier = JWT.require(algorithm).withIssuer("remotefalcon").build();
      verifier.verify(token);
      TokenDTO tokenDTO = this.tokenDTO = getJwtPayload();
      return tokenDTO.getShowRole() == ShowRole.ADMIN;
    } catch (JWTVerificationException e) {
      throw new RuntimeException(StatusResponse.INVALID_JWT.name());
    }
  }

  private String getTokenFromRequest(HttpServletRequest httpServletRequest) {
    String token = "";
    final String authorization = httpServletRequest.getHeader("Authorization");
    if (authorization != null && authorization.toLowerCase().startsWith("bearer")) {
      try {
        token = authorization.split(" ")[1];
      }catch (Exception e) {
        log.error("Error getting token from request");
        throw new RuntimeException(StatusResponse.INVALID_JWT.name());
      }
    }
    return token;
  }

  public String[] getBasicAuthCredentials(HttpServletRequest httpServletRequest) {
    final String authorization = httpServletRequest.getHeader("Authorization");
    if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
      String base64Credentials = authorization.substring("Basic".length()).trim();
      byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
      String credentials = new String(credDecoded, StandardCharsets.UTF_8);
      return credentials.split(":", 2);
    }
    return null;
  }

  public String getPasswordFromHeader(HttpServletRequest httpServletRequest) {
    final String password = httpServletRequest.getHeader("Password");
    if (password != null) {
      return new String(Base64.getDecoder().decode(password));
    }
    return null;
  }

  public String getUpdatedPasswordFromHeader(HttpServletRequest httpServletRequest) {
    final String password = httpServletRequest.getHeader("NewPassword");
    if (password != null) {
      return new String(Base64.getDecoder().decode(password));
    }
    return null;
  }
}
