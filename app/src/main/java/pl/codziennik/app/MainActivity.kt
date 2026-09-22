package pl.codziennik.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import pl.codziennik.app.data.CodziennikDatabase
import java.util.Calendar

/** Hosts navigation only; individual screens live in CodziennikScreens. */
class MainActivity : Activity() {
    val database by lazy { CodziennikDatabase(this) }
    val selectedDate = Calendar.getInstance()
    private lateinit var screens: CodziennikScreens
    private val photoRequest = 701
    override fun onCreate(state: Bundle?) { super.onCreate(state); screens = CodziennikScreens(this); screens.showHome() }
    override fun onDestroy() { database.close(); super.onDestroy() }
    fun open(route: String) = when(route) {
        "home" -> screens.showHome(); "tasks" -> screens.showTasks(); "shopping" -> screens.showShopping();
        "calendar" -> screens.showCalendar(); "more" -> screens.showMore(); "settings" -> screens.showSettings();
        "photos" -> screens.showPhotos(); "stats" -> screens.showStats(); else -> screens.showPlaceholder(route)
    }
    fun pickPhoto() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*";addCategory(Intent.CATEGORY_OPENABLE);addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) },photoRequest) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data);if(requestCode==photoRequest&&resultCode==RESULT_OK)data?.data?.let { uri: Uri -> runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) };database.savePhoto(java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US).format(selectedDate.time),uri.toString());screens.showPhotos() } }
}
