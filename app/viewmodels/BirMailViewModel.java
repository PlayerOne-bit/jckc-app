package app.viewmodels;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;

import app.database.BirMailRepository;
import app.models.Account;
import app.models.BirMail;
import app.models.Taxpayer;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.search.FromStringTerm;

/**
 * Loads the taxpayer's BIR confirmation emails. Messages are cached in the local
 * database, so opening the page is instant and the mail server is only asked for
 * messages newer than the newest one already saved.
 *
 * Threading: fetchNew() only talks to the mail server, so it is safe on a background
 * thread. Everything that touches the database (loadSaved, readSyncState, save,
 * clearSaved) should be called from the JavaFX thread.
 */
public class BirMailViewModel {

    /** Change this to read mail from another sender (any part of the From address works). */
    public static final String BIR_SENDER = "noreply@steampowered.com";

    /** "INBOX", or "[Gmail]/All Mail" to also include mail filed under labels/spam (Gmail only). */
    private static final String GMAIL_FOLDER = "INBOX";

    private static final int MAX_MESSAGES = 50;

    public enum Provider {
        GMAIL("Gmail", "imap.gmail.com", "@gmail.com", GMAIL_FOLDER),
        YAHOO("Yahoo Mail", "imap.mail.yahoo.com", "@yahoo.com", "INBOX");

        public final String label, host, domain, folder;

        Provider(String label, String host, String domain, String folder) {
            this.label = label;
            this.host = host;
            this.domain = domain;
            this.folder = folder;
        }

        /** Stored value may be just the username; add the domain only when it's missing. */
        String fullEmail(String stored) {
            String s = stored.trim();
            return s.contains("@") ? s : s + domain;
        }
    }

    public record Credentials(Provider provider, String email, String pass) {}

    /** What the cache currently holds, read before a sync starts. */
    public record SyncState(Long uidValidity, long maxUid) {}

    /** Messages downloaded by one sync, waiting to be saved. */
    public record SyncResult(long uidValidity, boolean reset, List<BirMail> fresh) {}

    private final BirMailRepository repo;
    private final int taxId;
    private final Credentials credentials;   // null when the taxpayer has no usable mail account

    public BirMailViewModel() throws Exception {
        repo = new BirMailRepository();
        taxId = Taxpayer.getTaxpayer().getId();
        credentials = resolveCredentials();
    }

