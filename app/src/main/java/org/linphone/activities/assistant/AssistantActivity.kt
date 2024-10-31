/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.activities.assistant

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.GenericActivity
import org.linphone.activities.SnackBarActivity
import org.linphone.activities.assistant.viewmodels.SharedAssistantViewModel
import org.linphone.core.tools.Log

class AssistantActivity : GenericActivity(), SnackBarActivity {
    private lateinit var sharedViewModel: SharedAssistantViewModel
    private lateinit var coordinator: CoordinatorLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.assistant_activity)

        sharedViewModel = ViewModelProvider(this)[SharedAssistantViewModel::class.java]

        coordinator = findViewById(R.id.coordinator)

        corePreferences.firstStart = false

        // Call checkLocationPermission
        if (checkLocationPermission()) {
            // Permission is granted, proceed with location-related tasks
        } else {
// Permission is denied, show a warning and close the app
        }
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private fun checkLocationPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.READ_PHONE_STATE
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.READ_PHONE_STATE
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            getCellLocation()
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                getCellLocation()
            } else {
                // Permission denied
            }
        }
    }

    private fun getCellLocation() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cellLocation = if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        } else {
            telephonyManager.cellLocation
        }

        if (cellLocation != null) {
            when (cellLocation) {
                is GsmCellLocation -> {
                    val lac = cellLocation.lac
                    val cid = cellLocation.cid
                    Log.d("CellLocation", "GSM Cell Location - LAC: $lac, CID: $cid")
                }

                is CdmaCellLocation -> {
                    val baseStationId = cellLocation.baseStationId
                    val latitude = cellLocation.baseStationLatitude
                    val longitude = cellLocation.baseStationLongitude
                    Log.d(
                        "CellLocation",
                        "CDMA Cell Location - Base Station ID: $baseStationId, Latitude: $latitude, Longitude: $longitude"
                    )
                }

                else -> {
                    Log.d("CellLocation", "Unknown Cell Location")
                }
            }
        } else {
            Log.d("CellLocation", "Cell Location is null")
        }
    }

    override fun showSnackBar(@StringRes resourceId: Int) {
        Snackbar.make(coordinator, resourceId, Snackbar.LENGTH_LONG).show()
    }

    override fun showSnackBar(@StringRes resourceId: Int, action: Int, listener: () -> Unit) {
        Snackbar
            .make(findViewById(R.id.coordinator), resourceId, Snackbar.LENGTH_LONG)
            .setAction(action) {
                listener()
            }
            .show()
    }

    override fun showSnackBar(message: String) {
        Snackbar.make(coordinator, message, Snackbar.LENGTH_LONG).show()
    }
}
