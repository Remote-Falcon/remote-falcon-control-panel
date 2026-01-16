package com.remotefalcon.controlpanel.util;

import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.exceptions.MailerSendException;
import com.remotefalcon.library.documents.Show;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class EmailUtil {

  @Value("${sendgrid.key}")
  String sendgridKey;

  @Value("${web.url}")
  String webUrl;

  public MailerSendResponse sendSignUpEmail(Show show) {
    Email email = new Email();
    email.addRecipient(show.getShowName(), show.getEmail());

    email.setTemplateId("3z0vklojo3eg7qrx");
    email.setSubject("Welcome to Remote Falcon!");
    email.AddVariable("showName", show.getShowName());
    email.AddVariable("verifyEmailLink", String.format("%s/verifyEmail/%s/%s", webUrl, show.getShowToken(), show.getShowSubdomain()));
    return sendEmail(email);
  }

  public MailerSendResponse sendForgotPasswordEmail(Show show, String passwordResetLink) {
    Email email = new Email();
    email.addRecipient(show.getShowName(), show.getEmail());
    email.setTemplateId("3vz9dlejwxp4kj50");
    email.setSubject("You forgot your password, huh?");
    email.AddVariable("resetPasswordLink", String.format("%s/resetPassword/%s", webUrl, passwordResetLink));
    return sendEmail(email);
  }

  public MailerSendResponse sendRequestApiAccessEmail(Show show, String apiAccessToken, String apiAccessSecret) {
    Email email = new Email();
    email.addRecipient(show.getShowName(), show.getEmail());
    email.setTemplateId("3yxj6lj6e304do2r");
    email.setSubject("Let's Get Coding!");
    email.AddVariable("accessToken", apiAccessToken);
    email.AddVariable("secretKey", apiAccessSecret);
    return sendEmail(email);
  }

  private MailerSendResponse sendEmail(Email email) {
    MailerSendResponse response = new MailerSendResponse();
    email.setFrom("Remote Falcon", "noreply@remotefalcon.com");

    MailerSend ms = new MailerSend();

    ms.setToken(sendgridKey);

    try {
      response = ms.emails().send(email);
    } catch (MailerSendException e) {
      log.error(
          "Error sending email (code={}, message={}, responseBody={}, errors={})",
          e.code,
          e.message,
          e.responseBody,
          e.errors,
          e
      );
      response.responseStatusCode = e.code > 0 ? e.code : 500;
    }
    return response;
  }
}