    /** Gmail if it's saved, otherwise Yahoo, otherwise null. */
    private static Credentials resolveCredentials() {
        Account acc = Taxpayer.getTaxpayer().getAccount();
        if (acc == null) return null;

        if (has(acc.getGmailEmail()) && has(acc.getGmailPass())) {
            return new Credentials(Provider.GMAIL,
                    Provider.GMAIL.fullEmail(acc.getGmailEmail()),
                    acc.getGmailPass().replaceAll("\\s+", ""));   // app password, spaces removed
        }
        if (has(acc.getYahooEmail()) && has(acc.getYahooPass())) {
            return new Credentials(Provider.YAHOO,
                    Provider.YAHOO.fullEmail(acc.getYahooEmail()),
                    acc.getYahooPass().replaceAll("\\s+", ""));
        }
        return null;
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    public Credentials getCredentials() {
        return credentials;
    }

    // ---------- Cache (JavaFX thread) ----------

    /** Saved messages, newest first. Empty when there is no mail account. */
    public List<BirMail> loadSaved() throws Exception {
        if (credentials == null) return new ArrayList<>();
        return repo.getAll(taxId, credentials.email());
    }

    public SyncState readSyncState() throws Exception {
        if (credentials == null) return new SyncState(null, 0);
        return new SyncState(
                repo.getUidValidity(taxId, credentials.email()),
                repo.getMaxUid(taxId, credentials.email()));
    }

    /** Saves the downloaded messages and returns everything now cached, newest first. */
    public List<BirMail> save(SyncResult result) throws Exception {
        if (credentials == null) return new ArrayList<>();
        if (result.reset()) repo.deleteAll(taxId, credentials.email());
        repo.addAll(taxId, credentials.email(), result.uidValidity(), result.fresh());
        return repo.getAll(taxId, credentials.email());
    }

    /** Forgets everything cached for this mail account (e.g. after changing BIR_SENDER). */
    public void clearSaved() throws Exception {
        if (credentials != null) repo.deleteAll(taxId, credentials.email());
    }

    // ---------- Mail server (background thread) ----------

    /** Downloads only the messages newer than what the cache already holds. */
    public SyncResult fetchNew(SyncState state, BiConsumer<Integer, Integer> progress) throws Exception {
        if (credentials == null) throw new IllegalStateException("No mail account saved.");
        Provider provider = credentials.provider();

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");
        Session session = Session.getInstance(props);

        Store store = session.getStore("imaps");
        try {
            store.connect(provider.host, 993, credentials.email(), credentials.pass());
            Folder folder = store.getFolder(provider.folder);
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder uf = (UIDFolder) folder;
                long validity = uf.getUIDValidity();
                // If the server renumbered its messages, the cached uids are meaningless
                boolean reset = state.uidValidity() != null && state.uidValidity() != validity;
                long maxUid = reset ? 0 : state.maxUid();

                Message[] found = folder.search(new FromStringTerm(BIR_SENDER));
                FetchProfile fp = new FetchProfile();
                fp.add(UIDFolder.FetchProfileItem.UID);
                folder.fetch(found, fp);                 // all uids in one round trip

                List<Message> fresh = new ArrayList<>();
                for (Message m : found) if (uf.getUID(m) > maxUid) fresh.add(m);
                if (fresh.size() > MAX_MESSAGES) {        // first sync: only the newest ones
                    fresh = fresh.subList(fresh.size() - MAX_MESSAGES, fresh.size());
                }

                List<BirMail> out = new ArrayList<>();
                int done = 0;
                for (Message m : fresh) {
                    out.add(toBirMail(uf, m));
                    progress.accept(++done, fresh.size());
                }
                return new SyncResult(validity, reset, out);
            } finally {
                folder.close(false);
            }
        } finally {
            store.close();
        }
    }

    private BirMail toBirMail(UIDFolder uf, Message m) throws Exception {
        var date = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        LocalDateTime when = date == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        String from = m.getFrom() == null || m.getFrom().length == 0
                ? BIR_SENDER : MimeUtility.decodeText(m.getFrom()[0].toString());
        String subject = m.getSubject() == null ? "(no subject)" : m.getSubject();

        List<String> attachments = new ArrayList<>();
        collectAttachmentNames(m, attachments);

        return new BirMail(uf.getUID(m), when, subject, from,
                addresses(m.getReplyTo()),
                addresses(m.getRecipients(Message.RecipientType.TO)),
                attachments, bodyOf(m));
    }

    private static String addresses(Address[] list) throws Exception {
        if (list == null || list.length == 0) return "";
        List<String> out = new ArrayList<>();
        for (Address a : list) out.add(MimeUtility.decodeText(a.toString()));
        return String.join(", ", out);
    }

    private static void collectAttachmentNames(Part part, List<String> out) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) collectAttachmentNames(mp.getBodyPart(i), out);
        } else if (part.getFileName() != null) {
            out.add(MimeUtility.decodeText(part.getFileName()));
        }
    }

    /** Prefers the HTML part; falls back to plain text. */
    private static String bodyOf(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.isMimeType("text/plain"))
            return "<pre style='font-family:sans-serif'>" + escape((String) part.getContent()) + "</pre>";
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            String plain = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) continue;
                String text = bodyOf(bp);
                if (text == null) continue;
                if (bp.isMimeType("text/plain")) plain = text; else return text;
            }
            return plain;
        }
        return null;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}