/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.lang.core.psi.ext.edition
import org.rust.lang.core.psi.isNotRustFile
import org.rust.openapiext.*
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.buildList
import org.rust.stdext.unwrapOrElse
import java.nio.file.Path

fun RsToolchainBase.rustfmt(): Rustfmt = Rustfmt(this)

class Rustfmt(toolchain: RsToolchainBase) : RustupComponent(NAME, toolchain) {

    fun reformatDocumentTextOrNull(cargoProject: CargoProject, document: Document): String? {
        return createCommandLine(cargoProject, document)
            ?.execute(cargoProject.project, stdIn = document.text.toByteArray())
            ?.unwrapOrElse { if (isUnitTestMode) throw it else return null }
            ?.stdout
    }

    fun createCommandLine(cargoProject: CargoProject, document: Document): GeneralCommandLine? {
        val file = document.virtualFile ?: return null
        if (file.isNotRustFile || !file.isValid) return null

        val arguments = buildList<String> {
            add("--emit=stdout")

            val configPath = findConfigPathRecursively(file.parent, stopAt = cargoProject.workingDirectory)
            if (configPath != null) {
                add("--config-path")
                add(configPath.toString())
            }

            val currentRustcVersion = cargoProject.rustcInfo?.version?.semver
            if (currentRustcVersion != null) {
                val edition = runReadAction {
                    val psiFile = file.toPsiFile(cargoProject.project)
                    psiFile?.edition ?: Edition.DEFAULT
                }
                add("--edition=${edition.presentation}")
            }
        }

        return createBaseCommandLine(arguments, cargoProject.workingDirectory)
    }

    fun reformatCargoProject(
        cargoProject: CargoProject,
        owner: Disposable = cargoProject.project
    ): RsProcessResult<Unit> {
        val project = cargoProject.project
        return project.computeWithCancelableProgress("Reformatting Cargo Project with Rustfmt...") {
            project.toolchain
                ?.cargoOrWrapper(cargoProject.workingDirectory)
                ?.toGeneralCommandLine(project, CargoCommandLine.forProject(cargoProject, "fmt", listOf("--all")))
                ?.execute(owner)
                ?.map { }
                ?: Ok(Unit)
        }
    }

    companion object {
        const val NAME: String = "rustfmt"

        private val CONFIG_FILES: List<String> = listOf("rustfmt.toml", ".rustfmt.toml")

        private fun findConfigPathRecursively(directory: VirtualFile, stopAt: Path): Path? {
            val path = directory.pathAsPath
            if (!path.startsWith(stopAt) || path == stopAt) return null
            if (directory.children.any { it.name in CONFIG_FILES }) return path
            return findConfigPathRecursively(directory.parent, stopAt)
        }
    }
}
