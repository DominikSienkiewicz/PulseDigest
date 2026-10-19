package pl.seniordeveloper.pulsedigest.modules.market_intel.domain.port.out;

import pl.seniordeveloper.pulsedigest.modules.market_intel.domain.model.ArchivedEdition;

import java.util.List;

/** Writes the public archive somewhere a static host can serve it (N31). */
public interface ArchiveSitePort {

    /**
     * Writes the complete site: one page per edition, an index, a feed and a search index.
     *
     * @param editions newest first, exactly as they will appear
     */
    void write(List<ArchivedEdition> editions);
}
