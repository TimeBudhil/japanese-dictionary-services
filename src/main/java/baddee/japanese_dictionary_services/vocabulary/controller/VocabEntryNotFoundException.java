package baddee.japanese_dictionary_services.vocabulary.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 404 for an unknown vocabulary id, rendered as an RFC 9457 ProblemDetail (D29). */
public class VocabEntryNotFoundException extends ErrorResponseException {

	public VocabEntryNotFoundException(long entSeq) {
		super(HttpStatus.NOT_FOUND,
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No vocabulary entry with id " + entSeq + "."),
				null);
	}
}
