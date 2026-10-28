package moe.n4tsu.dextop

import android.view.KeyEvent

internal data class LaptopKey(
    val label: String,
    val code: Int,
    val weight: Float = 1f,
)

/** Static keyboard geometry kept independent from the session service. */
internal object LaptopKeyboardLayout {
    const val SHIFT = -1
    const val CONTROL = -2
    const val ALT = -3
    const val CAPS = -4
    const val FN = -5
    const val MENU = -6
    const val BACK = -7
    const val SPACER = -8
    const val SYM = -9

    /**
     * Compact Gboard-inspired touch layout for Laptop Mode.
     *
     * Keeps Android key codes for reliable desktop input, but uses phone-style
     * staggered QWERTY rows and a dedicated bottom action row instead of a
     * physical PC keyboard layout.
     */
    fun laptopRows(showFunctionRow: Boolean): List<List<LaptopKey>> {
        val rows = mutableListOf<List<LaptopKey>>()
        if (showFunctionRow) rows += listOf(
            LaptopKey("ESC", KeyEvent.KEYCODE_ESCAPE, 1.15f),
            LaptopKey("F1", KeyEvent.KEYCODE_F1), LaptopKey("F2", KeyEvent.KEYCODE_F2),
            LaptopKey("F3", KeyEvent.KEYCODE_F3), LaptopKey("F4", KeyEvent.KEYCODE_F4),
            LaptopKey("F5", KeyEvent.KEYCODE_F5), LaptopKey("F6", KeyEvent.KEYCODE_F6),
            LaptopKey("F7", KeyEvent.KEYCODE_F7), LaptopKey("F8", KeyEvent.KEYCODE_F8),
            LaptopKey("F9", KeyEvent.KEYCODE_F9), LaptopKey("F10", KeyEvent.KEYCODE_F10),
            LaptopKey("F11", KeyEvent.KEYCODE_F11), LaptopKey("F12", KeyEvent.KEYCODE_F12),
            LaptopKey("DEL", KeyEvent.KEYCODE_FORWARD_DEL, 1.15f),
        )
        rows += listOf(
            LaptopKey("Q", KeyEvent.KEYCODE_Q), LaptopKey("W", KeyEvent.KEYCODE_W),
            LaptopKey("E", KeyEvent.KEYCODE_E), LaptopKey("R", KeyEvent.KEYCODE_R),
            LaptopKey("T", KeyEvent.KEYCODE_T), LaptopKey("Y", KeyEvent.KEYCODE_Y),
            LaptopKey("U", KeyEvent.KEYCODE_U), LaptopKey("I", KeyEvent.KEYCODE_I),
            LaptopKey("O", KeyEvent.KEYCODE_O), LaptopKey("P", KeyEvent.KEYCODE_P),
        )
        rows += listOf(
            LaptopKey("A", KeyEvent.KEYCODE_A), LaptopKey("S", KeyEvent.KEYCODE_S),
            LaptopKey("D", KeyEvent.KEYCODE_D), LaptopKey("F", KeyEvent.KEYCODE_F),
            LaptopKey("G", KeyEvent.KEYCODE_G), LaptopKey("H", KeyEvent.KEYCODE_H),
            LaptopKey("J", KeyEvent.KEYCODE_J), LaptopKey("K", KeyEvent.KEYCODE_K),
            LaptopKey("L", KeyEvent.KEYCODE_L),
        )
        rows += listOf(
            LaptopKey("⇧", SHIFT, 1.35f),
            LaptopKey("Z", KeyEvent.KEYCODE_Z), LaptopKey("X", KeyEvent.KEYCODE_X),
            LaptopKey("C", KeyEvent.KEYCODE_C), LaptopKey("V", KeyEvent.KEYCODE_V),
            LaptopKey("B", KeyEvent.KEYCODE_B), LaptopKey("N", KeyEvent.KEYCODE_N),
            LaptopKey("M", KeyEvent.KEYCODE_M),
            LaptopKey("⌫", KeyEvent.KEYCODE_DEL, 1.35f),
        )
        rows += listOf(
            LaptopKey("?123", SYM, 1.45f),
            LaptopKey(",", KeyEvent.KEYCODE_COMMA, .85f),
            LaptopKey("SPACE", KeyEvent.KEYCODE_SPACE, 4.5f),
            LaptopKey(".", KeyEvent.KEYCODE_PERIOD, .85f),
            LaptopKey("↵", KeyEvent.KEYCODE_ENTER, 1.35f),
        )
        return rows
    }

