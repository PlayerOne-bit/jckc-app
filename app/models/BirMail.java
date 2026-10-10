package app.models;

import java.time.LocalDateTime;
import java.util.List;

/** One confirmation email, as saved in the local cache. uid is the mail server's id for the message. */
public record BirMail(long uid, LocalDateTime received, String subject, String from,
                      String replyTo, String to, List<String> attachments, String html) {}