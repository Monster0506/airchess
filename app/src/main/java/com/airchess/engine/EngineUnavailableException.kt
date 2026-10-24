package com.airchess.engine

/** The engine binary is missing, failed to start, or died; callers should continue without hints. */
class EngineUnavailableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
