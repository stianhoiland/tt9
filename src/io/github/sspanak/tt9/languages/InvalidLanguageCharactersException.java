package io.github.sspanak.tt9.languages;

public class InvalidLanguageCharactersException extends Exception {

	public InvalidLanguageCharactersException(Language language, String extraMessage) {
		super("Some characters are not supported in language: " + language.getName() + ". " + extraMessage);
	}

}

