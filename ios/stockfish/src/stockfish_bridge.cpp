#include "stockfish_bridge.h"

#include <pthread.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <deque>
#include <iostream>
#include <mutex>
#include <streambuf>
#include <string>

// Stockfish's main(), renamed with -Dmain=sf_engine_main when main.cpp is built.
int sf_engine_main(int argc, char* argv[]);

namespace {

constexpr size_t kEngineStackBytes = size_t{8} << 20;
constexpr int kMinReadCapacity = 2;

enum class Pop { Line, Timeout, Closed };

// Closed queues still hand out their remaining lines before reporting Closed.
class LineQueue {
   public:
    void reset() {
        std::lock_guard<std::mutex> lock(mutex_);
        lines_.clear();
        closed_ = false;
    }

    void push(std::string line) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            lines_.push_back(std::move(line));
        }
        cv_.notify_one();
    }

    void close() {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            closed_ = true;
        }
        cv_.notify_all();
    }

    Pop pop(std::string& out, int timeout_ms) {
        std::unique_lock<std::mutex> lock(mutex_);
        auto ready = [this] { return !lines_.empty() || closed_; };

        if (timeout_ms < 0) {
            cv_.wait(lock, ready);
        } else if (!cv_.wait_for(lock, std::chrono::milliseconds(timeout_ms), ready)) {
            return Pop::Timeout;
        }

        if (lines_.empty()) {
            return Pop::Closed;
        }

        out = std::move(lines_.front());
        lines_.pop_front();
        return Pop::Line;
    }

   private:
    std::mutex mutex_;
    std::condition_variable cv_;
    std::deque<std::string> lines_;
    bool closed_ = true;
};

// Feeds queued lines to std::cin one at a time, blocking until one arrives.
class InBuf : public std::streambuf {
   public:
    explicit InBuf(LineQueue& queue) : queue_(queue) {}

   protected:
    int_type underflow() override {
        if (gptr() < egptr()) {
            return traits_type::to_int_type(*gptr());
        }

        if (queue_.pop(line_, -1) != Pop::Line) {
            return traits_type::eof();
        }

        line_.push_back('\n');
        setg(&line_[0], &line_[0], &line_[0] + line_.size());
        return traits_type::to_int_type(*gptr());
    }

   private:
    LineQueue& queue_;
    std::string line_;
};

// Splits everything written to std::cout into lines. Unbuffered, so no flush needed.
class OutBuf : public std::streambuf {
   public:
    explicit OutBuf(LineQueue& queue) : queue_(queue) {}

    void flush_partial() {
        std::lock_guard<std::mutex> lock(mutex_);
        emit();
    }

   protected:
    int_type overflow(int_type c) override {
        if (!traits_type::eq_int_type(c, traits_type::eof())) {
            char ch = traits_type::to_char_type(c);
            append(&ch, 1);
        }
        return traits_type::not_eof(c);
    }

    std::streamsize xsputn(const char* s, std::streamsize n) override {
        append(s, static_cast<size_t>(n));
        return n;
    }

   private:
    void append(const char* s, size_t n) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (size_t i = 0; i < n; ++i) {
            if (s[i] == '\n') {
                emit();
            } else if (s[i] != '\r') {
                pending_.push_back(s[i]);
            }
        }
    }

    // Caller holds mutex_.
    void emit() {
        if (pending_.empty()) {
            return;
        }
        queue_.push(std::move(pending_));
        pending_.clear();
    }

    LineQueue& queue_;
    std::mutex mutex_;
    std::string pending_;
};

struct Bridge {
    LineQueue in;
    LineQueue out;
    InBuf in_buf{in};
    OutBuf out_buf{out};
    std::streambuf* old_in = nullptr;
    std::streambuf* old_out = nullptr;
    pthread_t thread{};
    bool started = false;
    std::atomic<bool> finished{true};
    std::mutex control;
};

// Leaked on purpose: the engine thread may outlive static destruction.
Bridge& bridge() {
    static Bridge* instance = new Bridge();
    return *instance;
}

void* engine_thread(void*) {
    Bridge& b = bridge();
    char name[] = "stockfish";
    char* argv[] = {name, nullptr};

    sf_engine_main(1, argv);

    b.out_buf.flush_partial();
    b.out.close();
    b.finished = true;
    return nullptr;
}

void redirect(Bridge& b) {
    b.old_in = std::cin.rdbuf(&b.in_buf);
    b.old_out = std::cout.rdbuf(&b.out_buf);
}

void restore(Bridge& b) {
    std::cin.rdbuf(b.old_in);
    std::cout.rdbuf(b.old_out);
}

// Caller holds b.control.
void join_locked(Bridge& b) {
    pthread_join(b.thread, nullptr);
    b.started = false;
    restore(b);
}

bool spawn(Bridge& b) {
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setstacksize(&attr, kEngineStackBytes);
    int rc = pthread_create(&b.thread, &attr, engine_thread, nullptr);
    pthread_attr_destroy(&attr);
    return rc == 0;
}

}  // namespace

extern "C" {

int sf_start(void) {
    Bridge& b = bridge();
    std::lock_guard<std::mutex> lock(b.control);

    if (b.started) {
        if (!b.finished) {
            return -1;
        }
        join_locked(b);
    }

    b.in.reset();
    b.out.reset();
    redirect(b);
    b.finished = false;

    if (!spawn(b)) {
        b.finished = true;
        b.out.close();
        restore(b);
        return -2;
    }

    b.started = true;
    return 0;
}

int sf_send(const char* line) {
    Bridge& b = bridge();
    if (line == nullptr || b.finished) {
        return -1;
    }

    const char* end = line + std::strlen(line);
    while (line <= end) {
        const char* nl = std::find(line, end, '\n');
        std::string part(line, nl);
        if (!part.empty() && part.back() == '\r') {
            part.pop_back();
        }
        if (!part.empty()) {
            b.in.push(std::move(part));
        }
        line = nl + 1;
    }
    return 0;
}

int sf_read_line(char* buf, int capacity, int timeout_ms) {
    if (buf == nullptr || capacity < kMinReadCapacity) {
        return -1;
    }

    std::string line;
    switch (bridge().out.pop(line, timeout_ms)) {
        case Pop::Timeout:
            return 0;
        case Pop::Closed:
            return -1;
        case Pop::Line:
            break;
    }

    size_t n = std::min(line.size(), static_cast<size_t>(capacity - 1));
    std::memcpy(buf, line.data(), n);
    buf[n] = '\0';
    return static_cast<int>(n);
}

void sf_stop(void) {
    Bridge& b = bridge();
    std::lock_guard<std::mutex> lock(b.control);

    if (!b.started) {
        return;
    }

    if (!b.finished) {
        b.in.push("quit");
    }
    // Closing the queue also ends the loop if it is waiting on input.
    b.in.close();
    join_locked(b);
}

}  // extern "C"
