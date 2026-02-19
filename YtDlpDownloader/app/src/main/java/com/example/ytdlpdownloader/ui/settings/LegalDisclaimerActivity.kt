package com.example.ytdlpdownloader.ui.settings

import android.os.Bundle
import android.text.Html
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ytdlpdownloader.R
import com.example.ytdlpdownloader.databinding.ActivityLegalDisclaimerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LegalDisclaimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalDisclaimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalDisclaimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDisclaimer()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.legal_disclaimer_title)
    }

    private fun setupDisclaimer() {
        // Set the disclaimer text
        val disclaimerText = getString(R.string.legal_disclaimer_content)
        binding.textDisclaimer.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            Html.fromHtml(disclaimerText, Html.FROM_HTML_MODE_COMPACT)
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(disclaimerText)
        }

        // Accept button
        binding.buttonAccept.setOnClickListener {
            // Save acceptance
            saveDisclaimerAccepted(true)
            finish()
        }

        // Decline button
        binding.buttonDecline.setOnClickListener {
            showDeclineDialog()
        }
    }

    private fun saveDisclaimerAccepted(accepted: Boolean) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("disclaimer_accepted", accepted).apply()
    }

    private fun showDeclineDialog() {
        AlertDialog.Builder(this)
            .setTitle("Important")
            .setMessage("You must accept the legal disclaimer to use this application. If you decline, the app will close.")
            .setPositiveButton("Accept") { _, _ ->
                saveDisclaimerAccepted(true)
                finish()
            }
            .setNegativeButton("Exit App") { _, _ ->
                saveDisclaimerAccepted(false)
                finishAffinity()
            }
            .setCancelable(false)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
