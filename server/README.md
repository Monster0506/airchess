# AirChess server

Ktor WebSocket server for online AirChess games. Two phones connect to `ws://<host>:<port>/ws`,
speak the JSON protocol from `:protocol`, and play a private game identified by a 6-character code.
Rooms live in memory only: restarting the server ends all games. There are no accounts.

## What it does

- Creates rooms with a random code, seats the creator on the chosen colour and the joiner opposite.
- Validates every move with the `:chess` rules, so clients cannot cheat.
- Owns the clocks. Each side's first move is untimed; after that the mover is charged the elapsed
  time and then gets the increment. A flag fall loses on time, or draws when the opponent has no
  mating material.
- Resign, draw offers, and rematch (colours swap). Every change is broadcast as a full
  `GameSnapshot` to both players.
- A dropped player keeps the seat for 60 seconds and can `Resume(code, token)`; after that the
  game is lost by abandonment. Rooms with nobody connected for 10 minutes are removed.

## Run locally

```sh
sh ./gradlew :server:run --console=plain
```

The server listens on `0.0.0.0:8080` (override with the `PORT` environment variable) and prints a
`ws://<ip>:<port>/ws` line for every LAN IPv4 address. Check it with:

```sh
curl http://localhost:8080/
# AirChess server OK - rooms: 0
```

### Play over the LAN

1. Put the phones and the computer on the same Wi-Fi.
2. Start the server and copy a printed `ws://192.168.x.x:8080/ws` URL into the app's server setting.
3. Allow inbound TCP on the port in the computer's firewall if the phones cannot connect.

The Android emulator reaches the host machine at `ws://10.0.2.2:8080/ws`.

## Tests

```sh
sh ./gradlew :server:test --console=plain
```

Unit tests drive `GameRoom` with a fake clock (`runTest` virtual time) and fake connections.
The integration test plays a full game over real WebSocket frames with Ktor `testApplication`.

## Deploy

Build a distribution and run it on any machine with Java 17+:

```sh
sh ./gradlew :server:installDist --console=plain
PORT=8080 server/build/install/server/bin/server
```

Or build the container from the repository root:

```sh
docker build -f server/Dockerfile -t airchess-server .
docker run -p 8080:8080 airchess-server
```

The server speaks plain `ws://`. For internet use, put it behind a reverse proxy that terminates
TLS (Caddy, nginx, a platform router) and point the app at `wss://your.host/ws`. The proxy must
allow WebSocket upgrades and idle connections of at least 30 seconds (the server pings every 15).

The server keeps all rooms in one process, so run a single instance.
