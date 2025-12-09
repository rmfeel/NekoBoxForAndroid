package io.nekohasekai.sagernet.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.api.ApiClient
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.URLEncoder

class DashboardFragment : Fragment() {

    private val selectProfileForNode = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val profileId = result.data?.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, -1L) ?: -1L
            if (profileId > 0) {
                val old = DataStore.selectedProxy
                DataStore.selectedProxy = profileId
                runOnDefaultDispatcher {
                    ProfileManager.postUpdate(old, true)
                    ProfileManager.postUpdate(profileId, true)
                }
                Toast.makeText(requireContext(), R.string.node_selected, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val planName = view.findViewById<TextView>(R.id.planName)
        val expiryDate = view.findViewById<TextView>(R.id.expiryDate)
        val trafficProgress = view.findViewById<ProgressBar>(R.id.trafficProgress)
        val trafficText = view.findViewById<TextView>(R.id.trafficText)

        fetchSubscriptionData(planName, expiryDate, trafficProgress, trafficText)

        val nodeSelectorButton = view.findViewById<android.widget.Button>(R.id.nodeSelectorButton)
        nodeSelectorButton.setOnClickListener {
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
    }

    private fun fetchSubscriptionData(
        planName: TextView,
        expiryDate: TextView,
        trafficProgress: ProgressBar,
        trafficText: TextView
    ) {
        ApiClient.service.getSubscribe().enqueue(object : Callback<JsonObject> {
            override fun onResponse(call: Call<JsonObject>, response: Response<JsonObject>) {
                if (!isAdded) return

                if (response.isSuccessful) {
                    val data = response.body()?.getAsJsonObject("data")
                    if (data != null) {
                        planName.text = if (data.has("plan_id")) {
                            getString(R.string.plan_id, data.get("plan_id").asString)
                        } else {
                            getString(R.string.standard_plan)
                        }

                        if (data.has("expired_at")) {
                            val expiredAt = data.get("expired_at").asLong
                            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                .format(java.util.Date(expiredAt * 1000))
                            expiryDate.text = getString(R.string.expires, date)
                        }

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

                        autoImportSubscription(data)
                    }
                }
            }

            override fun onFailure(call: Call<JsonObject>, t: Throwable) {
                if (!isAdded) return
                Toast.makeText(requireContext(), R.string.loading_failed, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun autoImportSubscription(data: JsonObject) {
        if (!data.has("subscribe_url")) return
        
        val subUrl = data.get("subscribe_url").asString
        if (subUrl.isEmpty()) return

        val lastImportedUrl = DataStore.configurationStore.getString("last_imported_sub_url")
        if (lastImportedUrl == subUrl) return

        DataStore.configurationStore.putString("last_imported_sub_url", subUrl)

        try {
            val act = requireActivity() as MainActivity
            val encodedUrl = URLEncoder.encode(subUrl, "UTF-8")
            val uri = Uri.parse("sn://subscription?url=$encodedUrl&name=Subscription")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage(act.packageName)
            act.startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
