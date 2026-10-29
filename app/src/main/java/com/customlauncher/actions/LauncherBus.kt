package com.customlauncher.actions

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Commands that only the home screen UI can carry out. */
sealed interface UiCommand {
    data object OpenSearch : UiCommand
    data object OpenDrawer : UiCommand
    data object EnterEditMode : UiCommand
    data object OpenGestureCanvas : UiCommand
    data object NextPage : UiCommand
    data object PreviousPage : UiCommand
    data class OpenWidget(val instanceId: String) : UiCommand
    data class Message(val text: String) : UiCommand
}

/** App-wide event bus between non-UI code (services, executor) and the home screen. */
class LauncherBus {
    private val _commands = MutableSharedFlow<UiCommand>(extraBufferCapacity = 32)
    val commands: SharedFlow<UiCommand> = _commands.asSharedFlow()

    fun send(command: UiCommand) {
        _commands.tryEmit(command)
    }
}
