# Midnight Chess

A dark-themed multiplayer chess app built with **Vaadin 25 (Flow)** and **Spring Boot 4**, featuring live chat and server push.

## Features

- **Play the computer**: four levels (Beginner → Master), choose a colour, take back moves.
- **Play people**: challenge anyone in the lobby, or use *Quick match* to get paired with the next player who is seeking a game.
- **Live chat**: a lobby room plus a per-game room for players and spectators, with quick-reply chips and Arbiter (system) messages for game events.
- **Spectate**: the lobby lists live games, and anyone can watch them.
- **Full rules**: castling, en passant, promotion (with a picker), check, checkmate, stalemate, threefold repetition, the 50-move rule, and insufficient material.
- **Board UX**: click-to-move or drag-and-drop, legal-move hints, last-move and check highlights, captured pieces with a material count, flip board, move list in SAN, and synthesized move sounds.
- **Draw offers, resignation and rematches**. A player who leaves a human game for more than 20 seconds forfeits it.

## Run

Requires JDK 21+. Maven is provided through the wrapper.

```powershell
$env:JAVA_HOME = "C:\Users\depth\.jdks\ms-21.0.12"
.\mvnw.cmd spring-boot:run
```

Then open http://localhost:8080. The first start downloads frontend tooling and takes a minute or two.

**Testing multiplayer alone:** each browser tab is a separate player (identity is kept in `sessionStorage`), so open two tabs, pick two names, and challenge yourself.

## Tests

```powershell
.\mvnw.cmd test
```

The engine is checked with perft counts on standard reference positions, SAN and draw/mate rules, and AI tactics.

## Project layout

```
src/main/java/com/midnightchess
├── Application.java          app shell: @Push, Lumo dark color scheme, stylesheet
├── engine/                   pure-Java chess (no Vaadin dependency)
│   ├── Board.java            immutable position, move generation, SAN, FEN
│   ├── ChessGame.java        history, result detection, undo
│   └── ChessAi.java          alpha-beta + quiescence + piece-square tables
├── service/                  in-memory, thread-safe application state
│   ├── PlayerRegistry.java   presence (with reload grace period)
│   ├── GameService.java      pairing, challenges, computer moves, game lifecycle
│   ├── GameSession.java      one live game + listeners
│   └── ChatService.java      chat rooms
└── ui/
    ├── MainLayout.java       top bar, per-tab identity, challenge notifications
    ├── LobbyView.java        play cards, players and games lists, lobby chat
    ├── GameView.java         board, player bars, status/actions, moves, game chat
    └── components/           ChessBoard, ChatPanel, PlayerBar, PromotionDialog
src/main/resources/META-INF/resources/styles.css   the dark theme
```

All state lives in memory, so restarting the server clears players, games and chat.

## Production build

```powershell
.\mvnw.cmd -Pproduction package
java -jar target\midnight-chess-0.1.0-SNAPSHOT.jar
```
