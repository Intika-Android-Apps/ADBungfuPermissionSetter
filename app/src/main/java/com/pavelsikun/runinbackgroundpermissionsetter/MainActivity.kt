package com.pavelsikun.runinbackgroundpermissionsetter

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager

import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.coroutines.experimental.bg
import com.yarolegovich.lovelydialog.LovelyStandardDialog
import com.yarolegovich.lovelydialog.LovelyProgressDialog
import kotlinx.android.synthetic.main.search_view.*
import android.view.ViewAnimationUtils
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.os.Build
import android.support.design.widget.Snackbar
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import com.pavelsikun.runinbackgroundpermissionsetter.AppListAdapter.SortMethod
class MainActivity : AppCompatActivity() {

    val B_SDK = Build.VERSION.SDK_INT
    var appopstype = "WAKE_LOCK"
    var sigma = 0
    var full = false

    val adapter by lazy {
        AppListAdapter { (_, appName, appTime, appPackage, isEnabled) ->
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        val spinner = findViewById<Spinner>(R.id.spinner)
        toolbar.title = "_"
        if (intent != null) {
            if (intent.getStringExtra("extraID") != null)
                appopstype = intent.getStringExtra("extraID")
            else if (intent.action == Intent.ACTION_VIEW)
                full =true

        }


        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener {
            adapter.clear()
            loadApps(full)
        }

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
                    Toast.makeText(this@MainActivity, "OnItemSelectedListener : " + parent.getItemAtPosition(position), Toast.LENGTH_SHORT).show()
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
            R.id.action_info -> showInfoDialog()
            R.id.action_sort_name -> adapter.sort(SortMethod.NAME)
            R.id.action_sort_package -> adapter.sort(SortMethod.PACKAGE)
            R.id.action_sort_disabled_first -> adapter.sort(SortMethod.STATE)
            R.id.action_system -> {
                //toolbar.subtitle = fully(full) + "sdk" + B_SDK.toString() + ":" + appopstype
                full = !full
                adapter.clear()
                loadApps(full)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun loadApps(boolean: Boolean) {
        swipeRefreshLayout.isRefreshing = false
        val ad = LovelyProgressDialog(this)
                .setTopColorRes(R.color.accent)
                .setTopTitle(getString(R.string.loading_dialog_title))
                //.setTopTitleColor(getColor(android.R.color.white))
                .setIcon(R.drawable.clock_alert)
                .setMessage(appopstype).show()

        async(UI) {
            if (boolean){
                val appsInfos = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                appsInfos.map {
                    val data = bg {
                        val ztest = checkAppOpsPermission(it.packageName , appopstype).get()
                        var fuel = ""
                        if (ztest.contains("time")) {
                            fuel = ztest.substring(ztest.indexOf("time")+5)
                            sigma++
                        }
                        AppItem(it.loadIcon(packageManager),
                                it.loadLabel(packageManager).toString(),
                                fuel,
                                it.packageName,
                                testB(ztest))
                    }

                    adapter.addItem(data.await())

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
                    val data = bg {
                        val ztest = checkAppOpsPermission(it.activityInfo.packageName , appopstype).get()
                        var fuel = ""
                        if (ztest.contains("time")) {
                            fuel = ztest.substring(ztest.indexOf("time")+5)
                            sigma++
                        }
                        AppItem(it.loadIcon(packageManager),
                                it.loadLabel(packageManager).toString(),
                                fuel,
                                it.activityInfo.packageName,
                                testB(ztest))
                    }

                    adapter.addItem(data.await())

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

    fun openGithub() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bitbucket.org/oF2pks/adbungfupermissionsetter/commits/")))
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
        enterAnim.start()
    }

    fun showInfoDialog() {
        LovelyStandardDialog(this)
                .setTopColorRes(R.color.accent)
                .setTopTitle(getString(R.string.button_open_information))
                //.setTopTitleColor(getColor(android.R.color.white))
                .setButtonsColorRes(R.color.primary)
                .setIcon(R.drawable.information)
                .setMessage(R.string.info_dialog_message)
                .setNegativeButton("!RESET ALL appOps!") {
                    Snackbar.make(coordinator, resetAppOpsPermission("").get(), Snackbar.LENGTH_LONG).show()
                    adapter.clear()
                    loadApps(full)
                }
                .setPositiveButton(getString(R.string.button_open_github)) {
                    openGithub()
                }
                .show()
    }

    fun sdkArray(): List<String?> {
        val sdkarray = arrayListOf<String>()

        val list = resources.getStringArray(R.array.permary)
        for (n in list.indices) {
            if (list[n].substring(0,2).toInt() <= B_SDK) {
                sdkarray.add(list[n].toString().substring(5))
            }
        }

        return sdkarray.sorted()
    }

}
