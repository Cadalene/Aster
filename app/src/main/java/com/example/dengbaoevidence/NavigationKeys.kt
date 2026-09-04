package com.example.dengbaoevidence

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Projects : NavKey

@Serializable data object TemplateManagement : NavKey

@Serializable data object AppSettings : NavKey

@Serializable data class TemplateEditor(val templateId: Long) : NavKey

@Serializable data class ProjectDetails(val projectId: Long) : NavKey

@Serializable data class DeviceDetails(val deviceId: Long) : NavKey

@Serializable data class EvidenceDetails(val itemId: Long) : NavKey

@Serializable data class CameraCapture(val itemId: Long) : NavKey

@Serializable data class PhotoViewer(val itemId: Long, val initialIndex: Int) : NavKey
