package dev.gnomebot.app.data.complex;

import dev.gnomebot.app.util.SimpleStringReader;

public class ComplexMessageContext {
	public TextHolder textHolder;
	public PropertyHolder propertyHolder;
	public OptionHolder optionHolder;

	public void acceptText(String text) {
		if (textHolder != null) {
			textHolder.acceptText(this, text);
		}
	}

	public void acceptProperty(String name, SimpleStringReader reader) {
		if (propertyHolder != null) {
			propertyHolder.acceptProperty(this, name, reader);
		}
	}

	public void acceptOption(SimpleStringReader reader) {
		if (optionHolder != null) {
			optionHolder.acceptOption(this, reader);
		}
	}

	@FunctionalInterface
	public interface TextHolder {
		void acceptText(ComplexMessageContext ctx, String text);
	}

	@FunctionalInterface
	public interface PropertyHolder {
		void acceptProperty(ComplexMessageContext ctx, String name, SimpleStringReader reader);
	}

	@FunctionalInterface
	public interface OptionHolder {
		void acceptOption(ComplexMessageContext ctx, SimpleStringReader reader);
	}
}
