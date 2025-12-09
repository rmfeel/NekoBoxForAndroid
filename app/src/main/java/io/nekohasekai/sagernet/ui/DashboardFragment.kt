package io.nekohasekai.sagernet.ui

import android.content.Intent
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
import io.nekohasekai.sagernet.ui.VpnRequestActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder
import androidx.core.net.toUri

class DashboardFragment : ToolbarFragment(R.layout.layout_dashboard) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(R.string.title_dashboard) // Ensure string exists or use literal

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
                                         // Call MainActivity import
                                         val act = requireActivity() as MainActivity
                                         // We need to parse URI.
                                         // Usually subscription is link, so importSubscription(Uri.parse(link))
                                         // But MainActivity expects special URI or just URL string?
                                         // importSubscription takes Uri.
                                         // If it's a http link, we construct "sn://subscription?url=..."
                                         
                                         // Simplified: Just trigger update if group exists, else create
                                         // For now, let's assume we pass it to import logic
                                         
                                         // Verify if we already have this subscription?
                                         // TODO: Logic to check duplicate
                                         
                                          val uri = androidx.core.net.toUri("sn://subscription?url=${java.net.URLEncoder.encode(subUrl, "UTF-8")}&name=NekoBoxSub")
                                          act.onNewIntent(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
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
             // Open the GroupFragment or a Dialog to select node
             // Simplified: Switch to Group Tab (if we kept it) or show a BottomSheet
             // Launch ProfileSelectActivity
             val intent = Intent(requireContext(), io.nekohasekai.sagernet.ui.ProfileSelectActivity::class.java)
             startActivity(intent)
        }
        
        val purchaseButton = view.findViewById<android.widget.Button>(R.id.purchaseButton)
        purchaseButton.setOnClickListener {
             // Open Website
             // Assuming DataStore.apiUrl is the base url, or we have a specific portal url
             // For now, allow opening the generic site
             val url = DataStore.apiUrl // Or user specific URL
             if (url.isNotEmpty()) {
                  val intent = Intent(Intent.ACTION_VIEW, androidx.core.net.toUri(url))
                  startActivity(intent)
             }
        }

        // Connect Logic
         connectButton.setOnClickListener {
            if (DataStore.serviceState.canStop) {
                SagerNet.stopService()
            } else {
                // Check if profile selected
                // If not, maybe auto select first
                if (DataStore.selectedProxy == 0L) {
                     // logic to auto select
                }
                
                // Launch
                 val intent = Intent(requireContext(), VpnRequestActivity::class.java)
                 requireContext().startActivity(intent)
            }
        }
        
        // Update Status UI based on DataStore.serviceState (Need observer or poll)
    }
}
