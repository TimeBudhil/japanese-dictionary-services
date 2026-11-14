package baddee.japanese_dictionary_services.vocabulary.integration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * 400 for a page past Elasticsearch's result window ({@code index.max_result_window},
 * 10,000 by default). Without this, ES would fail the request and the client would get a 500.
 */
public class VocabPageTooDeepException extends ErrorResponseException {

	public VocabPageTooDeepException(int maxResults) {
		super(HttpStatus.BAD_REQUEST,
				ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
						"Only the first " + maxResults + " results can be paged through. Refine the query."),
				null);
	}
}
