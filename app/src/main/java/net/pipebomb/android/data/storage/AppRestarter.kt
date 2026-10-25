package net.pipebomb.android.data.storage

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import net.pipebomb.android.RestartActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRestarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun restart() {
        context.startActivity(
            Intent(context, RestartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
        )
        Handler(Looper.getMainLooper()).postDelayed(
            {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(0)
            },
            500L,
        )
    }
}
