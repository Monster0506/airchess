# Compiles Stockfish plus the UCI bridge into one static library.
# Driven by build.sh, which sets: CXX CXXFLAGS SRC_DIR BRIDGE_DIR OBJ_DIR LIB
# Objects compile with SRC_DIR as working directory because incbin resolves the
# embedded net relative to it.

SF_SRCS := $(shell cd '$(SRC_DIR)' && find . -name '*.cpp' ! -path './universal/*' | sed 's|^\./||' | sort)
OBJS := $(addprefix $(OBJ_DIR)/,$(SF_SRCS:.cpp=.o)) $(OBJ_DIR)/stockfish_bridge.o

# main.cpp's entry point becomes a plain function the bridge calls on its thread.
EXTRA_main := -Dmain=sf_engine_main

$(LIB): $(OBJS)
	mkdir -p '$(@D)'
	rm -f '$@'
	xcrun libtool -static -o '$@' $(OBJS)

$(OBJ_DIR)/stockfish_bridge.o: $(BRIDGE_DIR)/src/stockfish_bridge.cpp
	mkdir -p '$(@D)'
	$(CXX) $(CXXFLAGS) -I'$(BRIDGE_DIR)/include' -c '$<' -o '$@'

$(OBJ_DIR)/%.o: $(SRC_DIR)/%.cpp
	mkdir -p '$(@D)'
	cd '$(SRC_DIR)' && $(CXX) $(CXXFLAGS) $(EXTRA_$*) -c '$<' -o '$@'
