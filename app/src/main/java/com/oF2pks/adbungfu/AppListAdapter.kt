package com.oF2pks.adbungfu

import android.graphics.Color
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.listitem_app.view.*
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Created by Pavel Sikun on 16.07.17.
 */
class AppListAdapter(val itemClick: (AppItem) -> Unit) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    enum class SortMethod { NAME, PACKAGE, STATE }

    val allItems = ArrayList<AppItem>()
    val displayedItems = ArrayList<AppItem>()
    var sortMethod = SortMethod.NAME

    inner class ViewHolder(view: View, val itemClick: (AppItem) -> Unit) : RecyclerView.ViewHolder(view) {

        fun bindAppItem(appItem: AppItem) = with(itemView) {
            fun setStatus(isEnabled: Boolean) {
                appItem.isEnabled = isEnabled
                permissionStatus.text = if (appItem.isEnabled) {
                    context.getString(R.string.message_allow)
                } else {
                    context.getString(R.string.message_ignore)
                }
            }

            permissionSwitch.setOnCheckedChangeListener(null)

            appIcon.setImageDrawable(appItem.appIcon)
            appName.text = appItem.appName
            appTime.text = appItem.appTime
            appPackage.text = appItem.appPackage
            if (appItem.isSystem) {
                appName.setTextColor(Color.BLACK)
                //appName.setError("zz")
            }
            else {
                appName.setTextColor(Color.WHITE)
                //appName.setError(null)
            }
            if (appTime.length() == 0) {
                itemView.setBackgroundColor(Color.GRAY)
            } else itemView.setBackgroundColor(Color.LTGRAY)

            permissionSwitch.isChecked = appItem.isEnabled
            setStatus(appItem.isEnabled)

            permissionSwitch.setOnCheckedChangeListener { _, _ ->
                setStatus(!appItem.isEnabled)
                itemClick(appItem)
            }

            container.setOnClickListener {
                permissionSwitch.isChecked = !permissionSwitch.isChecked
            }

            container.setOnLongClickListener {
                    val infoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    infoIntent.addCategory(Intent.CATEGORY_DEFAULT)
                    infoIntent.data = Uri.parse("package:${appItem.appPackage}")
                    context.startActivity(infoIntent)
                return@setOnLongClickListener true
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.listitem_app, parent, false)
        return ViewHolder(view, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindAppItem(displayedItems[position])
    }

    override fun getItemCount() = displayedItems.size

    fun addItem(appItem: AppItem) {
        allItems.add(appItem)
        sort()
    }

    fun clear() {
        allItems.clear()
        setItemsToDisplay(allItems)
    }

    fun sort(method: SortMethod = sortMethod) {
        sortMethod = method

        when (method) {
            SortMethod.NAME -> {
                allItems.sortBy { it.appName.toLowerCase() }
                setItemsToDisplay(allItems)
            }
            SortMethod.PACKAGE -> {
                allItems.sortBy { it.appPackage }
                setItemsToDisplay(allItems)
            }
            SortMethod.STATE -> {
                allItems.sortBy { it.isEnabled }
                setItemsToDisplay(allItems)
            }
        }
    }

    fun filter(keyword: String) {
        val filteredList = allItems.filter {
            it.appName.toLowerCase().contains(keyword) or it.appPackage.toLowerCase().contains(keyword)
        }

        setItemsToDisplay(filteredList)
    }

    fun setItemsToDisplay(items: List<AppItem>) {
        displayedItems.clear()
        displayedItems.addAll(items)
        notifyDataSetChanged()
    }
}
