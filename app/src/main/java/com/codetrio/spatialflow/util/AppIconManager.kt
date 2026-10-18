package com.codetrio.spatialflow.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.codetrio.spatialflow.R

data class AppIconOption(
    val id: String,
    val title: String,
    val aliasName: String,
    @DrawableRes val previewResId: Int
)

object AppIconManager {

    val availableIcons = listOf(
        AppIconOption(
            id = "default",
            title = "Classic",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasDefault",
            previewResId = R.drawable.ic_preview_classic
        ),
        AppIconOption(
            id = "filled_dark",
            title = "Filled Dark",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasFilledDark",
            previewResId = R.drawable.ic_filled_dark
        ),
        AppIconOption(
            id = "filled_light",
            title = "Filled Light",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasFilledLight",
            previewResId = R.drawable.ic_filled_light
        ),
        AppIconOption(
            id = "nobg",
            title = "Minimalist",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasNobg",
            previewResId = R.drawable.ic_nobg
        ),
        AppIconOption(
            id = "stroked_dark",
            title = "Stroked Dark",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasStrokedDark",
            previewResId = R.drawable.ic_stroked_dark
        ),
        AppIconOption(
            id = "stroked_light",
            title = "Stroked Light",
            aliasName = "com.codetrio.spatialflow.MainActivityAliasStrokedLight",
            previewResId = R.drawable.ic_stroked_light
        )
    )

    fun getCurrentIconAlias(context: Context): String {
        val pm = context.packageManager
        val packageName = context.packageName
        return availableIcons.firstOrNull { icon ->
            val component = ComponentName(packageName, icon.aliasName)
            pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }?.aliasName ?: availableIcons.first().aliasName
    }

    fun setAppIcon(context: Context, targetAliasName: String) {
        val pm = context.packageManager
        val packageName = context.packageName

        availableIcons.forEach { icon ->
            val state = if (icon.aliasName == targetAliasName) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, icon.aliasName),
                    state,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {}
        }
    }
}
