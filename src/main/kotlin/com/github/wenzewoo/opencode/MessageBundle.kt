package com.github.wenzewoo.opencode

import com.intellij.DynamicBundle
import java.util.function.Supplier

object MessageBundle : DynamicBundle("messages.MessageBundle") {
    @JvmStatic
    fun message(key: String, vararg params: Any): String = getMessage(key, *params)

    @JvmStatic
    fun messagePointer(key: String, vararg params: Any): Supplier<String> = getLazyMessage(key, *params)
}
