package com.pinterest.ktlint.ruleset.experimental

import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.test.diffFileFormat
import com.pinterest.ktlint.test.diffFileLint
import com.pinterest.ktlint.test.format
import com.pinterest.ktlint.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MaxLineLengthRuleTest {
    val userData = mapOf("max_line_length" to "100")

    @Test
    fun classWithParameterList() {
        val unformattedFunction =
            "class MyLongClassHolder(src: Int, dst: Int) : MyLongHolder<MyFavouriteVeryLongClass>(), SomeOtherInterface { fun foo() { /*...*/ } }"
            .trimIndent()

        val formattedFunction =
            """
            class MyLongClassHolder(
                src: Int,
                dst: Int
            ) : MyLongHolder<MyFavouriteVeryLongClass>(),
                SomeOtherInterface
            {
                fun foo() { /*...*/ }
            }

            """.trimIndent()

        assertThat(
            MaxLineLengthRule().format(
                unformattedFunction, userData
            )
        ).isEqualTo(formattedFunction)
    }

    @Test
    fun funWithParameterList() {
        val unformattedFunction =
            "private fun userDataResolver(editorConfigPath: String?, debug: Boolean): (String?) -> Map<String, String> {" +
            "    val workDir = File(name).canonicalPath" +
            "}".trimIndent()

        val formattedFunction =
            """
            private fun userDataResolver(
                editorConfigPath: String?,
                debug: Boolean
            ): (String?) -> Map<String, String> {
                val workDir = File(name).canonicalPath
            }

            """.trimIndent()

        assertThat(
            MaxLineLengthRule().format(
                unformattedFunction, userData
            )
        ).isEqualTo(formattedFunction)
    }

    @Test
    fun eqAndIfElse() {
        val unformattedFunction =
            "var orgFile = if (inFileName.takeLast(2) == \"kt\") FileWriter(File(args[0].take(inFileName.length - 2) + \"org.kt\")) else FileWriter(File(inFileName))" +
            "".trimIndent()

        val formattedFunction =
            """
            var orgFile =
                if (inFileName.takeLast(2) == "kt") {
                    FileWriter(File(args[0].take(inFileName.length - 2) + "org.kt"))
                } else {
                    FileWriter(File(inFileName))
                }

            """.trimIndent()

        assertThat(
            MaxLineLengthRule().format(
                unformattedFunction, userData
            )
        ).isEqualTo(formattedFunction)
    }

    @Test
    fun ifWithAndOrLists() {
        val unformattedFunction =
            "fun foo() {\n" +
            "    if (!File(inFileName).exists() || !(argc > 3 && args[i++].toIntOrNull() > 0 && outFile != null)) {\n" +
            "        println(\"File inFileName does not exist. This is test on Maximum Line auto correction example\")\n" +
            "    }\n" +
            "}".trimIndent()

        val formattedFunction =
            """
            fun foo() {
                if (!File(inFileName).exists() ||
                    !(argc > 3 && args[i++].toIntOrNull() > 0 && outFile != null)
                ) {
                    println(
                        "File inFileName does not exist. This is test on Maximum Line auto correction example"
                    )
                }
            }
            """.trimIndent()

        assertThat(
            MaxLineLengthRule().format(
                unformattedFunction, userData
            )
        ).isEqualTo(formattedFunction)
    }

    @Test
    fun withLineEndCommentAndLamda() {
        val unformattedFunction =
            "class foo() {\n" +
            "    override fun of(dir: Path) = generateSequence(locate(dir)) { seed -> locate(seed.parent.parent) } /* seed.parent == .editorconfig dir */\n" +
            "}".trimIndent()

        val formattedFunction =
            """
            class foo() {
                /* seed.parent == .editorconfig dir */
                override fun of(dir: Path) = generateSequence(locate(dir)) { seed ->
                    locate(seed.parent.parent)
                }
            }
            """.trimIndent()

        assertThat(
            MaxLineLengthRule().format(
                unformattedFunction, userData
            )
        ).isEqualTo(formattedFunction)
    }
}
