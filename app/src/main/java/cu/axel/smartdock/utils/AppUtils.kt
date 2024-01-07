package cu.axel.smartdock.utils

import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceManager
import cu.axel.smartdock.models.App
import cu.axel.smartdock.models.AppTask
import cu.axel.smartdock.models.DockApp
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.regex.Pattern

object AppUtils {
    const val PINNED_LIST = "pinned.lst"
    const val DOCK_PINNED_LIST = "dock_pinned.lst"
    const val DESKTOP_LIST = "desktop.lst"
    var currentApp = ""
    fun getInstalledApps(pm: PackageManager): ArrayList<App> {
        val apps = ArrayList<App>()
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val appsInfo = pm.queryIntentActivities(intent, 0)

        //TODO: Filter Google App
        for (appInfo in appsInfo) {
            val label = appInfo.activityInfo.loadLabel(pm).toString()
            val icon = appInfo.activityInfo.loadIcon(pm)
            val packageName = appInfo.activityInfo.packageName
            apps.add(App(label, packageName, icon))
        }
        apps.sortWith { app: App, app2: App -> app.name.compareTo(app2.name, ignoreCase = true) }
        return apps
    }

    fun getPinnedApps(context: Context, pm: PackageManager, type: String): ArrayList<App> {
        val apps = ArrayList<App>()
        try {
            val br = BufferedReader(FileReader(File(context.filesDir, type)))
            var applist: String
            try {
                if (br.readLine().also { applist = it } != null) {
                    val applist2 = applist.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (app in applist2) {
                        try {
                            val appInfo = pm.getApplicationInfo(app, 0)
                            apps.add(App(pm.getApplicationLabel(appInfo).toString(), app,
                                    pm.getApplicationIcon(app)))
                        } catch (e: PackageManager.NameNotFoundException) {
                            //app is no longer available, lets unpin it
                            unpinApp(context, app, type)
                        }
                    }
                }
            } catch (_: IOException) {
            }
        } catch (_: FileNotFoundException) {
        }
        return apps
    }

    fun pinApp(context: Context, app: String, type: String) {
        try {
            val file = File(context.filesDir, type)
            val fw = FileWriter(file, true)
            fw.write("$app ")
            fw.close()
        } catch (_: IOException) {
        }
    }

    fun unpinApp(context: Context, app: String, type: String) {
        try {
            val file = File(context.filesDir, type)
            val br = BufferedReader(FileReader(file))
            var applist: String
            if (br.readLine().also { applist = it } != null) {
                applist = applist.replace("$app ", "")
                val fw = FileWriter(file, false)
                fw.write(applist)
                fw.close()
            }
        } catch (_: IOException) {
        }
    }

    fun moveApp(context: Context, app: String, type: String, direction: Int) {
        try {
            val file = File(context.filesDir, type)
            val br = BufferedReader(FileReader(file))
            var applist: String
            var what = ""
            var with = ""
            if (br.readLine().also { applist = it } != null) {
                val apps = applist.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val pos = findInArray(app, apps)
                if (direction == 0 && pos > 0) {
                    what = apps[pos - 1] + " " + app
                    with = app + " " + apps[pos - 1]
                } else if (direction == 1 && pos < apps.size - 1) {
                    what = app + " " + apps[pos + 1]
                    with = apps[pos + 1] + " " + app
                }
                applist = applist.replace(what, with)
                val fw = FileWriter(file, false)
                fw.write(applist)
                fw.close()
            }
        } catch (_: IOException) {
        }
    }

    private fun findInArray(key: String, array: Array<String>): Int {
        for (i in array.indices) {
            if (array[i].contains(key)) return i
        }
        return -1
    }

    fun isPinned(context: Context, app: String, type: String): Boolean {
        try {
            val br = BufferedReader(FileReader(File(context.filesDir, type)))
            var applist: String
            if (br.readLine().also { applist = it } != null) {
                return applist.contains(app)
            }
        } catch (_: IOException) {
        }
        return false
    }

    fun isGame(pm: PackageManager, packageName: String): Boolean {
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                info.category == ApplicationInfo.CATEGORY_GAME
            } else {
                info.flags and ApplicationInfo.FLAG_IS_GAME == ApplicationInfo.FLAG_IS_GAME
            }
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getCurrentLauncher(pm: PackageManager): String {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo!!.activityInfo.packageName
    }

