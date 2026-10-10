package com.thelightphone.sample

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec

@EntryPoint
object ToolEntryPoint : LightEntryPoint {
    override fun getToolManagerManifest() = ClientToolManifest(
        title = "Routine",
        roots = listOf(
            ClientLeafNode(FileBrowserSpec("Downloads", "exports",
                "Create a CSV or full backup in Routine → Data, then download it here. Backups contain your private records.")),
            ClientLeafNode(FileBrowserSpec("Restore Inbox", "imports",
                "Upload a Routine JSON backup here. Nothing is restored automatically. Open Routine → Data to review and confirm.")),
        ),
    )
}
