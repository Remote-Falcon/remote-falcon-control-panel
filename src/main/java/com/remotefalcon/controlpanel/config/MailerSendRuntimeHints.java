package com.remotefalcon.controlpanel.config;

import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.Recipient;
import com.mailersend.sdk.emails.Attachment;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.emails.Personalization;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class MailerSendRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerPublicFields(hints, Email.class);
        registerPublicFields(hints, Recipient.class);
        registerPublicFields(hints, Personalization.class);
        registerPublicFields(hints, Attachment.class);
        registerPublicFields(hints, MailerSendResponse.class);
    }

    private void registerPublicFields(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(
                type,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                MemberCategory.PUBLIC_FIELDS
        );
    }
}
