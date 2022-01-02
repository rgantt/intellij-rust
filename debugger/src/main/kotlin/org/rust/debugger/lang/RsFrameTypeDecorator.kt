/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.lang

import com.jetbrains.cidr.execution.debugger.CidrFrameTypeDecorator
import com.jetbrains.cidr.execution.debugger.CidrStackFrame
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import com.jetbrains.cidr.execution.debugger.evaluation.CidrPhysicalValue
import com.jetbrains.cidr.toolchains.OSType

class RsFrameTypeDecorator(private val frame: CidrStackFrame) : CidrFrameTypeDecorator {
    override fun getValueDisplayType(value: CidrPhysicalValue, renderForUiLabel: Boolean): String {
        val driverConfiguration = frame.process.runParameters.debuggerDriverConfiguration
        // Check if MSVC LLDB is used
        if (driverConfiguration.hostMachine.osType == OSType.WIN && driverConfiguration is LLDBDriverConfiguration) {
//        if (true) {

            // https://github.com/rust-lang/rust/pull/79184
            // https://github.com/rust-lang/rust/pull/85292
            // https://github.com/rust-lang/rust/pull/86983
            /*
            "str" to "&str",
            "slice$<T>" to "&[T]",
            "tuple$<T, ...>" to "(T, ...)",
            "enum$<T>" to "T",
            "enum$<T1, T2>" to "T1::T2",
            "enum$<T1, T2, T3, T4>" to "???"
            */

            val type = value.type
            val typeNode = TypeNode.parse(type)
            typeNode.replaceMSVCTypes()

            // Wouldn't work because types like `enum$<...>` are grammatically incorrect
            // val builder = value.process.project.createAdaptedRustPsiBuilder(type)
            // RustParser.TypeReference(builder, 0)
            // val typeReference = builder.treeBuilt.psi

            val x = 123
        }
        return super.getValueDisplayType(value, renderForUiLabel)
    }
}

private class TypeNode(
    val parent: TypeNode?,
    val base: StringBuilder = StringBuilder(),
    val children: MutableList<TypeNode> = mutableListOf(),
    var bracket: Char = '<'
) {
    fun addChild(): TypeNode {
        val child = TypeNode(this)
        children.add(child)
        return child
    }

    override fun toString(): String =
        if (children.isEmpty()) {
            base.toString()
        } else {
            val closingBracket = when (bracket) {
                '<' -> '>'
                '[' -> ']'
                '(' -> ')'
                else -> error("Unexpected bracket symbol")
            }
            "$base$bracket${children.joinToString(", ")}$closingBracket"
        }


    fun replaceMSVCTypes() {
        when (base.toString()) {
            "str" -> {
                base.insert(0, '&')
            }

            "slice$" -> {
                base.setLength(0)
                base.append('&')
                bracket = '['
            }

            "tuple$" -> {
                base.setLength(0)
                bracket = '('
            }

            "enum$" -> {
                when (children.size) {
                    1 -> {
                        base.setLength(0)
                        base.append(children.first())
                        children.clear()
                    }
                    2 -> {
                        base.setLength(0)
                        base.append("${children[0]}::${children[1]}")
                    }
                    4 -> {
                        TODO("Support enum$<T1, T2, T3, T4>")
                    }
                    else -> error("Unexpected enum description")
                }
            }

            // for testing only
            // "alloc::vec::Vec" -> {
            //    base.setLength(0)
            //    bracket = '{'
            //}
        }

        for (child in children) {
            child.replaceMSVCTypes()
        }
    }

    companion object {
        fun parse(type: String): TypeNode = TypeParser(type).parse()

        private class TypeParser(private val type: String) {
            private var position: Int = 0
            private val char: Char
                get() = type[position]

            private val topLevelTypeNode = TypeNode(null)
            private var currentType = topLevelTypeNode

            fun parse(): TypeNode {
                while (position < type.length) {
                    when {
                        char == '<' -> {
                            currentType = currentType.addChild()
                        }
                        char == '>' -> {
                            currentType = currentType.parent ?: break
                        }
                        char == ',' -> {
                            currentType = currentType.parent!!
                            currentType = currentType.addChild()
                        }
                        char.isWhitespace() && type[position - 1] == ',' -> {
                        }
                        else -> {
                            currentType.base.append(char)
                        }
                    }
                    position++
                }
                return topLevelTypeNode
            }
        }
    }
}
