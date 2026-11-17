package baddee.japanese_dictionary_services.names.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 404 for an unknown JMnedict id, rendered as an RFC 9457 ProblemDetail (D29). */
public class NameNotFoundException extends ErrorResponseException {

	public NameNotFoundException(long entSeq) {
		super(HttpStatus.NOT_FOUND,
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No name with id " + entSeq + "."),
				null);
	}
}