    fun setWindowMode(am: ActivityManager, taskId: Int, mode: Int) {
        try {
            val setWindowMode = am.javaClass.getMethod("setTaskWindowingMode", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            setWindowMode.invoke(am, taskId, mode, false)
        } catch (_: Exception) {
        }
    }

    fun getRunningTasks(am: ActivityManager, pm: PackageManager, max: Int): ArrayList<AppTask> {
        val tasksInfo = am.getRunningTasks(max)
        currentApp = tasksInfo[0].baseActivity!!.packageName
        val appTasks = ArrayList<AppTask>()
        for (taskInfo in tasksInfo) {
            try {
                //Exclude systemui, launcher and other system apps from the tasklist
                if (taskInfo.baseActivity!!.packageName.contains("com.android.systemui")
                        || taskInfo.baseActivity!!.packageName.contains("com.google.android.packageinstaller")) continue

                //Hack to save Dock settings activity ftom being excluded
                if (!(taskInfo.topActivity!!.className == "cu.axel.smartdock.activities.MainActivity" || taskInfo.topActivity!!.className == "cu.axel.smartdock.activities.DebugActivity") && taskInfo.topActivity!!.packageName == getCurrentLauncher(pm)) continue
                if (Build.VERSION.SDK_INT > 29) {
                    try {
                        val isRunning = taskInfo.javaClass.getField("isRunning")
                        val running = isRunning.getBoolean(taskInfo)
                        if (!running) continue
                    } catch (_: Exception) {
                    }
                }
                appTasks.add(
                        AppTask(taskInfo.id, pm.getActivityInfo(taskInfo.topActivity!!, 0).loadLabel(pm).toString(),
                                taskInfo.topActivity!!.packageName, pm.getActivityIcon(taskInfo.topActivity!!)))
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
        return appTasks
    }

    fun getRecentTasks(context: Context, max: Int): ArrayList<AppTask> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start,
                System.currentTimeMillis())
        val appTasks = ArrayList<AppTask>()
        usageStats.sortWith { usageStats1: UsageStats, usageStats2: UsageStats -> usageStats1.lastTimeUsed.compareTo(usageStats2.lastTimeUsed) }
        for (stat in usageStats) {
            val app = stat.packageName
            try {
                if (isLaunchable(context, app) && app != getCurrentLauncher(context.packageManager)) appTasks.add(AppTask(-1, getPackageLabel(context, app), app,
                        context.packageManager.getApplicationIcon(app)))
            } catch (_: PackageManager.NameNotFoundException) {
            }
            if (appTasks.size >= max) break
        }
        return appTasks
    }

    fun isSystemApp(context: Context, app: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(app, 0)
            appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isLaunchable(context: Context, app: String): Boolean {
        val resolveInfo = context.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app), 0)
        return resolveInfo.size > 0
    }

    fun removeTask(am: ActivityManager, id: Int) {
        try {
            val removeTask = am.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
            removeTask.invoke(am, id)
        } catch (e: Exception) {
            Log.e("Dock", e.toString() + e.cause.toString())
        }
    }

    fun getPackageLabel(context: Context, packageName: String): String {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            return pm.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
        }
        return ""
    }

    fun getAppIcon(context: Context, app: String): Drawable {
        return try {
            context.packageManager.getApplicationIcon(app)
        } catch (_: PackageManager.NameNotFoundException) {
            AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
        }
    }

    fun makeLaunchBounds(context: Context, mode: String, dockHeight: Int, secondary: Boolean): Rect {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        val deviceWidth = DeviceUtils.getDisplayMetrics(context, secondary).widthPixels
        val deviceHeight = DeviceUtils.getDisplayMetrics(context, secondary).heightPixels
        val statusHeight = DeviceUtils.getStatusBarHeight(context)
        val usableHeight = deviceHeight - dockHeight - statusHeight
        val scaleFactor = sp.getString("scale_factor", "1.0")!!.toFloat()
        when (mode) {
            "standard" -> {
                left = (deviceWidth / (5 * scaleFactor)).toInt()
                top = ((usableHeight + statusHeight) / (7 * scaleFactor)).toInt()
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }

            "maximized" -> {
                right = deviceWidth
                bottom = usableHeight
            }

            "portrait" -> {
                left = deviceWidth / 3
                top = usableHeight / 15
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }

            "tiled-left" -> {
                right = deviceWidth / 2
                bottom = usableHeight
            }

            "tiled-top" -> {
                right = deviceWidth
                bottom = (usableHeight + statusHeight) / 2
            }

            "tiled-right" -> {
                left = deviceWidth / 2
                right = deviceWidth
                bottom = usableHeight
            }

            "tiled-bottom" -> {
                right = deviceWidth
                top = (usableHeight + statusHeight) / 2
                bottom = usableHeight + statusHeight
            }
        }
        return Rect(left, top, right, bottom)
    }

    fun resizeTask(context: Context, mode: String, taskId: Int, dockHeight: Int, secondary: Boolean) {
        if (taskId < 0) return
        val bounds = makeLaunchBounds(context, mode, dockHeight, secondary)
        DeviceUtils.runAsRoot("am task resize " + taskId + " " + bounds.left + " " + bounds.top + " " + bounds.right
                + " " + bounds.bottom)
    }

    fun containsTask(apps: ArrayList<DockApp>, task: AppTask): Int {
        for (i in apps.indices) {
            if (apps[i].packageName == task.packageName) return i
        }
        return -1
    }

    fun findStackId(taskId: Int): String? {
        val stackInfo = DeviceUtils.runAsRoot("am stack list")
        val regexPattern = "(?s)Stack id=(\\d+).*?taskId=$taskId"
        val pattern = Pattern.compile(regexPattern)
        val matcher = pattern.matcher(stackInfo)
        return if (matcher.find()) matcher.group(1) else null
    }

    fun removeStack(stackId: String) {
        DeviceUtils.runAsRoot("am stack remove $stackId")
    }

    enum class WindowMode {
        STANDARD,
        PORTRAIT,
        MAXIMIZED,
        FULLSCREEN,
        TILED_TOP,
        TILED_LEFT,
        TILED_RIGHT,
        TILED_BOTTOM
    }
}
