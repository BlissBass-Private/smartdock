package cu.axel.smartdock.preferences

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.AttributeSet
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cu.axel.smartdock.R
import cu.axel.smartdock.icons.IconPackHelper
import cu.axel.smartdock.utils.AppUtils


class IconPackPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private lateinit var iconPackHelper: IconPackHelper

    /*
    These are all icon pack intents to date
    It could change in the future
    but by default, I don't think we even use these any more in icon packs
    but we support all icon packs to date (Long live Ander Web)
     */
    private val LAUNCHER_INTENTS = arrayOf("com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME", "com.teslacoilsw.launcher.THEME", "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES", "org.adw.launcher.icons.ACTION_PICK_ICON",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON")

    init {
        setupPreference(context)
    }

    private fun setupPreference(context: Context) {
        iconPackHelper = IconPackHelper()
        setTitle(R.string.icon_pack)
        if (iconPackHelper.getIconPack(sharedPreferences!!) == "") {
            setSummary(R.string.system)
        } else {
            summary = AppUtils.getPackageLabel(context, iconPackHelper.getIconPack(sharedPreferences))
        }
    }

    override fun onClick() {

        /*
		We manually add Smart Dock context as a default item so Smart Dock has a default item to rely on
		 */
        val iconPackageList = ArrayList<String>()
        val iconNameList = ArrayList<String>()
        iconPackageList.add(context.packageName)
        iconNameList.add(context.getString(R.string.system))
        val launcherActivities: MutableList<ResolveInfo> = ArrayList()
        /*
		Gather all the apps installed on the device
		filter all the icon pack packages to the list
		 */for (i in LAUNCHER_INTENTS) {
            launcherActivities.addAll(context.packageManager.queryIntentActivities(Intent(i), PackageManager.GET_META_DATA))
        }
        for (ri in launcherActivities) {
            iconPackageList.add(ri.activityInfo.packageName)
            iconNameList.add(AppUtils.getPackageLabel(context, ri.activityInfo.packageName))
        }
        val cleanedNameList: Set<String> = LinkedHashSet(iconNameList)
        val newNameList = cleanedNameList.toTypedArray<String>()
        val dialog = MaterialAlertDialogBuilder(context)
        dialog.setTitle(R.string.icon_pack)
        dialog.setItems(newNameList) { _, item ->
            if (iconPackageList[item] == context.packageName) {
                sharedPreferences!!.edit().putString("icon_pack", "").apply()
                setSummary(R.string.system)
            } else {
                sharedPreferences!!.edit().putString("icon_pack", iconPackageList[item]).apply()
                summary = AppUtils.getPackageLabel(context, iconPackHelper.getIconPack(sharedPreferences))
            }
        }
        dialog.show()
    }
}
