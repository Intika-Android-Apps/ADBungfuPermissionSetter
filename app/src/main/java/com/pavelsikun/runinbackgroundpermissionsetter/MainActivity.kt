package com.pavelsikun.runinbackgroundpermissionsetter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager

import android.content.pm.PackageManager
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import com.yarolegovich.lovelydialog.LovelyProgressDialog
import kotlinx.android.synthetic.main.search_view.*
import android.view.ViewAnimationUtils
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ApplicationInfo
import android.os.Build
import com.google.android.material.snackbar.Snackbar
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pavelsikun.runinbackgroundpermissionsetter.AppListAdapter.SortMethod
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

const val freezer = "1ive + stand-by"

class MainActivity : AppCompatActivity(), CoroutineScope {
    val B_SDK = Build.VERSION.SDK_INT
    var appopstype = freezer
    var sigma = 0
    var full = false

    val adapter by lazy {
        AppListAdapter { (_, appName, _, appPackage, _, isEnabled) ->
            setAppOpsPermission(appPackage, appopstype, isEnabled) { isSuccess ->
                val status = if (isEnabled) getString(R.string.message_allow) else getString(R.string.message_ignore)
                val msgSuccess = "$appName $appopstype ${getString(R.string.message_was_set_to)} '$status'"
                val msgError = "${getString(R.string.message_there_was_error)} $appName $appopstype ${getString(R.string.message_to)} '$status'"

                runOnUiThread {
                    val msg = if (isSuccess) msgSuccess else msgError
                    Snackbar.make(coordinator, msg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    lateinit var masterJob: Job //https://stackoverflow.com/questions/53125385/how-to-migrate-kotlin-from-1-2-to-kotlin-1-3-0-then-using-async-ui-and-bg-in-a
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + masterJob

    override fun onDestroy() {
        super.onDestroy()
        masterJob.cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        masterJob = Job()
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val fab = findViewById<FloatingActionButton>(R.id.fab)!!
        fab.setOnClickListener(View.OnClickListener { showActionsDialog() })

        val spinner = findViewById<Spinner>(R.id.spinner)
        toolbar.title = "_"

        if (intent != null) {
            if (intent.getStringExtra("extraID") != null)
                appopstype = intent.getStringExtra("extraID")
            else if (intent.action == Intent.ACTION_VIEW)
                full =true

        }

        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener {refresh()}

        if (spinner != null) {
            val adapterq = ArrayAdapter(this, android.R.layout.simple_spinner_item, sdkArray())
            adapterq.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapterq

            spinner.setSelection(adapterq.getPosition(appopstype))
            spinner.onItemSelectedListener = object :
                    AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>,
                                            view: View?, position: Int, id: Long) {
                    Snackbar.make(coordinator,
                            parent.getItemAtPosition(position).toString(), Snackbar.LENGTH_LONG).show()
                    //Toast.makeText(this@MainActivity, "OnItemSelectedListener : " + parent.getItemAtPosition(position), Toast.LENGTH_SHORT).show()
                    appopstype = parent.getItemAtPosition(position).toString()
                    toolbar.subtitle = fully(full) + "sdk" + B_SDK.toString() + ":" + appopstype
                    adapter.clear()
                    loadApps(full)
                }

                override fun onNothingSelected(parent: AdapterView<*>) {
                    // write code to perform some action
                }
            }
        }
        //loadApps(full)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> showSearchBar()
            R.id.action_sort_name -> adapter.sort(SortMethod.NAME)
            R.id.action_sort_package -> adapter.sort(SortMethod.PACKAGE)
            R.id.action_sort_disabled_first -> adapter.sort(SortMethod.STATE)
            R.id.action_system -> {
                //toolbar.subtitle = fully(full) + "sdk" + B_SDK.toString() + ":" + appopstype
                full = !full
                adapter.clear()
                loadApps(full)
            }
            R.id.action_info -> android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.button_open_information)
                    .setView(layoutInflater.inflate(R.layout.about_dialog_message, null))
                    .setNegativeButton(android.R.string.ok, null)
                    .show()

        }
        return super.onOptionsItemSelected(item)
    }

    fun CoroutineScope.loadApps(boolean: Boolean) {
        swipeRefreshLayout.isRefreshing = false
        val ad = LovelyProgressDialog(this@MainActivity)
                .setTopColorRes(R.color.accent)
                .setTopTitle(getString(R.string.loading_dialog_title))
                //.setTopTitleColor(getColor(android.R.color.white))
                .setIcon(R.drawable.clock_alert)
                .setMessage(appopstype).show()

        this.launch {
            if (boolean){
                val appsInfos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                appsInfos.map {
                    val data = withContext(Dispatchers.IO) {
                        val ztest: String
                        var fuel = ""
                        if (appopstype.equals(freezer)) {
                            if (it.enabled) ztest = "allow"
                            else ztest ="deny"
                            if (Build.VERSION.SDK_INT >= 23) {
                                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                                if (usm.isAppInactive(it.packageName)) fuel = ""
                                else {
                                    fuel ="Active"
                                    sigma++
                                }
                            }
                        } else {
                            if (B_SDK == Build.VERSION_CODES.LOLLIPOP) {
                                ztest = "no"
                                fuel = getString(R.string.message_blind)
                            } else {
                                ztest = checkAppOpsPermission(it.packageName , appopstype).get()
                                if (ztest.contains("time")) {
                                    fuel = ztest.substring(ztest.indexOf("time")+5)
                                    sigma++
                                }
                            }
                        }
                        AppItem(it.loadIcon(packageManager),
                                it.loadLabel(packageManager).toString(),
                                fuel,
                                it.packageName,
                                it.flags and  ApplicationInfo.FLAG_SYSTEM != 0,
                                testB(ztest))
                    }

                    adapter.addItem(data)

                    if (adapter.itemCount == appsInfos.size) {
                        adapter.sort()
                        ad.dismiss()
                    }

                }
            } else {
                val intent = Intent(Intent.ACTION_MAIN, null)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)

                apps.map {
                    val data = withContext(Dispatchers.IO) {
                        val ztest: String
                        var fuel = ""
                        if (appopstype.equals(freezer)) {
                            ztest = "allow"
                            if (Build.VERSION.SDK_INT >= 23) {
                                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                                if (usm.isAppInactive(it.activityInfo.packageName)) fuel = ""
                                else {
                                    fuel ="Active"
                                    sigma++
                                }
                            }
                        } else {
                            if (B_SDK == Build.VERSION_CODES.LOLLIPOP) {
                                ztest = "no"
                                fuel = getString(R.string.message_blind)
                            } else {
                                ztest = checkAppOpsPermission(it.activityInfo.packageName , appopstype).get()
                                if (ztest.contains("time")) {
                                    fuel = ztest.substring(ztest.indexOf("time")+5)
                                    sigma++
                                }
                            }
                        }
                        AppItem(it.loadIcon(packageManager),
                                it.loadLabel(packageManager).toString(),
                                fuel,
                                it.activityInfo.packageName,
                                it.activityInfo.applicationInfo.flags and  ApplicationInfo.FLAG_SYSTEM != 0,
                                testB(ztest))
                    }

                    adapter.addItem(data)

                    if (adapter.itemCount == apps.size) {
                        adapter.sort()
                        ad.dismiss()
                    }
                }
            }
            toolbar.subtitle = fully(full) + " | \u2211 = " + sigma.toString()
            sigma = 0
        }
    }

