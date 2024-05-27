package io.github.vinceglb.picker.core.platform.mac

import io.github.vinceglb.picker.core.platform.PlatformFilePicker
import io.github.vinceglb.picker.core.platform.mac.foundation.Foundation
import io.github.vinceglb.picker.core.platform.mac.foundation.ID
import java.awt.Window
import java.io.File

internal class MacOSFilePicker : PlatformFilePicker {
	override suspend fun pickFile(
		initialDirectory: String?,
		fileExtensions: List<String>?,
		title: String?,
		parentWindow: Window?,
	): File? {
		return callNativeMacOSPicker(
			mode = MacOSFilePickerMode.SingleFile,
			initialDirectory = initialDirectory,
			fileExtensions = fileExtensions,
			title = title
		)
	}

	override suspend fun pickFiles(
		initialDirectory: String?,
		fileExtensions: List<String>?,
		title: String?,
		parentWindow: Window?,
	): List<File>? {
		return callNativeMacOSPicker(
			mode = MacOSFilePickerMode.MultipleFiles,
			initialDirectory = initialDirectory,
			fileExtensions = fileExtensions,
			title = title
		)
	}

	override fun pickDirectory(
		initialDirectory: String?,
		title: String?,
		parentWindow: Window?,
	): File? {
		return callNativeMacOSPicker(
			mode = MacOSFilePickerMode.Directories,
			initialDirectory = initialDirectory,
			fileExtensions = null,
			title = title
		)
	}

	private fun <T> callNativeMacOSPicker(
		mode: MacOSFilePickerMode<T>,
		initialDirectory: String?,
		fileExtensions: List<String>?,
		title: String?,
	): T? {
		val pool = Foundation.NSAutoreleasePool()
		return try {
			var response: T? = null

			Foundation.executeOnMainThread(
				withAutoreleasePool = false,
				waitUntilDone = true,
			) {
				// Create the file picker
				val openPanel = Foundation.invoke("NSOpenPanel", "new")

				// Setup single, multiple selection or directory mode
				mode.setupPickerMode(openPanel)

				// Set the title
				title?.let {
					Foundation.invoke(openPanel, "setMessage:", Foundation.nsString(it))
				}

				// Set initial directory
				initialDirectory?.let {
					Foundation.invoke(openPanel, "setDirectoryURL:", Foundation.nsURL(it))
				}

				// Set file extensions
				fileExtensions?.let { extensions ->
					val items = extensions.map { Foundation.nsString(it) }
					val nsData = Foundation.invokeVarArg("NSArray", "arrayWithObjects:", *items.toTypedArray())
					Foundation.invoke(openPanel, "setAllowedFileTypes:", nsData)
				}

				// Open the file picker
				val result = Foundation.invoke(openPanel, "runModal")

				// Get the path(s) from the file picker if the user validated the selection
				if (result.toInt() == 1) {
					response = mode.getResult(openPanel)
				}
			}

			response
		} finally {
			pool.drain()
		}
	}

	private companion object {
		fun singlePath(openPanel: ID): File? {
			val url = Foundation.invoke(openPanel, "URL")
			val nsPath = Foundation.invoke(url, "path")
			val path = Foundation.toStringViaUTF8(nsPath)
			return path?.let { File(it) }
		}

		fun multiplePaths(openPanel: ID): List<File>? {
			val urls = Foundation.invoke(openPanel, "URLs")
			val urlCount = Foundation.invoke(urls, "count").toInt()

			return (0 until urlCount).mapNotNull { index ->
				val url = Foundation.invoke(urls, "objectAtIndex:", index)
				val nsPath = Foundation.invoke(url, "path")
				val path = Foundation.toStringViaUTF8(nsPath)
				path?.let { File(it) }
			}.ifEmpty { null }
		}
	}

	private sealed class MacOSFilePickerMode<T> {
		abstract fun setupPickerMode(openPanel: ID)
		abstract fun getResult(openPanel: ID): T?

		data object SingleFile : MacOSFilePickerMode<File?>() {
			override fun setupPickerMode(openPanel: ID) {
				Foundation.invoke(openPanel, "setCanChooseFiles:", true)
				Foundation.invoke(openPanel, "setCanChooseDirectories:", false)
			}

			override fun getResult(openPanel: ID): File? = singlePath(openPanel)
		}

		data object MultipleFiles : MacOSFilePickerMode<List<File>>() {
			override fun setupPickerMode(openPanel: ID) {
				Foundation.invoke(openPanel, "setCanChooseFiles:", true)
				Foundation.invoke(openPanel, "setCanChooseDirectories:", false)
				Foundation.invoke(openPanel, "setAllowsMultipleSelection:", true)
			}

			override fun getResult(openPanel: ID): List<File>? = multiplePaths(openPanel)
		}

		data object Directories : MacOSFilePickerMode<File>() {
			override fun setupPickerMode(openPanel: ID) {
				Foundation.invoke(openPanel, "setCanChooseFiles:", false)
				Foundation.invoke(openPanel, "setCanChooseDirectories:", true)
			}

			override fun getResult(openPanel: ID): File? = singlePath(openPanel)
		}
	}
}
