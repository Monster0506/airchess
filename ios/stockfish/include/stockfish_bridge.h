/*
 * In-process UCI bridge for Stockfish (C ABI).
 *
 * The engine's own UCI loop runs on a dedicated thread. std::cin and std::cout
 * are redirected to in-memory line queues; process file descriptors are never
 * touched. While the engine runs, anything else in the process that writes to
 * std::cout is captured as well.
 *
 * Lifecycle: sf_start / sf_stop can be called repeatedly (restartable). Call
 * them from one controlling thread. sf_send may be called from any thread.
 * sf_read_line supports a single reader at a time.
 */
#ifndef STOCKFISH_BRIDGE_H
#define STOCKFISH_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

/* Start the engine thread. Output queued by a previous run is discarded.
 * Returns 0 on success, -1 if already running, -2 if the thread could not be
 * created. */
int sf_start(void);

/* Queue one UCI command (a trailing newline is optional). Returns 0 on
 * success, -1 if the engine is not running or line is NULL. */
int sf_send(const char* line);

/* Wait for the next non-empty engine output line and copy it into buf as a
 * NUL-terminated string without the newline. Lines longer than capacity - 1
 * are truncated.
 *
 * timeout_ms < 0 waits forever, 0 polls.
 * Returns the line length (> 0), 0 on timeout, or -1 once the engine has quit
 * and every queued line has been read (also when capacity <= 0 or buf is NULL). */
int sf_read_line(char* buf, int capacity, int timeout_ms);

/* Send "quit", wait for the engine thread to finish and restore the original
 * std::cin / std::cout buffers. Blocked sf_read_line calls return the
 * remaining lines, then -1. Safe to call when not running. */
void sf_stop(void);

#ifdef __cplusplus
}
#endif

#endif
