package pl.codziennik.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import pl.codziennik.app.data.CodziennikDatabase
import pl.codziennik.app.data.Habit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("codziennik", MODE_PRIVATE) }
    private val database by lazy { CodziennikDatabase(this) }
    private val backgrounds = listOf(R.drawable.bg_lake, R.drawable.bg_forest, R.drawable.bg_mountains, R.drawable.bg_grass, R.drawable.bg_meadow)
    private val selectedDate = Calendar.getInstance()
    private var photoView: ImageView? = null
    private val photoRequest = 701

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); buildDay() }
    override fun onDestroy() { database.close(); super.onDestroy() }

    private val dayKey get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDate.time)
    private val dayLabel get() = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("pl", "PL")).format(selectedDate.time).replaceFirstChar { it.uppercase() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun card(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply { this.text=value; textSize=size; setTextColor(color) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text=value; setOnClickListener { action() } }

    private fun buildDay() {
        val root = FrameLayout(this)
        root.addView(ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(backgrounds[prefs.getInt("background", 0).coerceIn(0, backgrounds.lastIndex)]) }, FrameLayout.LayoutParams(-1,-1))
        root.addView(View(this).apply { setBackgroundColor(Color.argb(90, 0, 0, 0)) }, FrameLayout.LayoutParams(-1,-1))
        val content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(20)) }
        root.addView(ScrollView(this).apply { addView(content) }, FrameLayout.LayoutParams(-1,-1)); setContentView(root)

        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(button("☰") { showMenu() }, LinearLayout.LayoutParams(dp(52),dp(48)))
        header.addView(LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; addView(text("Codziennik",28f).apply { gravity=Gravity.CENTER }); addView(text("MAŁE KROKI, WIELKIE ZMIANY",10f).apply { gravity=Gravity.CENTER; letterSpacing=.15f }) }, LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("⌕") { Toast.makeText(this, "Wyszukiwanie pojawi się po zebraniu historii wpisów.", Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(dp(52),dp(48))); content.addView(header)

        val navigation = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        navigation.addView(button("‹") { selectedDate.add(Calendar.DAY_OF_MONTH,-1); buildDay() }, LinearLayout.LayoutParams(dp(52),dp(44)))
        navigation.addView(text(dayLabel,18f).apply { gravity=Gravity.CENTER; setOnClickListener { chooseDate() } }, LinearLayout.LayoutParams(0,-2,1f))
        navigation.addView(button("›") { selectedDate.add(Calendar.DAY_OF_MONTH,1); buildDay() }, LinearLayout.LayoutParams(dp(52),dp(44))); content.addView(navigation)

        val habits = database.habits(); val done = database.completed(dayKey)
        content.addView(text(if (habits.isEmpty()) "Dodaj pierwszy nawyk w menu." else "Postęp dnia: ${done.count { it in habits.map(Habit::id) }} / ${habits.size}", 18f).apply { gravity=Gravity.CENTER; setPadding(0,0,0,dp(8)) })
        habits.forEach { habit -> content.addView(habitRow(habit, done.contains(habit.id)), LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(7) }) }
        content.addView(memoryCard(), LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        content.addView(noteCard(), LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
    }

    private fun habitRow(habit: Habit, completed: Boolean): CheckBox = CheckBox(this).apply {
        text="${habit.icon}  ${habit.title}"; textSize=16f; setTextColor(Color.rgb(45,48,44)); setPadding(dp(10),0,dp(8),0); background=card(Color.argb(240,242,235,221),18); isChecked=completed
        if(completed) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        setOnCheckedChangeListener { _, checked -> database.setCompleted(dayKey, habit.id, checked); buildDay() }
    }

    private fun memoryCard(): View {
        val record=database.record(dayKey); return LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(14)); background=card(Color.argb(242,242,235,221),22)
            addView(LinearLayout(this@MainActivity).apply { gravity=Gravity.CENTER_VERTICAL; addView(text("Pamiątka z dnia",20f,Color.rgb(45,48,44)),LinearLayout.LayoutParams(0,-2,1f)); addView(button("Zmień") { choosePhoto() }) })
            photoView=ImageView(this@MainActivity).apply { scaleType=ImageView.ScaleType.CENTER_CROP; background=card(Color.rgb(225,219,208),18); contentDescription="Pamiątka z dnia"; record.photoUri?.let { runCatching { setImageURI(Uri.parse(it)) } } }
            addView(photoView,LinearLayout.LayoutParams(-1,dp(190)).apply { topMargin=dp(6) }); addView(text(if(record.photoUri==null) "Dodaj zdjęcie" else "Dotknij „Zmień”, aby wybrać inne zdjęcie",14f,Color.rgb(35,58,42)).apply { gravity=Gravity.CENTER; setPadding(0,dp(8),0,0); setOnClickListener { choosePhoto() } })
        }
    }

    private fun noteCard(): View { val note=EditText(this).apply { hint="Co dziś zapamiętać?"; setText(database.record(dayKey).note); minLines=3; gravity=Gravity.TOP; setTextColor(Color.DKGRAY); setHintTextColor(Color.GRAY); background=card(Color.WHITE,12); setPadding(dp(12),dp(8),dp(12),dp(8)) }
        return LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(12)); background=card(Color.argb(242,242,235,221),22); addView(text("Notatka dnia",19f,Color.rgb(45,48,44))); addView(note,LinearLayout.LayoutParams(-1,dp(95)).apply { topMargin=dp(6) }); addView(button("ZAPISZ NOTATKĘ") { database.saveNote(dayKey,note.text.toString()); Toast.makeText(this@MainActivity,"Zapisano",Toast.LENGTH_SHORT).show() }) }
    }

    private fun chooseDate() { DatePickerDialog(this, { _, y,m,d -> selectedDate.set(y,m,d); buildDay() }, selectedDate.get(Calendar.YEAR),selectedDate.get(Calendar.MONTH),selectedDate.get(Calendar.DAY_OF_MONTH)).show() }
    private fun choosePhoto() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION) }, photoRequest) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==photoRequest && resultCode==RESULT_OK) data?.data?.let { uri -> runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; database.savePhoto(dayKey,uri.toString()); buildDay() } }

    private fun showMenu() { AlertDialog.Builder(this).setTitle("Codziennik 2.0").setItems(arrayOf("Nawyki", "Wybierz tło", "Wybierz datę", "O danych")) { _, which -> when(which) { 0->manageHabits(); 1->chooseBackground(); 2->chooseDate(); else->AlertDialog.Builder(this).setTitle("Twoje dane").setMessage("Wpisy są przechowywane lokalnie na tym urządzeniu. Dane z poprzedniej wersji zostały bezpiecznie zaimportowane.").setPositiveButton("OK",null).show() } }.show() }
    private fun manageHabits() { val habits=database.allHabits(); val labels=habits.map { "${it.icon}  ${it.title}" }.toTypedArray(); val checked=habits.map { it.active }.toBooleanArray(); AlertDialog.Builder(this).setTitle("Nawyki").setMultiChoiceItems(labels,checked) { _,pos,isChecked -> database.setHabitActive(habits[pos].id,isChecked) }.setNeutralButton("Dodaj") { _,_-> addHabitDialog() }.setPositiveButton("Gotowe") { _,_->buildDay() }.show() }
    private fun addHabitDialog() { val input=EditText(this).apply { hint="Nazwa nowego nawyku" }; AlertDialog.Builder(this).setTitle("Nowy nawyk").setView(input).setPositiveButton("Dodaj") { _,_-> if(input.text.trim().isNotEmpty()) { database.addHabit(input.text.toString()); buildDay() } }.setNegativeButton("Anuluj",null).show() }
    private fun chooseBackground() { val names=arrayOf("Jezioro", "Las", "Góry", "Trawy", "Łąka"); AlertDialog.Builder(this).setTitle("Wybierz tło").setSingleChoiceItems(names,prefs.getInt("background",0)) { d,which -> prefs.edit().putInt("background",which).apply(); d.dismiss(); buildDay() }.setNegativeButton("Anuluj",null).show() }
}
