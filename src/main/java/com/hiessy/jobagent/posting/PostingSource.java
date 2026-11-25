package com.hiessy.jobagent.posting;

import java.io.IOException;
import java.net.URI;

/** Loads a posting page. The HTTP one is the default; the eval set swaps in saved postings. */
public interface PostingSource {

    /** @param contentType for example "text/html"; plain text is used as it is */
    record Page(URI url, String body, String contentType) {
    }

    Page load(URI url) throws IOException;
}
