package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder

class DashboardFragment : ToolbarFragment(R.layout.layout_dashboard) {

    private val selectProfileForNode = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val profileId = result.data?.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, -1L) ?: -1L
            if (profileId > 0) {
                // Set the selected profile
                val old = DataStore.selectedProxy
                DataStore.selectedProxy = profileId
                runOnDefaultDispatcher {
                    ProfileManager.postUpdate(old, true)
                    ProfileManager.postUpdate(profileId, true)
                }
                Toast.makeText(requireContext(), "Node selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.title_dashboard)

        val planName = view.findViewById<TextView>(R.id.planName)
        val expiryDate = view.findViewById<TextView>(R.id.expiryDate)
        val trafficProgress = view.findViewById<ProgressBar>(R.id.trafficProgress)
        val trafficText = view.findViewById<TextView>(R.id.trafficText)
        val connectButton = view.findViewById<FloatingActionButton>(R.id.connectButton)
        val statusText = view.findViewById<TextView>(R.id.statusText)

        // Fetch subscription data
        fetchSubscriptionData(planName, expiryDate, trafficProgress, trafficText)

        val nodeSelectorButton = view.findViewById<android.widget.Button>(R.id.nodeSelectorButton)
        nodeSelectorButton.setOnClickListener {
            // Use startActivityForResult to receive the selected profile
            selectProfileForNode.launch(Intent(requireContext(), ProfileSelectActivity::class.java))
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
                // Check if there is a selected profile
                if (DataStore.selectedProxy == 0L) {
                    Toast.makeText(requireContext(), "Please select a node first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(requireContext(), VpnRequestActivity::class.java)
                requireContext().startActivity(intent)
            }
        }
    }

    private fun fetchSubscriptionData(
        planName: TextView,
        expiryDate: TextView,
        trafficProgress: ProgressBar,
        trafficText: TextView
    ) {
        ApiClient.service.getSubscribe().enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (!isAdded) return // Fragment not attached

                if (response.isSuccessful) {
                    val data = response.body()?.getAsJsonObject("data")
                    if (data != null) {
                        // Update plan name
                        planName.text = if (data.has("plan_id")) {
                            "Plan ID: ${data.get("plan_id").asString}"
                        } else {
                            "Standard Plan"
                        }

                        // Update expiry date
                        if (data.has("expired_at")) {
                            val expiredAt = data.get("expired_at").asLong
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(expiredAt * 1000))
                            expiryDate.text = "Expires: $date"
                        }

                        // Format traffic
                        val u = if (data.has("u")) data.get("u").asLong else 0L
                        val d = if (data.has("d")) data.get("d").asLong else 0L
                        val total = if (data.has("transfer_enable")) data.get("transfer_enable").asLong else 1024L * 1024L * 1024L
                        val used = u + d

                        val usedGb = used / 1024.0 / 1024.0 / 1024.0
                        val totalGb = total / 1024.0 / 1024.0 / 1024.0

                        trafficText.text = String.format("%.2f GB / %.2f GB", usedGb, totalGb)
                        trafficProgress.progress = if (total > 0) {
                            ((used.toDouble() / total.toDouble()) * 100).toInt()
                        } else {
                            0
                        }

                        // Auto Import subscription (only once)
                        autoImportSubscription(data)
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), "Failed to load data: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun autoImportSubscription(data: JsonObject) {
        if (!data.has("subscribe_url")) return
        
        val subUrl = data.get("subscribe_url").asString
        if (subUrl.isEmpty()) return

        // Check if we already imported (simple flag check)
        val lastImportedUrl = DataStore.configurationStore.getString("last_imported_sub_url")
        if (lastImportedUrl == subUrl) return

        // Save to prevent duplicate imports
        DataStore.configurationStore.putString("last_imported_sub_url", subUrl)

        try {
            val act = requireActivity() as MainActivity
            val encodedUrl = URLEncoder.encode(subUrl, "UTF-8")
            val uri = Uri.parse("sn://subscription?url=$encodedUrl&name=Subscription")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage(act.packageName)
            act.startActivity(intent)
        } catch (e: Exception) {
            // Ignore import errors
        }
    }
}
