package com.example.ytdlpdownloader.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ytdlpdownloader.R
import com.example.ytdlpdownloader.databinding.ActivityMainBinding
import com.example.ytdlpdownloader.domain.model.DownloadFormat
import com.example.ytdlpdownloader.domain.model.DownloadItem
import com.example.ytdlpdownloader.domain.model.DownloadStatus
import com.example.ytdlpdownloader.domain.model.Quality
import com.example.ytdlpdownloader.service.DownloadService
import com.example.ytdlpdownloader.ui.settings.SettingsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var downloadAdapter: DownloadAdapter
    
    private var downloadService: DownloadService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.LocalBinder
            downloadService = binder.getService()
            serviceBound = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
        }
    }
    
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            showNotificationPermissionRationale()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupInputSection()
        setupObservers()
        checkNotificationPermission()
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                    sharedUrl?.let { url ->
                        // Extract URL from text (it might be embedded in other text)
                        val urlRegex = "(https?://[^\\s]+)".toRegex()
                        val match = urlRegex.find(url)
                        match?.value?.let { extractedUrl ->
                            viewModel.handleSharedUrl(extractedUrl)
                        }
                    }
                }
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        bindService()
    }
    
    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
    
    private fun bindService() {
        Intent(this, DownloadService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    private fun setupRecyclerView() {
        downloadAdapter = DownloadAdapter(
            onPauseClick = { viewModel.pauseDownload(it.id) },
            onResumeClick = { viewModel.resumeDownload(it.id) },
            onCancelClick = { viewModel.cancelDownload(it.id) },
            onRetryClick = { viewModel.retryDownload(it.id) },
            onRemoveClick = { viewModel.removeDownload(it.id) },
            onOpenClick = { openDownloadedFile(it) }
        )
        
        binding.recyclerViewDownloads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = downloadAdapter
            setHasFixedSize(true)
        }
    }
    
    private fun setupInputSection() {
        // Format selector
        binding.chipGroupFormat.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipMp4 -> viewModel.onFormatSelected(DownloadFormat.MP4)
                R.id.chipMp3 -> viewModel.onFormatSelected(DownloadFormat.MP3)
            }
        }
        
        // Quality selector
        binding.chipGroupQuality.setOnCheckedStateChangeListener { _, checkedIds ->
            val quality = when (checkedIds.firstOrNull()) {
                R.id.chipBest -> Quality.BEST
                R.id.chipHigh -> Quality.HIGH
                R.id.chipMedium -> Quality.MEDIUM
                R.id.chipLow -> Quality.LOW
                else -> Quality.BEST
            }
            viewModel.onQualitySelected(quality)
        }
        
        // Download button
        binding.buttonDownload.setOnClickListener {
            val url = binding.editTextUrl.text.toString().trim()
            viewModel.onUrlChanged(url)
            viewModel.startDownload()
        }
        
        // Analyze button
        binding.buttonAnalyze.setOnClickListener {
            val url = binding.editTextUrl.text.toString().trim()
            viewModel.onUrlChanged(url)
            viewModel.analyzeUrl()
        }
    }
    
    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe UI state
                launch {
                    viewModel.uiState.collect { state ->
                        updateUi(state)
                    }
                }
                
                // Observe downloads
                launch {
                    viewModel.allDownloads.collectLatest { downloads ->
                        downloadAdapter.submitList(downloads)
                        updateEmptyState(downloads.isEmpty())
                    }
                }
                
                // Observe events
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }
    
    private fun updateUi(state: MainUiState) {
        // URL input
        binding.editTextUrl.setText(state.url)
        binding.textInputLayoutUrl.error = state.urlError
        
        // Loading states
        binding.progressBar.visibility = if (state.isAnalyzing) View.VISIBLE else View.GONE
        binding.buttonDownload.isEnabled = !state.isLoading && state.url.isNotBlank()
        binding.buttonAnalyze.isEnabled = !state.isAnalyzing && state.url.isNotBlank()
        
        // Format selection
        when (state.selectedFormat) {
            DownloadFormat.MP4 -> binding.chipMp4.isChecked = true
            DownloadFormat.MP3 -> binding.chipMp3.isChecked = true
        }
        
        // Quality selection
        when (state.selectedQuality) {
            Quality.BEST -> binding.chipBest.isChecked = true
            Quality.HIGH -> binding.chipHigh.isChecked = true
            Quality.MEDIUM -> binding.chipMedium.isChecked = true
            Quality.LOW -> binding.chipLow.isChecked = true
        }
        
        // Show format dialog if needed
        if (state.showFormatDialog && state.videoInfo != null) {
            showFormatSelectionDialog(state.videoInfo)
            viewModel.dismissFormatDialog()
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewDownloads.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun handleEvent(event: MainEvent) {
        when (event) {
            is MainEvent.ShowSnackbar -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
            }
            is MainEvent.ShowError -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG)
                    .setBackgroundTint(ContextCompat.getColor(this, R.color.error))
                    .show()
            }
            is MainEvent.NavigateToSettings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }
    
    private fun showFormatSelectionDialog(videoInfo: com.example.ytdlpdownloader.domain.model.VideoInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_format_selection, null)
        
        MaterialAlertDialogBuilder(this)
            .setTitle(videoInfo.title)
            .setView(dialogView)
            .setPositiveButton(R.string.download) { _, _ ->
                viewModel.startDownload()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun openDownloadedFile(downloadItem: DownloadItem) {
        downloadItem.localPath?.let { path ->
            try {
                val file = java.io.File(path)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(file.extension))
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.error_opening_file, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            else -> "*/*"
        }
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    showNotificationPermissionRationale()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    private fun showNotificationPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_clear_completed -> {
                viewModel.clearCompletedDownloads()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
