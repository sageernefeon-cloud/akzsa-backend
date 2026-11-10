package com.nefeon.akzsa.keyboard

/** Public entry points so the settings screen (another package) can edit the toolbar order. */
fun toolbarOrderLabels(csv: String): List<String> = ToolbarOrder.normalise(csv)
fun moveToolbarItem(csv: String, label: String, delta: Int): String = ToolbarOrder.move(csv, label, delta)
