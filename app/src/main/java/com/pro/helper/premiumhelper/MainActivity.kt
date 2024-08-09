package com.pro.helper.premiumhelper

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pro.helper.pro_xutils.PremiumClientListeners
import com.pro.helper.pro_xutils.PremiumHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        /*  ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
              val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
              v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
              insets
          }*/

        PremiumHelper(this).setInAppKeys(mutableListOf("android.test.purchase"))
            .setSubKeys(mutableListOf("basic")).enableLogging(false)
            .setBillingClientListener(object : PremiumClientListeners {
                override fun onPurchasesUpdated() {
                    Log.i(
                        "premium",
                        "onPurchasesUpdated: called when user latest premium status fetched "
                    )
                }

                override fun onClientReady() {
                    Log.i(
                        "premium",
                        "onClientReady: Called when client ready after fetch products details and active product against user"
                    )
                }

                override fun onClientInitError() {
                    Log.i("premium", "onClientInitError: Called when client fail to init")
                }

            }).initialize()
    }
}