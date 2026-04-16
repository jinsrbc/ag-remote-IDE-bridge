package com.example.gemmaai.data

enum class Role {
    USER, ASSISTANT, SYSTEM
}

enum class MessageType {
    TEXT,
    IMAGE,
    SEARCH_RESULT,
    COMMAND_RESULT,
    LOADING,
    ERROR
}

data class SearchResult(
    val title: String,
    val snippet: String,
    val url: String?,
    val source: String,
    val isOffline: Boolean = false
)

sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class NeedsPermission(val permission: String, val message: String) : CommandResult()
    data class NotSupported(val reason: String) : CommandResult()
    data class OpenedApp(val appName: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role = Role.USER,
    val content: String = "",
    val type: MessageType = MessageType.TEXT,
    val imageUri: String? = null,
    val searchResults: List<SearchResult>? = null,
    val commandResult: CommandResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)
