package com.remotefalcon.controlpanel.aop;

import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.controlpanel.util.AuthUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AccessAspect {
  @Autowired
  private AuthUtil authUtil;

  @Around("@annotation(RequiresAccess)")
  public Object isJwtValid(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    if(this.authUtil.isJwtValid(request)) {
      return proceedingJoinPoint.proceed();
    }
    throw new RuntimeException(StatusResponse.INVALID_JWT.name());
  }

  @Around("@annotation(RequiresAdminAccess)")
  public Object isAdminJwtValid(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    if(this.authUtil.isAdminJwtValid(request)) {
      return proceedingJoinPoint.proceed();
    }
    throw new RuntimeException(StatusResponse.INVALID_JWT.name());
  }
}
