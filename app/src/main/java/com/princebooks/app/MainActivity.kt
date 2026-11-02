package com.princebooks.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.BreakIterator
import java.util.Locale
import java.util.zip.ZipInputStream

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val navy = Color.rgb(20, 42, 67)
    private val gold = Color.rgb(221, 187, 115)
    private val muted = Color.rgb(95, 110, 126)
    private val paper = Color.rgb(246, 247, 250)
    private val prefs by lazy { getSharedPreferences("princebooks", MODE_PRIVATE) }
    private data class Book(val id: String, val title: String)
    private val books = mutableListOf<Book>()
    private var active: Book? = null
    private var bookText = ""
    private val sentences = mutableListOf<Pair<Int, Int>>()
    private var position = 0
    private var speech: TextToSpeech? = null
    private var ready = false
    private var speaking = false
    private var utterance = ""
    private var rate = 1.0f
    private var pageText: TextView? = null
    private var pageStatus: TextView? = null
    private var playControl: Button? = null
    private lateinit var body: LinearLayout
    private lateinit var tabs: LinearLayout
    private var page = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        restore()
        drawShell()
        show(0)
        speech = TextToSpeech(this, this)
    }
    private fun d(n: Int): Int = (n * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = d(radius).toFloat()
    }
    private fun text(value: String, sp: Float = 15f, color: Int = navy, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setLineSpacing(d(3).toFloat(), 1f)
        }
    private fun box(parent: LinearLayout): LinearLayout {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.WHITE, 18)
            setPadding(d(17), d(16), d(17), d(16))
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(12) })
        return view
    }
    private fun action(parent: LinearLayout, title: String, onClick: () -> Unit, primary: Boolean = true): Button {
        val button = Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            setTextColor(if (primary) Color.WHITE else navy)
            background = bg(if (primary) navy else Color.rgb(235, 239, 245), 12)
            setOnClickListener { onClick() }
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, d(49)).apply { topMargin = d(9) })
        return button
    }
    private fun title(value: String, subtitle: String) {
        body.addView(text(value, 22f, navy, true))
        body.addView(text(subtitle, 13f, muted).apply { setPadding(0, d(5), 0, d(15)) })
    }
    private fun drawShell() {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(22), d(20), d(22), d(18))
            setBackgroundColor(navy)
        }
        header.addView(text("PRINCE BOOKS", 25f, Color.WHITE, true))
        header.addView(text("READ  •  LISTEN  •  GROW", 11f, gold, true))
        shell.addView(header)
        val scrolling = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(18), d(20), d(18), d(22))
        }
        scrolling.addView(body)
        shell.addView(scrolling, LinearLayout.LayoutParams(-1, 0, 1f))
        tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(d(4), d(6), d(4), d(6))
            setBackgroundColor(Color.WHITE)
        }
        shell.addView(tabs)
        setContentView(shell)
    }
    private fun show(index: Int) {
        page = index
        body.removeAllViews()
        pageText = null
        pageStatus = null
        playControl = null
        when (index) {
            0 -> home()
            1 -> shelf()
            2 -> reader()
            3 -> library()
            else -> coach()
        }
        tabs.removeAllViews()
        listOf("Home", "Books", "Reader", "Free books", "Coach").forEachIndexed { i, label ->
            val item = text(label, 11f, if (i == index) navy else muted, i == index).apply {
                gravity = Gravity.CENTER
                setPadding(d(1), d(10), d(1), d(10))
                if (i == index) background = bg(Color.rgb(248, 236, 206), 10)
                setOnClickListener { show(i) }
            }
            tabs.addView(item, LinearLayout.LayoutParams(0, d(44), 1f))
        }
    }
    private fun home() {
        title("Build your knowledge daily", "Your offline reader and business conversation coach.")
        box(body).apply {
            addView(text("CURRENTLY READING", 11f, muted, true))
            addView(text(active?.title ?: "Start your first book", 19f, navy, true))
            action(this, if (active == null) "Import PDF, EPUB or TXT" else "Continue reading", {
                if (active == null) importBook() else show(2)
            })
        }
        box(body).apply {
            addView(text("MY BOOKS", 11f, muted, true))
            addView(text(books.size.toString() + " saved books", 20f, navy, true))
            action(this, "Open bookshelf", { show(1) }, false)
        }
        box(body).apply {
            addView(text("BETTER BUSINESS CONVERSATIONS", 11f, muted, true))
            addView(text("Learn ethical sales, recruiting and leadership skills", 16f))
            action(this, "Open lessons", { show(4) })
        }
        action(body, "Explore legally free books", { show(3) }, false)
    }
    private fun shelf() {
        title("My bookshelf", "Imported books are saved on your phone for offline use.")
        action(body, "+ Import a book", { importBook() })
        if (books.isEmpty()) box(body).addView(text("No books yet. Import a PDF, EPUB or TXT file.", 15f, muted))
        books.toList().forEach { book ->
            box(body).apply {
                addView(text(book.title, 17f, navy, true))
                action(this, "Read and listen", {
                    openBook(book)
                    show(2)
                })
                action(this, "Remove from device", {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Remove book?")
                        .setMessage("Only the imported copy inside PRINCE BOOKS will be deleted.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            stop()
                            File(filesDir, book.id).delete()
                            books.removeAll { it.id == book.id }
                            if (active?.id == book.id) {
                                active = null
                                bookText = ""
                                sentences.clear()
                                prefs.edit().remove("lastBook").apply()
                            }
                            save()
                            show(1)
                        }.show()
                }, false)
            }
        }
    }
    private fun reader() {
        title("Read and listen", "Highlighting follows the voice when the Android speech engine supports it.")
        if (active == null || sentences.isEmpty()) {
            box(body).apply {
                addView(text("Select a book from your library.", 15f, muted))
                action(this, "Import a book", { importBook() })
            }
            return
        }
        box(body).apply {
            addView(text(active?.title ?: "Book", 18f, navy, true))
            pageStatus = text("", 12f, muted).also { addView(it) }
            val controls = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            addView(controls)
            fun control(label: String, callback: () -> Unit): Button {
                val button = Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    textSize = 12f
                    setTextColor(navy)
                    background = bg(Color.rgb(235, 239, 245), 9)
                    setOnClickListener { callback() }
                }
                controls.addView(button, LinearLayout.LayoutParams(0, d(52), 1f).apply {
                    topMargin = d(10)
                    leftMargin = d(2)
                    rightMargin = d(2)
                })
                return button
            }
            control("Previous") { skip(-1) }
            playControl = control("Play") { if (speaking) stop() else read() }
            control("Next") { skip(1) }
            val rateButton = action(this, "Voice speed: " + rate.toString() + "×", {}, false)
            rateButton.setOnClickListener {
                rate = when (rate) { 0.75f -> 1.0f; 1.0f -> 1.25f; 1.25f -> 1.5f; else -> 0.75f }
                speech?.setSpeechRate(rate)
                rateButton.text = "Voice speed: " + rate.toString() + "×"
            }
            action(this, "Select another book", { stop(); show(1) }, false)
        }
        val textBox = box(body)
        textBox.addView(text("TEXT + VOICE", 11f, muted, true))
        val display = text("", 19f).apply {
            setPadding(0, d(13), 0, d(10))
            setTextIsSelectable(true)
        }
        pageText = display
        textBox.addView(display)
        updateText()
    }
    private fun skip(direction: Int) {
        if (sentences.isEmpty()) return
        stop()
        position = (position + direction).coerceIn(0, sentences.lastIndex)
        remember()
        updateText()
        read()
    }
    private fun updateText(range: Pair<Int, Int>? = null) {
        if (sentences.isEmpty()) return
        val sentence = sentences[position]
        val start = (sentence.first - 500).coerceAtLeast(0)
        val end = (sentence.second + 1000).coerceAtMost(bookText.length)
        val excerpt = SpannableString(bookText.substring(start, end))
        val selected = range ?: sentence
        val a = (selected.first - start).coerceIn(0, excerpt.length)
        val b = (selected.second - start).coerceIn(a, excerpt.length)
        if (a < b) excerpt.setSpan(BackgroundColorSpan(Color.rgb(255, 231, 163)), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        pageText?.text = excerpt
        pageStatus?.text = "Sentence " + (position + 1) + "/" + sentences.size + " • " + ((position + 1) * 100 / sentences.size) + "%"
        playControl?.text = if (speaking) "Pause" else "Play"
    }
    private fun read() {
        if (sentences.isEmpty()) return
        if (!ready) {
            alert("Voice unavailable", "Enable Android text-to-speech and install an English voice.")
            return
        }
        val passage = sentences[position]
        speaking = true
        utterance = "pb-" + System.nanoTime()
        remember()
        updateText()
        speech?.setSpeechRate(rate)
        speech?.speak(bookText.substring(passage.first, passage.second), TextToSpeech.QUEUE_FLUSH, null, utterance)
    }
    private fun stop() {
        speaking = false
        utterance = ""
        speech?.stop()
        updateText()
    }
    private fun remember() {
        active?.let { prefs.edit().putInt("index_" + it.id, position).putString("lastBook", it.id).apply() }
    }
    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS &&
            speech?.setLanguage(Locale.ENGLISH) != TextToSpeech.LANG_MISSING_DATA
        if (!ready) return
        speech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { runOnUiThread { if (id == utterance) stop() } }
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                runOnUiThread {
                    if (id == utterance && speaking && sentences.isNotEmpty()) {
                        val offset = sentences[position].first
                        updateText((offset + start) to (offset + end))
                    }
                }
            }
            override fun onDone(id: String?) {
                runOnUiThread {
                    if (id != utterance || !speaking) return@runOnUiThread
                    if (position < sentences.lastIndex) {
                        position += 1
                        read()
                    } else stop()
                }
            }
        })
    }
    private fun importBook() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "application/epub+zip", "text/plain", "application/octet-stream"))
        }
        startActivityForResult(intent, 330)
    }
    @Deprecated("Using Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 330 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            try {
                val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else "Book"
                } ?: "Book"
                val output = ByteArrayOutputStream()
                contentResolver.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(16384)
                    var total = 0
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 40000000) { "Book exceeds the 40 MB file limit." }
                        output.write(buffer, 0, n)
                    }
                } ?: error("Cannot open book.")
                val dataBytes = output.toByteArray()
                val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val extracted = when (extension) {
                    "txt" -> dataBytes.toString(Charsets.UTF_8)
                    "pdf" -> PDDocument.load(dataBytes).use { PDFTextStripper().getText(it) }
                    "epub" -> readEpub(dataBytes)
                    else -> error("Only PDF, EPUB and TXT files are supported.")
                }.trim()
                require(extracted.isNotBlank()) { "No text found. Scanned PDFs require OCR, which this version does not include." }
                require(extracted.length <= 4000000) { "Book text exceeds the limit for this version." }
                val id = "book_" + System.currentTimeMillis() + ".txt"
                File(filesDir, id).writeText(extracted)
                runOnUiThread {
                    val book = Book(id, name)
                    books.add(0, book)
                    save()
                    openBook(book)
                    show(2)
                }
            } catch (e: Exception) {
                runOnUiThread { alert("Import failed", e.message ?: "Unsupported file") }
            }
        }.start()
    }
    private fun readEpub(bytes: ByteArray): String {
        val chapters = mutableListOf<Pair<String, String>>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var count = 0
            while (true) {
                val item = zip.nextEntry ?: break
                count++
                require(count < 1500) { "EPUB contains too many files." }
                if (!item.isDirectory &&
                    (item.name.endsWith(".xhtml", true) || item.name.endsWith(".html", true) || item.name.endsWith(".htm", true))) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        require(output.size() + n < 2000000) { "EPUB chapter is too large." }
                        output.write(buffer, 0, n)
                    }
                    val plain = Html.fromHtml(output.toString("UTF-8"), Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    if (plain.isNotEmpty()) chapters.add(item.name to plain)
                }
                zip.closeEntry()
            }
        }
        require(chapters.isNotEmpty()) { "No EPUB chapters found." }
        return chapters.sortedBy { it.first }.joinToString("\n\n") { it.second }
    }
    private fun openBook(book: Book) {
        stop()
        try {
            val content = File(filesDir, book.id).readText()
            active = book
            bookText = content
            sentences.clear()
            val iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH)
            iterator.setText(content)
            var from = iterator.first()
            while (true) {
                val end = iterator.next()
                if (end == BreakIterator.DONE) break
                var start = from
                while (start < end) {
                    val next = (start + 1200).coerceAtMost(end)
                    if (content.substring(start, next).isNotBlank()) sentences.add(start to next)
                    start = next
                }
                from = end
            }
            position = prefs.getInt("index_" + book.id, 0).coerceIn(0, (sentences.size - 1).coerceAtLeast(0))
            prefs.edit().putString("lastBook", book.id).apply()
        } catch (e: Exception) { alert("Could not open book", e.message ?: "Book unavailable") }
    }
    private fun restore() {
        try {
            val list = JSONArray(prefs.getString("books", "[]"))
            for (i in 0 until list.length()) {
                val row = list.getJSONObject(i)
                val book = Book(row.getString("id"), row.getString("title"))
                if (File(filesDir, book.id).exists()) books.add(book)
            }
        } catch (_: Exception) { books.clear() }
        val last = prefs.getString("lastBook", null)
        if (last != null) books.firstOrNull { it.id == last }?.let { openBook(it) }
    }
    private fun save() {
        val list = JSONArray()
        books.forEach { list.put(JSONObject().put("id", it.id).put("title", it.title)) }
        prefs.edit().putString("books", list.toString()).apply()
    }
    private fun web(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { alert("No browser", "Install a browser to open this free book source.") }
    }
    private fun library() {
        title("Free worldwide book library", "Legal sources for business, communication, leadership and self-development.")
        val sources = listOf(
            Triple("The Art of Public Speaking", "Public speaking", "https://www.gutenberg.org/ebooks/16317"),
            Triple("Salesmanship classics", "Sales", "https://www.gutenberg.org/ebooks/search/?query=salesmanship"),
            Triple("As a Man Thinketh", "Mindset", "https://www.gutenberg.org/ebooks/search/?query=as+a+man+thinketh"),
            Triple("Acres of Diamonds", "Motivation", "https://www.gutenberg.org/ebooks/search/?query=acres+of+diamonds"),
            Triple("The Science of Getting Rich", "Historical self-development", "https://www.gutenberg.org/ebooks/search/?query=the+science+of+getting+rich"),
            Triple("OpenStax business textbooks", "Marketing and entrepreneurship", "https://openstax.org/subjects/business"),
            Triple("Standard Ebooks", "Free public-domain ebooks", "https://standardebooks.org/ebooks"),
            Triple("Project Gutenberg business search", "Business and leadership", "https://www.gutenberg.org/ebooks/search/?query=business"),
            Triple("Open Library: network marketing", "Library borrowing", "https://openlibrary.org/search?q=network+marketing"),
            Triple("Open Library: direct selling", "Library borrowing", "https://openlibrary.org/search?q=direct+selling")
        )
        sources.forEach { source ->
            box(body).apply {
                addView(text(source.second.uppercase(Locale.ROOT), 11f, muted, true))
                addView(text(source.first, 17f, navy, true))
                action(this, "Open legal book source", { web(source.third) }, false)
            }
        }
        body.addView(text("Some books require a library membership or are unavailable in some countries. Only books you legally obtain can be imported. Downloading a book requires internet; imported books remain offline.", 12f, muted))
    }
    private fun coach() {
        title("Business conversation coach", "Build confidence with truthful, respectful sales and recruitment practice.")
        val lessons = listOf(
            Triple("Begin with questions", "Understand what a prospect needs before explaining a business opportunity.", "What skills or business ideas interest you at the moment?"),
            Triple("Present without pressure", "Explain the business model, product value, costs, risks and conditions clearly.", "Would you like a short explanation of what the business involves and what it costs?"),
            Triple("Handle skepticism respectfully", "Provide verifiable evidence, official terms and return policies. Do not dismiss concerns.", "That's a fair question. We can review the facts together before you decide."),
            Triple("Avoid income guarantees", "Do not promise cars, luxury, or financial freedom. Share earnings information accurately.", "Results vary. I can explain the compensation plan and the costs without promising an outcome."),
            Triple("When someone is busy", "Respect their time and ask whether they want a short summary.", "Would you prefer a short summary, or should we leave it for now?"),
            Triple("When someone says no", "Respect the decision and avoid repeated unsolicited follow-ups.", "Thank you for being clear. I appreciate your time."),
            Triple("Support your team", "Teach product knowledge, customer service, responsible marketing and leadership.", "What would help you become more confident serving customers this week?")
        )
        lessons.forEachIndexed { index, lesson ->
            box(body).apply {
                addView(text("LESSON " + (index + 1), 11f, muted, true))
                addView(text(lesson.first, 17f, navy, true))
                addView(text(lesson.second, 14f, muted))
                action(this, "Practice a reply", { alert(lesson.first, lesson.third) }, false)
            }
        }
    }
    private fun alert(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show()
    }
    override fun onStop() { stop(); super.onStop() }
    override fun onDestroy() { speech?.stop(); speech?.shutdown(); super.onDestroy() }
}
