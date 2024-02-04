package cn.a10miaomiao.bilimiao.compose.base

import android.os.Bundle
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDeepLink
import androidx.navigation.NavHostController
import cn.a10miaomiao.bilimiao.compose.comm.navigation.NavDestinationBuilder
import com.a10miaomiao.bilimiao.comm.utils.DebugMiao

abstract class ComposePage {

    abstract val route: String

    open val arguments: List<NamedNavArgument> = autoGetArguments()

    open val deepLinks: List<NavDeepLink> = emptyList()

    open fun url(): String {
        return route
    }

    open fun url(params: Map<String, String>): String {
        var url = route
        arguments.forEach {
            url = url.replace("{${it.name}}", params[it.name]!!)
        }
        return url
    }

    @Composable
    abstract fun AnimatedContentScope.Content(navEntry: NavBackStackEntry)

    fun <T> Bundle.get(arg: PageArg<T>): T {
        return this.get(arg.name) as T
    }

    fun <T> Bundle.getOrNull(arg: PageArg<T>): T? {
        return this.get(arg.name) as? T
    }

    infix fun <T> PageArg<T>.set(value: T) {
        this.value = value
    }

    private fun autoGetArguments(): List<NamedNavArgument> {
        val typeName = PageArg::class.java.name
        return this::class.java.methods.filter {
            it.returnType.name == typeName
        }.mapNotNull {
            DebugMiao.log(it.name)
            (it.invoke(this) as? PageArg<*>)?.namedNavArgument
        }
    }

}

fun NavHostController.navigate(page: ComposePage) = navigate(page.url())

inline fun <T : ComposePage> NavHostController.navigate(
    page: T,
    initArgs: T.() -> Unit,
) = navigate(page.also(initArgs))