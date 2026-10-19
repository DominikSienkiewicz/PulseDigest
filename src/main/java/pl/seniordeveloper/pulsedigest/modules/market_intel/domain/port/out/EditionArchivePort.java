package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ReportData;
import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ResearchResult;

/**
 * Writes the edition as it was rendered to a local file, so the run leaves behind something a human
 * can open.
 *
 * <p>Until 2026-10-18 the rendered edition existed only inside the email that carried it: if the
 * send failed, the digest the run had just spent money producing was gone, and "what did Thursday's
 * edition look like?" had no answer short of the recipient's inbox (N34). The workflow uploads this
 * file as a run artifact, which also means a failed delivery still leaves the edition downloadable
 * from the Actions tab.
 *
 * <p>Archiving is best-effort by contract: a run that produced a digest must not fail because it
 * could not also write it to disk.
 */
public interface EditionArchivePort {

    /** Renders the edition to the configured path. Never throws; failures are logged. */
    void archive(ReportData report, ResearchResult research);
}
