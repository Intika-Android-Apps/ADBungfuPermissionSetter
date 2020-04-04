package com.oF2pks.adbungfu

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
import kotlinx.android.synthetic.main.search_view.*
import android.view.ViewAnimationUtils
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.ProgressDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ApplicationInfo
import android.os.Build
import android.text.InputType
import com.google.android.material.snackbar.Snackbar
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.oF2pks.adbungfu.AppListAdapter.SortMethod
import com.oF2pks.adbungfu.ExpandHmap.ExpandableListDetail
import kotlinx.coroutines.*
import java.io.*
import kotlin.coroutines.CoroutineContext

const val freezer = "1ive + stand-by"

class MainActivity : AppCompatActivity(), CoroutineScope {
    private var output: File? =null
    private var appopstype = freezer
    private var sigma = 0
    private var full = false

    private var mAlertDialog: ProgressDialog? =null
    private var mAlertText:String =""

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

    private lateinit var masterJob: Job //https://stackoverflow.com/questions/53125385/how-to-migrate-kotlin-from-1-2-to-kotlin-1-3-0-then-using-async-ui-and-bg-in-a
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + masterJob

    override fun onDestroy() {
        super.onDestroy()
        masterJob.cancel()
    }

    private val changeTextAlert = Runnable { mAlertDialog!!.setMessage(mAlertText) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = File(this.getExternalFilesDir(null), (Build.VERSION.RELEASE + "-X-" + Build.VERSION.INCREMENTAL + ".txt").replace(" ".toRegex(), ""))

        masterJob = Job()
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        val fab = findViewById<FloatingActionButton>(R.id.fab)!!
        fab.setOnClickListener { showActionsDialog() }

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
            val adapterSdkArray = ArrayAdapter(this, android.R.layout.simple_spinner_item, sdkArray())
            adapterSdkArray.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapterSdkArray

            spinner.setSelection(adapterSdkArray.getPosition(appopstype))
            spinner.onItemSelectedListener = object :
                    AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>,
                                            view: View?, position: Int, id: Long) {
                    Snackbar.make(coordinator,
                            parent.getItemAtPosition(position).toString(), Snackbar.LENGTH_LONG).show()
                    //Toast.makeText(this@MainActivity, "OnItemSelectedListener : " + parent.getItemAtPosition(position), Toast.LENGTH_SHORT).show()
                    appopstype = parent.getItemAtPosition(position).toString()
                    toolbar.subtitle = fully(full) + "sdk" + Build.VERSION.SDK_INT.toString() + ":" + appopstype
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
                //toolbar.subtitle = fully(full) + "sdk" + Build.VERSION.SDK_INT.toString() + ":" + appopstype
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

        mAlertDialog = ProgressDialog(this@MainActivity)
        mAlertDialog!!.setTitle(getString(R.string.loading_dialog_title))
        //mAlertDialog!!.setTopColorRes(R.color.accent)
        //.setTopTitleColor(getColor(android.R.color.white))
        mAlertDialog!!.setIcon(R.drawable.clock_alert)
        mAlertDialog!!.setMessage(appopstype)
        mAlertDialog!!.show()

        this.launch {
            if (boolean){
                val appsInfos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                mAlertText = appopstype + " " + appsInfos.size
                runOnUiThread (changeTextAlert)
                for (i in appsInfos.indices) {
                    if (i%20 == 0) {
                        mAlertDialog!!.setMessage(appopstype + " " + appsInfos.size + "/"+i)
                        mAlertDialog!!.show()
                        /*mAlertText = appopstype + " " + appsInfos.size + "/"+i
                        runOnUiThread (changeTextAlert)*/
                    }
                    val data = withContext(Dispatchers.IO) {
                        val ztest: String
                        var fuel = ""
                        if (appopstype == freezer) {
                            ztest = if (appsInfos[i].enabled) "allow"
                            else "deny"
                            if (Build.VERSION.SDK_INT >= 23) {
                                val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                                if (usm.isAppInactive(appsInfos[i].packageName)) fuel = ""
                                else {
                                    fuel ="Active"
                                    sigma++
                                }
                            }
                        } else {
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                                ztest = "no"
                                fuel = getString(R.string.message_blind)
                            } else {
                                ztest = checkAppOpsPermission(appsInfos[i].packageName , appopstype).get()
                                if (ztest.contains("time")) {
                                    fuel = ztest.substring(ztest.indexOf("time")+5)
                                    sigma++
                                }
                            }
                        }
                        AppItem(appsInfos[i].loadIcon(packageManager),
                                appsInfos[i].loadLabel(packageManager).toString(),
                                fuel,
                                appsInfos[i].packageName,
                                appsInfos[i].flags and  ApplicationInfo.FLAG_SYSTEM != 0,
                                testB(ztest))
                    }

                    adapter.addItem(data)

                    if (adapter.itemCount == appsInfos.size) {
                        adapter.sort()
                        mAlertDialog!!.dismiss()

                    }

                }
            } else {
                val intent = Intent(Intent.ACTION_MAIN, null)
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                val apps = packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                mAlertText = appopstype + " " + apps.size
                runOnUiThread (changeTextAlert)
                apps.map {
                    val data = withContext(Dispatchers.IO) {
                        val ztest: String
                        var fuel = ""
                        if (appopstype == freezer) {
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
                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
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
                        mAlertDialog!!.dismiss()
                    }
                }
            }
            toolbar.subtitle = fully(full) + " | \u2211 = " + sigma.toString()
            sigma = 0
        }
    }

