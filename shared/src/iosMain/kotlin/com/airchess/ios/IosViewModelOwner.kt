package com.airchess.ios

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.airchess.engine.ChessEngine
import com.airchess.media.ViewSharing
import com.airchess.viewmodel.ChessViewModel

/** Owns the app's one view model until the iOS app owner is explicitly cleared. */
class IosViewModelOwner(
    serverUrl: String,
    engineProvider: () -> ChessEngine,
    viewSharing: ViewSharing,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val viewModel: ChessViewModel = createChessViewModel(serverUrl, engineProvider, viewSharing)

    init {
        viewModelStore.put(VIEW_MODEL_KEY, viewModel)
    }

    fun clear() {
        viewModelStore.clear()
    }

    private companion object {
        const val VIEW_MODEL_KEY = "airchess"
    }
}
