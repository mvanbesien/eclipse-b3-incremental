/**
 *
 */
package fr.mvanbesien.b3.cli.headless;

import java.util.Locale;
import java.util.ResourceBundle;

import java.text.MessageFormat;

/**
 * Enumeration containing internationalisation-related messages and API.
 *
 * @generated com.worldline.awltech.i18ntools.wizard
 */
public enum Messages {
	PATH_NOT_FOUND("PATH_NOT_FOUND"), FILE_NOT_FOUND("FILE_NOT_FOUND"), FILE_NOT_READABLE("FILE_NOT_READABLE"), CHECKING_LAST_UPDATE("CHECKING_LAST_UPDATE"), REPO_NOT_FOUND_IN_FILE("REPO_NOT_FOUND_IN_FILE"), REPO_NOT_DATED("REPO_NOT_DATED"), AGGREGATION_WILL_HAPPEN("AGGREGATION_WILL_HAPPEN"), AN_ERROR_OCCURRED("AN_ERROR_OCCURRED"), AGGREGATION_SKIPPED("AGGREGATION_SKIPPED"), ON_EXCEPTION("ON_EXCEPTION"), REPO_CHECK_RESULT("REPO_CHECK_RESULT"), TIME_TO_CHECK("TIME_TO_CHECK"), CHECK_RESULT("CHECK_RESULT"), TITLE("TITLE"), PARSING_MODEL("PARSING_MODEL"), LOCATED_REPO("LOCATED_REPO")
	;

	/*
	 * Value of the key
	 */
	private final String messageKey;

	/*
	 * Constant ResourceBundle instance
	 */
	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("Messages", Locale.getDefault());

	/**
	 * Private Enumeration Literal constructor
	 * 
	 * @param messageKey
	 *            value
	 */
	private Messages(final String messageKey) {
		this.messageKey = messageKey;
	}

	/**
	 * @return the message associated with the current value
	 */
	public String value() {
		if (Messages.RESOURCE_BUNDLE == null || !Messages.RESOURCE_BUNDLE.containsKey(this.messageKey)) {
			return "!!" + this.messageKey + "!!";
		}
		return Messages.RESOURCE_BUNDLE.getString(this.messageKey);
	}

	/**
	 * Formats and returns the message associated with the current value.
	 *
	 * @see java.text.MessageFormat
	 * @param parameters
	 *            to use during formatting phase
	 * @return formatted message
	 */
	public String value(final Object... args) {
		if (Messages.RESOURCE_BUNDLE == null || !Messages.RESOURCE_BUNDLE.containsKey(this.messageKey)) {
			return "!!" + this.messageKey + "!!";
		}
		return MessageFormat.format(Messages.RESOURCE_BUNDLE.getString(this.messageKey), args);
	}

}
