/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
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
package org.linphone.ui.assistant

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import org.linphone.LinphoneApplication
import org.linphone.R
import org.linphone.databinding.AssistantActivityBinding
import org.linphone.databinding.ToastGreenBinding
import org.linphone.databinding.ToastRedBinding
import org.linphone.utils.slideInToastFromTopForDuration

@UiThread
class AssistantActivity : AppCompatActivity() {
    private lateinit var binding: AssistantActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)

        while (!LinphoneApplication.coreContext.isReady()) {
            Thread.sleep(20)
        }

        binding = DataBindingUtil.setContentView(this, R.layout.assistant_activity)
        binding.lifecycleOwner = this
    }

    fun showGreenToast(message: String, @DrawableRes icon: Int) {
        val greenToast: ToastGreenBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.toast_green,
            binding.toastsArea,
            false
        )
        greenToast.message = message
        greenToast.icon = icon
        greenToast.root.visibility = View.GONE
        binding.toastsArea.addView(greenToast.root)

        greenToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }

    fun showRedToast(message: String, @DrawableRes icon: Int) {
        val redToast: ToastRedBinding = DataBindingUtil.inflate(
            LayoutInflater.from(this),
            R.layout.toast_red,
            binding.toastsArea,
            false
        )
        redToast.message = message
        redToast.icon = icon
        redToast.root.visibility = View.GONE
        binding.toastsArea.addView(redToast.root)

        redToast.root.slideInToastFromTopForDuration(
            binding.toastsArea as ViewGroup,
            lifecycleScope
        )
    }
}
