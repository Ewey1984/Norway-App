package com.norwayshop.converter

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("shop", MODE_PRIVATE) }
    private var rate = 0.0795658 // fallback indicative rate, explicitly dated
    private var rateDate = "21 Oct 2026 (starter rate)"
    private lateinit var status: TextView
    private lateinit var nameInput: EditText
    private lateinit var priceInput: EditText
    private lateinit var result: TextView
    private lateinit var basket: TextView
    private var totalNok = 0.0
    private val translator by lazy {
        Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.NORWEGIAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH).build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rate = prefs.getFloat("rate", rate.toFloat()).toDouble()
        rateDate = prefs.getString("rateDate", rateDate) ?: rateDate
        totalNok = java.lang.Double.longBitsToDouble(prefs.getLong("basket", java.lang.Double.doubleToRawLongBits(0.0)))
        val scroller = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 34, 30, 40) }
        scroller.addView(layout)
        setContentView(scroller)
        fun heading(s: String) = TextView(this).apply { text=s; textSize=23f; setPadding(0, 8, 0, 14); layout.addView(this) }
        fun button(s: String, action: () -> Unit) { layout.addView(Button(this).apply { text=s; setOnClickListener { action() } }) }
        heading("🇳🇴 Norway Shop Converter 🇬🇧")
        status = TextView(this).apply { textSize=15f; layout.addView(this) }
        button("Update exchange rate now") { updateRate() }
        heading("Scan a shelf label")
        button("📷 Open camera") {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 10)
        }
        layout.addView(TextView(this).apply { text="Check and correct the detected product and price before adding it." })
        nameInput = EditText(this).apply { hint="Product name (English)"; setSingleLine(); layout.addView(this) }
        priceInput = EditText(this).apply { hint="Price in NOK, e.g. 24,90"; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; layout.addView(this) }
        result = TextView(this).apply { textSize=25f; setPadding(0,12,0,12); layout.addView(this) }
        button("Convert price") { convert() }
        button("Add to basket") {
            val n = parsePrice(priceInput.text.toString())
            if (n == null) { toast("Enter a valid Norwegian price") } else {
                totalNok += n; saveBasket(); showBasket(); toast("Added ${nameInput.text}")
            }
        }
        heading("Shopping basket")
        basket = TextView(this).apply { textSize=22f; layout.addView(this) }
        button("Clear basket") { totalNok=0.0; saveBasket(); showBasket() }
        layout.addView(TextView(this).apply { text="Offline: saved exchange rate and basket remain available. Camera OCR works offline after installation. Norwegian-to-English ML Kit language model requires a one-time download. Scanned results and currency rates should be checked before purchase."; setPadding(0,18,0,0) })
        showStatus(); showBasket(); updateRate(); prepareTranslation()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun showStatus() { status.text="1 NOK = £${"%.5f".format(Locale.UK,rate)} • Rate: $rateDate" }
    private fun showBasket() { basket.text="${"%.2f".format(Locale.UK,totalNok)} kr  =  £${"%.2f".format(Locale.UK,totalNok*rate)}" }
    private fun parsePrice(s: String): Double? = s.trim().replace(" ", "").replace(",", ".").toDoubleOrNull()?.takeIf { it >= 0 }
    private fun convert() { val n=parsePrice(priceInput.text.toString()); result.text=if(n==null) "Enter a price" else "${nameInput.text}\n${"%.2f".format(Locale.UK,n)} kr\n£${"%.2f".format(Locale.UK,n*rate)}" }
    private fun saveBasket() { prefs.edit().putLong("basket",java.lang.Double.doubleToRawLongBits(totalNok)).apply() }
    private fun updateRate() {
        thread {
            try {
                val conn=URL("https://open.er-api.com/v6/latest/NOK").openConnection().apply { connectTimeout=9000; readTimeout=9000 }
                val data=conn.getInputStream().bufferedReader().use { it.readText() }
                val json=JSONObject(data)
                if (json.optString("result") != "success") error("Rate service unavailable")
                val next=json.getJSONObject("rates").getDouble("GBP")
                if (next <= 0) error("Invalid rate")
                val updated=json.optString("time_last_update_utc", SimpleDateFormat("dd MMM yyyy",Locale.UK).format(Date()))
                runOnUiThread {
                    rate=next; rateDate=updated
                    prefs.edit().putFloat("rate",next.toFloat()).putString("rateDate",updated).apply()
                    showStatus(); showBasket(); convert()
                }
            } catch (_: Exception) { runOnUiThread { showStatus(); toast("Offline: using saved exchange rate") } }
        }
    }
    private fun prepareTranslation() {
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { toast("Norwegian translation ready for offline use") }
            .addOnFailureListener { toast("Translation model unavailable: connect to Wi-Fi to download") }
    }
    @Deprecated("Legacy camera thumbnail result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        @Suppress("DEPRECATION")
        val bitmap = data?.extras?.get("data") as? Bitmap
        if (bitmap == null) { toast("Camera returned no image"); return }
        status.text="Reading label…"
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap,0))
            .addOnSuccessListener { output ->
                val lines=output.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.isNotBlank() }
                val priceRegex=Regex("(?<!\\d)(\\d{1,5}[,.]\\d{2})(?!\\d)")
                val found=lines.mapNotNull { priceRegex.find(it)?.groupValues?.get(1) }.firstOrNull()
                if (found != null) priceInput.setText(found)
                val rawName=lines.firstOrNull { line -> !priceRegex.containsMatchIn(line) && line.any(Char::isLetter) } ?: lines.firstOrNull() ?: ""
                nameInput.setText(rawName)
                translator.translate(rawName)
                    .addOnSuccessListener { english -> nameInput.setText(english); convert(); showStatus() }
                    .addOnFailureListener { convert(); showStatus(); toast("Translation unavailable: edit product name manually") }
                if (lines.isEmpty()) toast("No text found; try a closer photo")
            }
            .addOnFailureListener { showStatus(); toast("Unable to read image") }
    }
    override fun onDestroy() { translator.close(); super.onDestroy() }
}
