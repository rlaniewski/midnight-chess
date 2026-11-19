package com.midnightchess.ui.components;

import com.midnightchess.service.ChatService;
import com.midnightchess.service.Player;
import com.midnightchess.ui.CurrentPlayer;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageInputI18n;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.List;

/** A live chat room: message history, quick replies and an input. */
public class ChatPanel extends Div {
    private static final int MAX_ITEMS = 150;

    private final ChatService chat;
    private final String room;
    private final MessageList list = new MessageList();
    private final List<MessageListItem> items = new ArrayList<>();
    private final Span subtitle = new Span();
    private final Div empty = new Div();
    private UI ui;
    private Registration registration;

    public ChatPanel(ChatService chat, String room, String title, List<String> quickReplies) {
        this.chat = chat;
        this.room = room;
        addClassName("chat-panel");

        Span icon = new Span(VaadinIcon.CHAT.create());
        icon.addClassName("chat-icon");
        Span titleSpan = new Span(title);
        titleSpan.addClassName("chat-title");
        subtitle.addClassName("chat-subtitle");
        Div titles = new Div(titleSpan, subtitle);
        titles.addClassName("chat-titles");
        Span live = new Span("Live");
        live.addClassName("chat-live");
        Div header = new Div(icon, titles, live);
        header.addClassName("chat-header");

        list.addClassName("chat-list");
        empty.addClassName("chat-empty");
        empty.add(new Span("♟︎"), new Span("No messages yet — say hello!"));
        Div body = new Div(list, empty);
        body.addClassName("chat-body");

        Div quick = new Div();
        quick.addClassName("chat-quick");
        for (String q : quickReplies) {
            Button b = new Button(q, e -> send(q));
            b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            b.addClassName("chip");
            quick.add(b);
        }

        MessageInput input = new MessageInput();
        input.setI18n(new MessageInputI18n().setMessage("Write a message…").setSend("Send"));
        input.addClassName("chat-input");
        input.addSubmitListener(e -> send(e.getValue()));

        add(header, body, quick, input);
    }

    public void setSubtitle(String text) {
        subtitle.setText(text);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        ui = event.getUI();
        items.clear();
        chat.history(room).forEach(m -> items.add(toItem(m)));
        publish();
        registration = chat.subscribe(room, m -> ui.access(() -> {
            items.add(toItem(m));
            while (items.size() > MAX_ITEMS) {
                items.remove(0);
            }
            publish();
        }));
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
        super.onDetach(event);
    }

    private void send(String text) {
        Player p = CurrentPlayer.get();
        if (p == null) {
            Notification.show("Choose a name first");
            return;
        }
        chat.post(room, p, text);
    }

    private void publish() {
        list.setItems(new ArrayList<>(items));
        empty.setVisible(items.isEmpty());
        list.getElement().executeJs("const el = this; requestAnimationFrame(() => el.scrollTop = el.scrollHeight);");
    }

    private MessageListItem toItem(ChatService.Message m) {
        MessageListItem item = new MessageListItem(m.text(), m.time(), m.author());
        if (m.system()) {
            item.setUserAbbreviation("♞");
            item.setUserColorIndex(4);
            item.addClassNames("system");
        } else {
            item.setUserColorIndex(m.colorIndex());
            Player me = ui == null ? null : CurrentPlayer.get(ui);
            if (me != null && me.getId().equals(m.authorId())) {
                item.addClassNames("own");
            }
        }
        return item;
    }
}
