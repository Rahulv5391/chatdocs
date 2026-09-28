package com.company.chatdocs.service;

final class TextUtils {

	private TextUtils() {
	}

	/** Joins the text into one line and cuts it to at most {@code maxLength} characters, ending with "…" if cut. */
	static String abbreviate(String text, int maxLength) {
		String singleLine = text.replaceAll("\s+", " ").strip();
		return singleLine.length() <= maxLength ? singleLine : singleLine.substring(0, maxLength - 1).strip() + "…";
	}

}
