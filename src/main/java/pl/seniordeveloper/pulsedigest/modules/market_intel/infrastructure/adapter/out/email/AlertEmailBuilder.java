package pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email;

import org.springframework.stereotype.Component;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.QuotaAlert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static pl.seniordeveloper.pulsedigest.modules.market_intel.infrastructure.adapter.out.email.EmailFormatting.escapeHtml;

/**
 * Builds the standalone alert email sent when the digest could not be produced at all — so the
 * recipient still learns by email that today's run failed. Kept separate from {@code ReportEmailBuilder}
 * because the alert is a distinct concern (failure notification) from the digest document itself.
 */
@Component
public class AlertEmailBuilder {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.of("pl", "PL"));

    /** Subject for the standalone alert email. */
    public String buildSubject() {
        return "⚠️ PulseDigest — dzisiejszy digest nie powstał";
    }

    /**
     * Alert email body. When quota/rate-limit accounts were detected they are named for topping up;
     * otherwise the alert renders the failure reason only (e.g. an unexpected error or all sources
     * returning empty), so a broken run is never silent.
     */
    public String buildHtml(QuotaAlert alert) {
        String today = LocalDate.now().format(DATE_FMT);
        boolean hasAccounts = alert.exhaustedAccounts() != null && !alert.exhaustedAccounts().isEmpty();
        String detail = alert.detail() != null && !alert.detail().isBlank()
                ? "<p style=\"margin:14px 0 0;color:#9ca3af;font-size:12px;font-family:monospace;"
                  + "word-break:break-word\">" + escapeHtml(alert.detail()) + "</p>"
                : "";
        String intro;
        String accountsBlock;
        if (hasAccounts) {
            StringBuilder list = new StringBuilder();
            for (String account : alert.exhaustedAccounts()) {
                list.append("<li><strong>").append(escapeHtml(account)).append("</strong></li>");
            }
            intro = "<p style=\"margin:0 0 12px\">Dzisiejszy PulseDigest nie został wygenerowany, ponieważ "
                    + "wyczerpał się limit/kredyty następujących kont. Doładuj je, aby przywrócić pełne raporty:</p>";
            accountsBlock = "<ul style=\"margin:0;padding-left:20px;color:#7f1d1d;font-size:14px;line-height:1.8\">"
                    + list + "</ul>";
        } else {
            intro = "<p style=\"margin:0 0 12px\">Dzisiejszy PulseDigest nie został wygenerowany z powodu błędu "
                    + "podczas przetwarzania. Szczegóły poniżej &mdash; jutrzejsze wydanie powinno przyjść "
                    + "normalnie.</p>";
            accountsBlock = "";
        }
        return "<!DOCTYPE html>"
                + "<html lang=\"pl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>PulseDigest — alert</title></head>"
                + "<body style=\"font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',"
                + "sans-serif;background:#f9fafb;margin:0;padding:20px\">"
                + "<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:12px;"
                + "overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,.1)\">"
                + "<div style=\"background:#b91c1c;padding:22px 28px\">"
                + "<h1 style=\"color:#fff;margin:0;font-size:20px\">&#9888;&#65039; Digest nie powstał</h1>"
                + "<p style=\"color:#fecaca;margin:4px 0 0;font-size:13px\">" + today + "</p></div>"
                + "<div style=\"padding:22px 28px;color:#0f172a;font-size:14px;line-height:1.6\">"
                + intro
                + accountsBlock
                + detail
                + "</div></div></body></html>";
    }
}