    fun blackBerryTopRow(showFunctionRow: Boolean): List<LaptopKey> =
        if (showFunctionRow) {
            listOf(
                LaptopKey("ESC", KeyEvent.KEYCODE_ESCAPE, 1.1f),
                LaptopKey("F1", KeyEvent.KEYCODE_F1), LaptopKey("F2", KeyEvent.KEYCODE_F2),
                LaptopKey("F3", KeyEvent.KEYCODE_F3), LaptopKey("F4", KeyEvent.KEYCODE_F4),
                LaptopKey("F5", KeyEvent.KEYCODE_F5), LaptopKey("F6", KeyEvent.KEYCODE_F6),
                LaptopKey("F7", KeyEvent.KEYCODE_F7), LaptopKey("F8", KeyEvent.KEYCODE_F8),
                LaptopKey("F9", KeyEvent.KEYCODE_F9), LaptopKey("F10", KeyEvent.KEYCODE_F10),
                LaptopKey("F11", KeyEvent.KEYCODE_F11), LaptopKey("F12", KeyEvent.KEYCODE_F12),
            )
        } else {
            listOf(
                LaptopKey("←", BACK, 1.35f),
                LaptopKey("", KeyEvent.KEYCODE_META_LEFT, 1.35f),
                LaptopKey("MENU", MENU, 1.35f),
            )
        }

    fun blackBerryRows(showFunctionRow: Boolean): List<List<LaptopKey>> = listOf(
        blackBerryTopRow(showFunctionRow),
        listOf(
            LaptopKey("#\nQ", KeyEvent.KEYCODE_Q), LaptopKey("1\nW", KeyEvent.KEYCODE_W),
            LaptopKey("2\nE", KeyEvent.KEYCODE_E), LaptopKey("3\nR", KeyEvent.KEYCODE_R),
            LaptopKey("(\nT", KeyEvent.KEYCODE_T), LaptopKey(")\nY", KeyEvent.KEYCODE_Y),
            LaptopKey("_\nU", KeyEvent.KEYCODE_U), LaptopKey("-\nI", KeyEvent.KEYCODE_I),
            LaptopKey("+\nO", KeyEvent.KEYCODE_O), LaptopKey("@\nP", KeyEvent.KEYCODE_P),
        ),
        listOf(
            LaptopKey("*\nA", KeyEvent.KEYCODE_A), LaptopKey("4\nS", KeyEvent.KEYCODE_S),
            LaptopKey("5\nD", KeyEvent.KEYCODE_D), LaptopKey("6\nF", KeyEvent.KEYCODE_F),
            LaptopKey("/\nG", KeyEvent.KEYCODE_G), LaptopKey(":\nH", KeyEvent.KEYCODE_H),
            LaptopKey(";\nJ", KeyEvent.KEYCODE_J), LaptopKey("'\nK", KeyEvent.KEYCODE_K),
            LaptopKey("\"\nL", KeyEvent.KEYCODE_L), LaptopKey("⌫", KeyEvent.KEYCODE_DEL),
        ),
        listOf(
            LaptopKey("ALT", ALT, 1.15f), LaptopKey("7\nZ", KeyEvent.KEYCODE_Z, 1.1f),
            LaptopKey("8\nX", KeyEvent.KEYCODE_X, 1.1f), LaptopKey("9\nC", KeyEvent.KEYCODE_C, 1.1f),
            LaptopKey("?\nV", KeyEvent.KEYCODE_V, 1.1f), LaptopKey("!\nB", KeyEvent.KEYCODE_B, 1.1f),
            LaptopKey(",\nN", KeyEvent.KEYCODE_N, 1.1f), LaptopKey(".\nM", KeyEvent.KEYCODE_M, 1.1f),
            LaptopKey("↵", KeyEvent.KEYCODE_ENTER, 1.3f),
        ),
        listOf(
            LaptopKey("CTRL", CONTROL, 1.5f), LaptopKey("SHIFT", SHIFT, 1.1f),
            LaptopKey("SPACE", KeyEvent.KEYCODE_SPACE, 4.8f),
            LaptopKey("SYM", SYM, 1.25f), LaptopKey("FN", FN, 1.5f),
        ),
    )
}
