package com.example.dengbaoevidence.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.example.dengbaoevidence.theme.AppAppearance

object AppSettings {
  private const val PREFERENCES_NAME = "app_settings"
  private const val APPEARANCE = "appearance"
  private const val DEFAULT_WORD_EXPORT_TREE_URI = "default_word_export_tree_uri"
  private const val DEFAULT_PROJECT_PACKAGE_EXPORT_TREE_URI = "default_project_package_export_tree_uri"
  private const val EXPANDED_DEVICE_TYPES_PREFIX = "expanded_device_types_"
  private const val PROJECT_LIST_SCROLL_PREFIX = "project_list_scroll_"

  fun appearance(context: Context): AppAppearance =
    AppAppearance.fromStorage(
      context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(APPEARANCE, null),
    )

  fun setAppearance(context: Context, appearance: AppAppearance) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit { putString(APPEARANCE, appearance.storageValue) }
  }

  fun defaultWordExportTreeUri(context: Context): Uri? =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString(DEFAULT_WORD_EXPORT_TREE_URI, null)
      ?.let(Uri::parse)

  fun setDefaultWordExportTreeUri(context: Context, uri: Uri) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit { putString(DEFAULT_WORD_EXPORT_TREE_URI, uri.toString()) }
  }

  fun clearDefaultWordExportTreeUri(context: Context) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit { remove(DEFAULT_WORD_EXPORT_TREE_URI) }
  }

  fun defaultProjectPackageExportTreeUri(context: Context): Uri? =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getString(DEFAULT_PROJECT_PACKAGE_EXPORT_TREE_URI, null)
      ?.let(Uri::parse)

  fun setDefaultProjectPackageExportTreeUri(context: Context, uri: Uri) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit { putString(DEFAULT_PROJECT_PACKAGE_EXPORT_TREE_URI, uri.toString()) }
  }

  fun clearDefaultProjectPackageExportTreeUri(context: Context) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit { remove(DEFAULT_PROJECT_PACKAGE_EXPORT_TREE_URI) }
  }

  fun expandedDeviceTypes(context: Context, projectId: Long): Set<String> =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getStringSet(expandedDeviceTypesKey(projectId), emptySet())
      ?.toSet()
      .orEmpty()

  fun setDeviceTypeExpanded(context: Context, projectId: Long, typeName: String, expanded: Boolean) {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val key = expandedDeviceTypesKey(projectId)
    val expandedTypes = preferences.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
    if (expanded) expandedTypes.add(typeName) else expandedTypes.remove(typeName)
    preferences.edit { putStringSet(key, expandedTypes) }
  }

  fun clearProjectUiState(context: Context, projectId: Long) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit {
        remove(expandedDeviceTypesKey(projectId))
        remove(projectListScrollIndexKey(projectId))
        remove(projectListScrollOffsetKey(projectId))
      }
  }

  fun projectListScrollPosition(context: Context, projectId: Long): ProjectListScrollPosition {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    return ProjectListScrollPosition(
      index = preferences.getInt(projectListScrollIndexKey(projectId), 0),
      offset = preferences.getInt(projectListScrollOffsetKey(projectId), 0),
    )
  }

  fun setProjectListScrollPosition(context: Context, projectId: Long, index: Int, offset: Int) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit {
        putInt(projectListScrollIndexKey(projectId), index.coerceAtLeast(0))
        putInt(projectListScrollOffsetKey(projectId), offset.coerceAtLeast(0))
      }
  }

  private fun expandedDeviceTypesKey(projectId: Long): String = "$EXPANDED_DEVICE_TYPES_PREFIX$projectId"
  private fun projectListScrollIndexKey(projectId: Long): String = "$PROJECT_LIST_SCROLL_PREFIX${projectId}_index"
  private fun projectListScrollOffsetKey(projectId: Long): String = "$PROJECT_LIST_SCROLL_PREFIX${projectId}_offset"

}

data class ProjectListScrollPosition(val index: Int, val offset: Int)
