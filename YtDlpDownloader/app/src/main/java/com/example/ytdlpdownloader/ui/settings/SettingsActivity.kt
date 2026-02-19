package com.example.ytdlpdownloader.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ytdlpdownloader.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupSettings()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(com.example.ytdlpdownloader.R.string.settings_title)
    }

    private fun setupSettings() {
        // Default Format
        binding.cardFormat.setOnClickListener {
            showFormatSelectionDialog()
        }

        // Default Quality
        binding.cardQuality.setOnClickListener {
            showQualitySelectionDialog()
        }

        // Download Location
        binding.cardDownloadLocation.setOnClickListener {
            openDownloadLocationPicker()
        }

        // Concurrent Downloads
        binding.cardConcurrentDownloads.setOnClickListener {
            showConcurrentDownloadsDialog()
        }

        // Wi-Fi Only
        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            // Save preference
        }

        // Legal Disclaimer
        binding.cardLegalDisclaimer.setOnClickListener {
            startActivity(Intent(this, LegalDisclaimerActivity::class.java))
        }

        // Version
        binding.textVersion.text = getAppVersion()
    }

    private fun showFormatSelectionDialog() {
        val formats = arrayOf("MP4 (Video)", "MP3 (Audio)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Default Format")
            .setItems(formats) { dialog, which ->
                // Save selection
                dialog.dismiss()
            }
            .show()
    }

    private fun showQualitySelectionDialog() {
        val qualities = arrayOf("Best", "High (1080p)", "Medium (720p)", "Low (480p)")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Default Quality")
            .setItems(qualities) { dialog, which ->
                // Save selection
                dialog.dismiss()
            }
            .show()
    }

    private fun openDownloadLocationPicker() {
        // Open Storage Access Framework
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
    }

    private fun showConcurrentDownloadsDialog() {
        val options = arrayOf("1", "2", "3", "4", "5")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Concurrent Downloads")
            .setItems(options) { dialog, which ->
                // Save selection
                dialog.dismiss()
            }
            .show()
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001
    }
}
