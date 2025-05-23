/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.tordnscrypt.crash_handling

import android.content.SharedPreferences
import android.util.Log
import pan.alexander.tordnscrypt.utils.preferences.PreferenceKeys.CRASH_REPORT
import pan.alexander.tordnscrypt.utils.root.RootExecService.LOG_TAG
import kotlin.system.exitProcess

class TopExceptionHandler(
    private val sharedPreferences: SharedPreferences,
    private val defaultExceptionHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(t: Thread, e: Throwable) {

        var arr = e.stackTrace
        var report = e.toString() + "\n\n"
        report += "--------- Stack trace ---------\n\n"
        for (i in arr.indices) {
            report += "    " + arr[i].toString() + "\n"
        }
        report += "-------------------------------\n\n"

        // If the exception was thrown in a background thread inside
        // AsyncTask, then the actual exception can be found with getCause
        val cause = e.cause
        if (cause != null) {
            report += "--------- Cause ---------\n\n"
            report += cause.toString() + "\n\n"
            arr = cause.stackTrace
            for (i in arr.indices) {
                report += "    " + arr[i].toString() + "\n"
            }
            report += "-------------------------------\n\n"
        }

        Log.e(LOG_TAG, report)

        saveReport(report)

        defaultExceptionHandler?.uncaughtException(t, e) ?: exitProcess(2)
    }

    private fun saveReport(report: String) {
        sharedPreferences.edit().putString(CRASH_REPORT, report).commit()
    }
}