    private fun testB(string: String): Boolean {
        return string.contains("allow") || string.contains("default")  ||
                !(string.contains("ignore") || string.contains("deny"))
    }

    fun fully(boolean: Boolean): String {
        return if (boolean) "☢" else ""
    }

    private fun showSearchBar() {
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

    private fun sdkArray(): List<String?> {
        val sdkarray = arrayListOf<String>()
        sdkarray.add(freezer)

        val list = resources.getStringArray(R.array.permary)
        for (n in list.indices) {
            if (list[n].substring(0,2).toInt() <= Build.VERSION.SDK_INT) {
                sdkarray.add(list[n].toString().substring(5))
            }
        }

        return sdkarray.sorted()
    }

    private fun refresh() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP && appopstype != freezer) {
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
                getString(R.string.action_wipe_all),
                "perform idle maintenance now",
                "pm list libraries",
                "pm list permission-groups",
                "am stack list",
                getString(R.string.s0),
                getString(R.string.action_adb)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) actions.add(getString(R.string.action_reset_all))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            actions.add(getString(R.string.action_deviceidle))
            actions.add("profman ALL")
        }

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
                        Snackbar.make(coordinator, suString("am idle-maintenance").get(), Snackbar.LENGTH_LONG).show()
                    }).start()
                }
                3 -> {
                    val showText = TextView(this)
                    showText.text = suList("pm list libraries").joinToString("\n").replace("library:","")
                    showText.setTextIsSelectable(true)
                    val builder = AlertDialog.Builder(this)
                    builder.setView(showText)
                            .setTitle(R.string.button_open_information)
                            .setView(showText)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
                4 -> {
                    val showText = TextView(this)
                    showText.text = suList("pm list permission-groups").joinToString("\n").replace("permission group:android.permission-group","")
                    showText.setTextIsSelectable(true)
                    android.app.AlertDialog.Builder(this)
                            .setTitle(R.string.button_open_information)
                            .setView(showText)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
                5 -> {
                    val showText = TextView(this)
                    showText.text = suList("am stack list").joinToString("\n")
                    showText.setTextIsSelectable(true)
                    val builder = AlertDialog.Builder(this , R.style.AppTheme)
                    builder.setView(showText)
                            .setTitle(R.string.button_open_information)
                            .setView(showText)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
                6 -> {
                    val builder = AlertDialog.Builder(this , R.style.AppTheme)
                    val cmdText = EditText(this)
                    cmdText.hint = "be aware, NO SAFEGUARDS!"
                    cmdText.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

                    builder.setView(cmdText)
                            .setTitle("zZz CAUTION ->" + getString(R.string.s0))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton("eXe") {_ , _ ->
                                val showText = TextView(this)
                                showText.text = suList(cmdText.text.toString()).joinToString("\n")
                                showText.setTextIsSelectable(true)
                                builder.setView(showText)
                                        .setTitle(R.string.button_open_information)
                                        .setNegativeButton(android.R.string.ok, null)
                                        .setPositiveButton(null,null)
                                        .show()
                            }
                            .show()

                }
                7 -> {
                    val showText = TextView(this)
                    var zz: HashMap<String, List<String>>? = null
                    var bHelper = true
                    val cmdL = arrayListOf(" input   ", "toybox --help ","toybox   ","sqlite3 --help"," dpm   "," pm   "," am   "," zip -h","ls /system/bin","logcat --help")//"ls /system/bin","dexdump -h","/system/bin/sh -dexdump",
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        suList("cmd -l").map {
                            cmdL.add("cmd $it ")
                        }
                    } else cmdL.addAll(arrayListOf(
                            " activity ",
                            " appops ",
                            " battery ",
                            " deviceidle ",
                            //" input ",
                            " jobscheduler ",
                            " netpolicy ",
                            " package ",
                            " shortcut ",
                            " statusbar ",
                            " user ",
                            " webviewupdate "))
                    Thread(Runnable{
                        zz = suADB(cmdL, output!!)
                        if (bHelper) startActivity(Intent(this, ExpandableListDetail::class.java)
                                .putExtra("HMAP",zz))
                    }).start()
                    showText.text = cmdL.joinToString("\n")
                    showText.setTextIsSelectable(true)
                    val builder = AlertDialog.Builder(this)
                    builder.setView(showText)
                            .setTitle(R.string.button_open_information)
                            .setView(showText)
                            .setCancelable(true)
                            .setNegativeButton(android.R.string.ok) {_ , _ ->
                                bHelper = false
                            }
                            .setPositiveButton("2²°") {_ , _ ->
                                bHelper = true
                                if (zz != null) startActivity(Intent(this, ExpandableListDetail::class.java)
                                        .putExtra("HMAP",zz))
                            }
                            .show()
                }
                8 -> {
                    Thread(Runnable{
                        Snackbar.make(coordinator, suString("appops reset").get(), Snackbar.LENGTH_LONG).show()
                    }).start()
                    if (appopstype != freezer) {
                        adapter.clear()
                        loadApps(full)
                    }
                }
                9 -> {
                    val showText = TextView(this)
                    showText.text = suList("cmd deviceidle whitelist").joinToString("\n").replace("system","")
                    showText.setTextIsSelectable(true)
                    val builder = AlertDialog.Builder(this , R.style.AppTheme)
                    builder.setView(showText)
                            .setTitle(R.string.button_open_information)
                            .setView(showText)
                            .setNegativeButton(android.R.string.ok, null)
                            .show()
                }
                10 -> {
                    mAlertDialog = ProgressDialog(this@MainActivity)
                    mAlertDialog!!.setTitle(getString(R.string.loading_dialog_title))
                    mAlertDialog!!.setIcon(R.drawable.clock_alert)
                    mAlertDialog!!.setMessage("PROFMAN")
                    mAlertDialog!!.show()

                    Thread(Runnable{
                        Snackbar.make(coordinator, "PROFMAN:" +suProfman(this, mAlertDialog!!), Snackbar.LENGTH_LONG).show()
                        mAlertDialog!!.dismiss()
                    }).start()
                }
            }
        }.show()
                .getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(resources.getColor(R.color.primary))
    }

    /*fun ls():String {
        //@SuppressLint("PrivateApi")
        val execClass = Class.forName("android.os.Exec")
        val createSubprocess = execClass.getMethod("createSubprocess", String::class.java, String::class.java, String::class.java, IntArray::class.java)
        val pid = IntArray(1)
        val fd = createSubprocess.invoke(null, "/system/bin/ls", "/", null, pid) as FileDescriptor
        return FileInputStream(fd).bufferedReader().use(BufferedReader::readText)
    }*/
}
