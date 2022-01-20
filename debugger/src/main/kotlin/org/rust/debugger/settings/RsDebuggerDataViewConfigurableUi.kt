/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.settings

import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.layout.panel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.UIUtil.ComponentStyle.SMALL
import org.rust.debugger.GDBRenderers
import org.rust.debugger.LLDBRenderers
import org.rust.debugger.RsDebuggerBundle
import javax.swing.JComponent

class RsDebuggerDataViewConfigurableUi : ConfigurableUi<RsDebuggerSettings> {

    private val lldbRenderers = ComboBox<LLDBRenderers>().apply {
        LLDBRenderers.values().forEach { addItem(it) }
    }

    private val gdbRenderers = ComboBox<GDBRenderers>().apply {
        GDBRenderers.values().forEach { addItem(it) }
    }

    override fun isModified(settings: RsDebuggerSettings): Boolean =
        lldbRenderers.selectedIndex != settings.lldbRenderers.ordinal || gdbRenderers.selectedIndex != settings.gdbRenderers.ordinal

    override fun apply(settings: RsDebuggerSettings) {
        settings.lldbRenderers = LLDBRenderers.fromIndex(lldbRenderers.selectedIndex)
        settings.gdbRenderers = GDBRenderers.fromIndex(gdbRenderers.selectedIndex)
    }

    override fun reset(settings: RsDebuggerSettings) {
        lldbRenderers.selectedIndex = settings.lldbRenderers.ordinal
        gdbRenderers.selectedIndex = settings.gdbRenderers.ordinal
    }

    override fun getComponent(): JComponent = panel {
        row(RsDebuggerBundle.message("settings.rust.debugger.data.view.lldb.renderers.label")) { lldbRenderers() }
        // GDB support is available only in CLion for now
        if (PlatformUtils.isCLion()) {
            row(RsDebuggerBundle.message("settings.rust.debugger.data.view.gdb.renderers.label")) { gdbRenderers() }
        }
        row {
            label(RsDebuggerBundle.message("settings.rust.debugger.data.view.change.renderers.comment"), style = SMALL)
                .withLargeLeftGap()
        }
    }
}
