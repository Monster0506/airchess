// Host smoke test for the in-process UCI bridge.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "stockfish_bridge.h"

namespace {

constexpr int kLineCapacity = 4096;
constexpr int kShortWaitMs = 20000;
constexpr int kSearchWaitMs = 120000;

// Every legal black reply to 1. e4.
const std::vector<std::string> kLegalReplies = {
    "a7a6", "a7a5", "b7b6", "b7b5", "c7c6", "c7c5", "d7d6", "d7d5", "e7e6", "e7e5",
    "f7f6", "f7f5", "g7g6", "g7g5", "h7h6", "h7h5", "b8a6", "b8c6", "g8f6", "g8h6",
};

[[noreturn]] void fail(const char* what) {
    std::printf("FAIL: %s\n", what);
    std::fflush(stdout);
    sf_stop();
    std::exit(1);
}

void send(const char* cmd) {
    std::printf("> %s\n", cmd);
    if (sf_send(cmd) != 0) {
        fail("sf_send rejected a command");
    }
}

// Logs engine lines until one starts with prefix; returns that line.
std::string expect(const char* prefix, int timeout_ms) {
    char buf[kLineCapacity];
    size_t prefix_len = std::strlen(prefix);

    for (;;) {
        int n = sf_read_line(buf, kLineCapacity, timeout_ms);
        if (n == 0) {
            std::printf("timeout waiting for '%s'\n", prefix);
            fail("read timeout");
        }
        if (n < 0) {
            fail("engine quit unexpectedly");
        }
        std::printf("< %s\n", buf);
        if (std::strncmp(buf, prefix, prefix_len) == 0) {
            return buf;
        }
    }
}

bool is_legal_reply(const std::string& bestmove_line) {
    std::string move = bestmove_line.substr(std::strlen("bestmove "));
    move = move.substr(0, move.find(' '));
    for (const std::string& legal : kLegalReplies) {
        if (legal == move) {
            return true;
        }
    }
    return false;
}

void play_game() {
    send("uci");
    expect("uciok", kShortWaitMs);

    send("isready");
    expect("readyok", kShortWaitMs);

    send("position startpos moves e2e4");
    send("go depth 12");
    std::string best = expect("bestmove ", kSearchWaitMs);
    if (!is_legal_reply(best)) {
        fail("bestmove is not a legal reply to e2e4");
    }
}

}  // namespace

int main() {
    if (sf_start() != 0) {
        fail("sf_start failed");
    }
    if (sf_start() != -1) {
        fail("second sf_start must report already running");
    }

    play_game();

    // Proves the embedded network loaded and evaluates.
    send("position startpos");
    send("eval");
    expect("Final evaluation", kShortWaitMs);

    char buf[kLineCapacity];
    if (sf_read_line(buf, kLineCapacity, 0) < 0) {
        fail("poll on a live engine must not report quit");
    }

    sf_stop();
    if (sf_send("uci") != -1) {
        fail("sf_send after sf_stop must fail");
    }
    while (sf_read_line(buf, kLineCapacity, 0) > 0) {
    }
    if (sf_read_line(buf, kLineCapacity, 0) != -1) {
        fail("sf_read_line must return -1 after quit");
    }
    std::printf("-- stopped, restarting --\n");

    if (sf_start() != 0) {
        fail("restart failed");
    }
    play_game();
    sf_stop();

    std::printf("PASS\n");
    return 0;
}
