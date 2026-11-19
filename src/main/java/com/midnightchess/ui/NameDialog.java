package com.midnightchess.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

/** First-visit dialog asking for a display name. */
public class NameDialog extends Dialog {

    public NameDialog(Consumer<String> onName) {
        addClassName("name-dialog");
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);

        Span mark = new Span("♞︎");
        mark.addClassName("name-dialog-mark");
        Div heading = new Div(new Span("Welcome to"), new Span("Midnight Chess"));
        heading.addClassName("name-dialog-heading");
        Paragraph intro = new Paragraph("Choose a name other players will see in the lobby and chat.");
        intro.addClassName("name-dialog-intro");

        TextField field = new TextField("Display name");
        field.setMaxLength(20);
        field.setPlaceholder("e.g. KnightOwl");
        field.setWidthFull();
        field.setAutofocus(true);

        Button enter = new Button("Enter the lobby", e -> {
            String v = field.getValue().strip();
            if (v.length() < 2) {
                field.setErrorMessage("Use at least 2 characters");
                field.setInvalid(true);
                return;
            }
            close();
            onName.accept(v);
        });
        enter.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        enter.addClickShortcut(Key.ENTER);
        enter.setWidthFull();

        add(mark, heading, intro, field, enter);
    }
}