    fun testB(string: String): Boolean {
        return string.contains("allow") || string.contains("default")  ||
                !(string.contains("ignore") || string.contains("deny"))
    }

    fun fully(boolean: Boolean): String {
        if (boolean) return "☢"
        else return ""
    }

    fun showSearchBar() {
        val viewWidth = searchOverlay.measuredWidth.toFloat()
        val x = (searchOverlay.measuredWidth * 0.95).toInt()
        val y = searchOverlay.measuredHeight / 2

        val enterAnim = ViewAnimationUtils.createCircularReveal(searchOverlay, x, y, 0f, viewWidth)
        val exitAnim = ViewAnimationUtils.createCircularReveal(searchOverlay, x, y, viewWidth, 0f)

        val inputManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager

        enterAnim.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                searchBox.requestFocus()
                inputManager.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
            }
        })

        exitAnim.addListener(object: AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                searchOverlay.visibility = View.INVISIBLE
                fab.visibility = View.VISIBLE
            }
        })

        buttonClear.setOnClickListener {
            searchBox.text.clear()
        }

        buttonBack.setOnClickListener {
            val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(View(this).windowToken, 0)
            exitAnim.start()
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) { /*IGNORE*/ }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { /*IGNORE*/ }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                adapter.filter(searchBox.text.toString().toLowerCase())
            }
        })

        searchOverlay.visibility = View.VISIBLE
        fab.visibility = View.INVISIBLE
        enterAnim.start()
    }

    fun sdkArray(): List<String?> {
        val sdkarray = arrayListOf<String>()
        sdkarray.add(freezer)

        val list = resources.getStringArray(R.array.permary)
        for (n in list.indices) {
            if (list[n].substring(0,2).toInt() <= B_SDK) {
                sdkarray.add(list[n].toString().substring(5))
            }
        }

        return sdkarray.sorted()
    }

    private fun refresh() {
        if (B_SDK == Build.VERSION_CODES.LOLLIPOP && !appopstype.equals(freezer)) {
            Snackbar.make(coordinator, "No Lollipop/20,,, nu Refresh ! " + getString(R.string.message_blind), Snackbar.LENGTH_LONG).show()
            swipeRefreshLayout.isRefreshing = false
        } else {
            adapter.clear()
            loadApps(full)
        }
    }


    private fun showActionsDialog() {
        val actions = mutableListOf(
                getString(R.string.action_refresh),
                getString(R.string.action_wipe_all)
                )
        if (B_SDK >= Build.VERSION_CODES.LOLLIPOP_MR1) actions.add(getString(R.string.action_reset_all))

        AlertDialog.Builder(this)
                .setTitle("Select action")
                .setCancelable(true)
                .setNegativeButton(getString(R.string.button_close_dialog)) { dialog, _ -> dialog.cancel()}
                .setItems(actions.toTypedArray()) { _, which ->
            when (which) {
                0 -> {
                    refresh()
                }
                1 -> {
                    Thread(Runnable{
                        Snackbar.make(coordinator, suBool("pm trim-caches 999999G").get().toString(), Snackbar.LENGTH_LONG).show()
                    }).start()
                }
                2 -> {
                    Thread(Runnable{
                        Snackbar.make(coordinator, suString("appops reset").get(), Snackbar.LENGTH_LONG).show()

                    }).start()
                    if (!appopstype.equals(freezer)) {
                        adapter.clear()
                        loadApps(full)
                    }

                }
            }
        }.show()
                .getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.primary))
    }
}
