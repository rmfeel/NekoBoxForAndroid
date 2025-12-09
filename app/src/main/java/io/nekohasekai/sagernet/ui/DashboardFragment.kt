package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder

class DashboardFragment : ToolbarFragment(R.layout.layout_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.title_dashboard)

        val planName = view.findViewById<TextView>(R.id.planName)
        val expiryDate = view.findViewById<TextView>(R.id.expiryDate)
        val trafficProgress = view.findViewById<ProgressBar>(R.id.trafficProgress)
        val trafficText = view.findViewById<TextView>(R.id.trafficText)
        val connectButton = view.findViewById<FloatingActionButton>(R.id.connectButton)
        val statusText = view.findViewById<TextView>(R.id.statusText)

        // Fetch Data & Auto Import
        runOnDefaultDispatcher {
            ApiClient.service.getSubscribe().enqueue(object : Callback<JsonObject> {
                override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                    if (response.isSuccessful) {
                        val data = response.body()?.getAsJsonObject("data")
                        if (data != null) {
                            runOnMainDispatcher {
                                planName.text = if(data.has("plan_id")) data.get("plan_id").asString else "Standard Plan"
                                
                                // format traffic
                                val u = if(data.has("u")) data.get("u").asLong else 0L
                                val d = if(data.has("d")) data.get("d").asLong else 0L
                                val total = if(data.has("transfer_enable")) data.get("transfer_enable").asLong else 1024L*1024L*1024L
                                val used = u + d
                                
                                val usedGb = used / 1024.0 / 1024.0 / 1024.0
                                val totalGb = total / 1024.0 / 1024.0 / 1024.0
                                
                                trafficText.text = String.format("%.2f GB / %.2f GB", usedGb, totalGb)
                                trafficProgress.progress = ((used.toDouble() / total.toDouble()) * 100).toInt()
                            }
                            
                            // Auto Import
                            if (data.has("subscribe_url")) {
                                val subUrl = data.get("subscribe_url").asString
                                if (subUrl.isNotEmpty()) {
                                    runOnMainDispatcher {
                                         val act = requireActivity() as MainActivity
                                         val encodedUrl = URLEncoder.encode(subUrl, "UTF-8")
                                         val uri = Uri.parse("sn://subscription?url=$encodedUrl&name=NekoBoxSub")
                                         val intent = Intent(Intent.ACTION_VIEW, uri)
                                         intent.setPackage(act.packageName)
                                         act.startActivity(intent)
                                    }
                                }
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                }
            })
        }
        
        
        val nodeSelectorButton = view.findViewById<android.widget.Button>(R.id.nodeSelectorButton)
        nodeSelectorButton.setOnClickListener {
             val intent = Intent(requireContext(), ProfileSelectActivity::class.java)
             startActivity(intent)
        }
        
        val purchaseButton = view.findViewById<android.widget.Button>(R.id.purchaseButton)
        purchaseButton.setOnClickListener {
             val url = DataStore.apiUrl
             if (!url.isNullOrEmpty()) {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                  startActivity(intent)
             }
        }

        // Connect Logic
         connectButton.setOnClickListener {
            if (DataStore.serviceState.canStop) {
                SagerNet.stopService()
            } else {
                 val intent = Intent(requireContext(), VpnRequestActivity::class.java)
                 requireContext().startActivity(intent)
            }
        }
    }
}
