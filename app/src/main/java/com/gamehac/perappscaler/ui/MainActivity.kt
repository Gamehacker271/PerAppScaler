package com.gamehac.perappscaler.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.gamehac.perappscaler.R
import com.gamehac.perappscaler.data.AppItem
import com.gamehac.perappscaler.engine.GameInterventionEngine
import com.gamehac.perappscaler.utils.LocaleHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var chipGroupFilter: ChipGroup
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rvApps: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: AppAdapter

    private val allAppsList = mutableListOf<AppItem>()
    private var currentFilterType = FilterType.ALL
    private var currentSearchQuery = ""

    enum class FilterType {
        ALL, GAMES, CONFIGURED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupEdgeToEdge()
        setupListeners()
        loadInstalledApps()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        chipGroupFilter = findViewById(R.id.chipGroupFilter)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        rvApps = findViewById(R.id.rvApps)
        progressBar = findViewById(R.id.progressBar)
        tvEmpty = findViewById(R.id.tvEmpty)

        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { handleMenuItemClick(it) }

        adapter = AppAdapter { item ->
            val dialog = ScalingBottomSheetDialog(item) { updatedItem ->
                val index = allAppsList.indexOfFirst { it.packageName == updatedItem.packageName }
                if (index != -1) {
                    allAppsList[index] = updatedItem
                }
                applyFilterAndSearch()
            }
            dialog.show(supportFragmentManager, ScalingBottomSheetDialog.TAG)
        }

        rvApps.layoutManager = LinearLayoutManager(this)
        rvApps.adapter = adapter
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBarLayout)) { v, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(0, statusBar.top, 0, 0)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(rvApps) { v, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, navBar.bottom + 80)
            insets
        }
    }

    private fun setupListeners() {
        swipeRefresh.setOnRefreshLayoutListener {
            loadInstalledApps()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilterAndSearch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentFilterType = when (checkedIds.firstOrNull()) {
                R.id.chipGames -> FilterType.GAMES
                R.id.chipConfigured -> FilterType.CONFIGURED
                else -> FilterType.ALL
            }
            applyFilterAndSearch()
        }
    }

    private fun handleMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_language -> {
                LocaleHelper.showLanguageDialog(this) {
                    recreate()
                }
                true
            }
            R.id.action_reset_all -> {
                showResetAllConfirmationDialog()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> false
        }
    }

    private fun showResetAllConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_reset_all_title)
            .setMessage(R.string.dialog_reset_all_msg)
            .setPositiveButton(R.string.dialog_reset_all_confirm) { _, _ ->
                GameInterventionEngine.resetAll(this)
                for (app in allAppsList) {
                    app.scalePercent = 100
                    app.fps = 0
                }
                applyFilterAndSearch()
                Toast.makeText(this, R.string.toast_reset, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_about, null)
        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun loadInstalledApps() {
        progressBar.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        scope.launch {
            val loadedList = withContext(Dispatchers.IO) {
                val pm = packageManager
                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    pm.getInstalledApplications(PackageManager.GET_META_DATA)
                }

                val list = mutableListOf<AppItem>()
                for (info in packages) {
                    // Filter out this app itself and system core packages without launcher intent
                    if (info.packageName == packageName) continue

                    val launchIntent = pm.getLaunchIntentForPackage(info.packageName)
                    val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        info.category == ApplicationInfo.CATEGORY_GAME
                    } else {
                        (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                    }

                    // Include if it has a launch intent or is marked as a game
                    if (launchIntent != null || isGame) {
                        val label = pm.getApplicationLabel(info).toString()
                        val icon = try {
                            pm.getApplicationIcon(info)
                        } catch (e: Exception) {
                            null
                        }

                        val config = GameInterventionEngine.getConfig(this@MainActivity, info.packageName)
                        val scalePercent = (config.scaleFactor * 100).toInt()

                        list.add(
                            AppItem(
                                packageName = info.packageName,
                                label = label,
                                icon = icon,
                                isGame = isGame,
                                scalePercent = if (scalePercent in 30..100) scalePercent else 100,
                                fps = config.fpsOverride
                            )
                        )
                    }
                }

                // Sort: Configured first, then Games, then alphabetically by label
                list.sortWith(
                    compareByDescending<AppItem> { it.isScaled }
                        .thenByDescending { it.isGame }
                        .thenBy { it.label.lowercase() }
                )
                list
            }

            allAppsList.clear()
            allAppsList.addAll(loadedList)

            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            applyFilterAndSearch()
        }
    }

    private fun applyFilterAndSearch() {
        val filtered = allAppsList.filter { item ->
            val matchesFilter = when (currentFilterType) {
                FilterType.ALL -> true
                FilterType.GAMES -> item.isGame
                FilterType.CONFIGURED -> item.isScaled
            }

            val matchesSearch = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                item.label.contains(currentSearchQuery, ignoreCase = true) ||
                        item.packageName.contains(currentSearchQuery, ignoreCase = true)
            }

            matchesFilter && matchesSearch
        }

        adapter.submitList(filtered)
        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }
}
