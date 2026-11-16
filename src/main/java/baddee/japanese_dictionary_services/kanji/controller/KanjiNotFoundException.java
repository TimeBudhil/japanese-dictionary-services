package baddee.japanese_dictionary_services.kanji.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** 404 for an unknown kanji character, rendered as an RFC 9457 ProblemDetail (D29). */
public class KanjiNotFoundException extends ErrorResponseException {

	public KanjiNotFoundException(String character) {
		super(HttpStatus.NOT_FOUND,
				ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No kanji " + character + "."),
				null);
	}
}
