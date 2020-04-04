package com.oF2pks.adbungfu.ExpandHmap


import android.os.Bundle;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity
import com.oF2pks.adbungfu.R


class ExpandableListDetail : AppCompatActivity() {

    internal var expandableListView: ExpandableListView? = null
    internal var expandableListAdapter: ExpandableListAdapter? = null
    internal var expandableListTitle: List<String>? = null
    internal var expandableListDetail: HashMap<String, List<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.hmap_main)
        expandableListView = findViewById(R.id.expandableListView) as ExpandableListView
        expandableListDetail = intent.getSerializableExtra("HMAP") as HashMap<String, List<String>>//suADB()
        expandableListTitle = ArrayList<String>(expandableListDetail!!.keys.sorted())
        expandableListAdapter = CustomExpandableListAdapter(this, expandableListTitle as ArrayList<String>, expandableListDetail!!)
        expandableListView!!.setAdapter(expandableListAdapter)
        expandableListView!!.setOnGroupExpandListener { groupPosition ->
            Toast.makeText(applicationContext,
                    (expandableListTitle as ArrayList<String>)[groupPosition] + " List Expanded.",
                    Toast.LENGTH_SHORT).show()
        }

        expandableListView!!.setOnGroupCollapseListener { groupPosition ->
            Toast.makeText(applicationContext,
                    (expandableListTitle as ArrayList<String>)[groupPosition] + " List Collapsed.",
                    Toast.LENGTH_SHORT).show()
        }

        expandableListView!!.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
            Toast.makeText(
                    applicationContext,
                    (expandableListTitle as ArrayList<String>)[groupPosition]
                            + " -> "
                            + expandableListDetail!![(expandableListTitle as ArrayList<String>)[groupPosition]]!!.get(
                            childPosition), Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

}