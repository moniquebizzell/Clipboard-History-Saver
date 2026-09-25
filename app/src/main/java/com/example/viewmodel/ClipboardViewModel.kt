package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ClipboardRepository
import com.example.model.ClipItem
import com.example.util.ClipboardUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ClipFilter(val label: String) {
    ALL("All"),
    PINNED("Pinned"),
    LINKS("Links"),
    CODE("Code")
}

data class UserFeedback(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

class ClipboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ClipboardRepository(application.applicationContext)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(ClipFilter.ALL)
    val selectedFilter: StateFlow<ClipFilter> = _selectedFilter.asStateFlow()

    private val _activeClipboardPeek = MutableStateFlow<String?>(null)
    val activeClipboardPeek: StateFlow<String?> = _activeClipboardPeek.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<UserFeedback>()
    val feedbackEvents: SharedFlow<UserFeedback> = _feedbackEvents.asSharedFlow()

    private var recentlyDeletedClip: Pair<ClipItem, Int>? = null

    // Raw repository list
    val allClips: StateFlow<List<ClipItem>> = repository.clipsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered list based on Search & Filter Chip
    val filteredClips: StateFlow<List<ClipItem>> = combine(
        allClips,
        _searchQuery,
        _selectedFilter
    ) { clips, query, filter ->
        var list = clips

        // Apply Category/Pin filter
        list = when (filter) {
            ClipFilter.ALL -> list
            ClipFilter.PINNED -> list.filter { it.isPinned }
            ClipFilter.LINKS -> list.filter { it.category == "url" }
            ClipFilter.CODE -> list.filter { it.category == "code" }
        }

        // Apply text search
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            list = list.filter { it.text.lowercase().contains(q) }
        }

        // Order: Pinned items first, then by timestamp descending
        list.sortedWith(
            compareByDescending<ClipItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshActiveClipboardPeek()
    }

    fun refreshActiveClipboardPeek() {
        val peek = ClipboardUtils.readPrimaryClipText(getApplication())
        _activeClipboardPeek.value = peek
    }

    fun saveActiveClipboard() {
        val context = getApplication<Application>().applicationContext
        val text = ClipboardUtils.readPrimaryClipText(context)

        if (text.isNullOrBlank()) {
            viewModelScope.launch {
                _feedbackEvents.emit(UserFeedback("Clipboard is empty or contains no text"))
            }
            return
        }

        viewModelScope.launch {
            when (repository.saveClip(text)) {
                ClipboardRepository.SaveResult.Success -> {
                    ClipboardUtils.triggerHapticFeedback(context)
                    _feedbackEvents.emit(UserFeedback("Saved to Pinboard!"))
                    refreshActiveClipboardPeek()
                }
                ClipboardRepository.SaveResult.Duplicate -> {
                    _feedbackEvents.emit(UserFeedback("Already saved at top of Pinboard"))
                }
                ClipboardRepository.SaveResult.Empty -> {
                    _feedbackEvents.emit(UserFeedback("Clipboard is empty or blank"))
                }
            }
        }
    }

    fun copyToClipboard(clip: ClipItem) {
        val context = getApplication<Application>().applicationContext
        ClipboardUtils.copyTextToClipboard(context, clip.text)
        ClipboardUtils.triggerHapticFeedback(context)
        refreshActiveClipboardPeek()

        viewModelScope.launch {
            _feedbackEvents.emit(UserFeedback("Copied to clipboard!"))
        }
    }

    fun togglePin(clip: ClipItem) {
        val context = getApplication<Application>().applicationContext
        ClipboardUtils.triggerHapticFeedback(context)
        viewModelScope.launch {
            repository.togglePin(clip.id)
            val action = if (!clip.isPinned) "Pinned to top" else "Unpinned"
            _feedbackEvents.emit(UserFeedback(action))
        }
    }

    fun deleteClip(clip: ClipItem) {
        val currentList = allClips.value
        val index = currentList.indexOfFirst { it.id == clip.id }
        recentlyDeletedClip = clip to index

        viewModelScope.launch {
            repository.deleteClip(clip.id)
            _feedbackEvents.emit(
                UserFeedback(
                    message = "Snippet removed",
                    actionLabel = "UNDO",
                    onAction = { restoreLastDeleted() }
                )
            )
        }
    }

    fun restoreLastDeleted() {
        val deleted = recentlyDeletedClip ?: return
        viewModelScope.launch {
            repository.restoreClip(deleted.first, deleted.second)
            recentlyDeletedClip = null
            _feedbackEvents.emit(UserFeedback("Snippet restored"))
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            _feedbackEvents.emit(UserFeedback("All clipboard history cleared"))
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ClipFilter) {
        _selectedFilter.value = filter
    }
}
