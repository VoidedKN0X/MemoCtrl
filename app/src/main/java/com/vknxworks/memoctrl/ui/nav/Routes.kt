package com.vknxworks.memoctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object MemosRoute
@Serializable data object TagsRoute
@Serializable data object ExploreRoute
@Serializable data object SearchRoute

@Serializable data class MemoRoute(val id: String)
@Serializable data class TagRoute(val tag: String)
/**
 * The editor: a new memo when [id] is null. [share] picks up text and pictures shared from another
 * app; [text] starts a new memo with some text, like the tag of the list it was opened from.
 */
@Serializable data class EditorRoute(val id: String? = null, val share: Boolean = false, val text: String? = null)

@Serializable data object YouRoute
@Serializable data object ArchivedRoute
@Serializable data object AttachmentsRoute
@Serializable data object SettingsRoute
@Serializable data object AccountsRoute
@Serializable data object AddAccountRoute
