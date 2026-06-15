package com.hospitalfamilia.server.auth.service;

import com.hospitalfamilia.server.auth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetDeliveryService.class);

    private final JavaMailSender mailSender;
    private final boolean deliveryEnabled;
    private final String from;
    private final String resetUrl;

    public PasswordResetDeliveryService(
        JavaMailSender mailSender,
        @Value("${app.password-reset.delivery-enabled:false}") boolean deliveryEnabled,
        @Value("${app.password-reset.from:no-reply@hospitalfamilia.local}") String from,
        @Value("${app.password-reset.reset-url:http://localhost:8100/auth/reset-password}") String resetUrl
    ) {
        this.mailSender = mailSender;
        this.deliveryEnabled = deliveryEnabled;
        this.from = from;
        this.resetUrl = resetUrl;
    }

    public void sendResetToken(User user, String rawToken, long expirationMinutes) {
        if (!deliveryEnabled) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Recuperacion de contrasena - Hospital Familia");
        message.setText("""
            Hola %s,

            Recibimos una solicitud para recuperar tu contrasena de Hospital Familia.

            Ingresa al siguiente enlace y usa el token indicado:
            %s?token=%s

            Token: %s

            Este token expira en %d minutos. Si no solicitaste este cambio, ignora este correo.
            """.formatted(user.getFirstName(), resetUrl, rawToken, rawToken, expirationMinutes));

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            LOGGER.error("No se pudo enviar email de recuperacion para userId={}", user.getId(), exception);
        }
    }
}
