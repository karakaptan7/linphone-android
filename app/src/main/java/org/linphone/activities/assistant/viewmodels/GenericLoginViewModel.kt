package org.linphone.activities.assistant.viewmodels

import android.content.Context
import android.preference.PreferenceManager
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.databinding.BindingAdapter
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.Event

class GenericLoginViewModelFactory(private val accountCreator: AccountCreator) :
    ViewModelProvider.NewInstanceFactory() {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GenericLoginViewModel(accountCreator) as T
    }
}

class GenericLoginViewModel(private val accountCreator: AccountCreator) : ViewModel() {
    val email = MutableLiveData<String>()
    val password = MutableLiveData<String>()

    val apiUrl = MutableLiveData<String>().apply {
        value = "https://labeco.ttsanalsantral.com.tr/api_v1"
    }

    val message = MutableLiveData<String>()
    val waitForServerAnswer = MutableLiveData<Boolean>()
    val leaveAssistantEvent = MutableLiveData<Event<Boolean>>()
    val invalidCredentialsEvent = MutableLiveData<Event<Boolean>>()
    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var accountToCheck: Account? = null
    private var onLoginSuccess: (() -> Unit)? = null

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (account == accountToCheck) {
                Log.i("[Assistant] [Generic Login] Registration state is $state: $message")
                if (state == RegistrationState.Ok) {
                    waitForServerAnswer.value = false
                    leaveAssistantEvent.value = Event(true)
                    core.removeListener(this)
                } else if (state == RegistrationState.Failed) {
                    waitForServerAnswer.value = false
                    invalidCredentialsEvent.value = Event(true)
                    onErrorEvent.value = Event(message)
                    core.removeListener(this)
                }
            }
        }
    }

    fun onLoginButtonClicked(view: View) {
        val context = view.context

        if (email.value.isNullOrEmpty() || password.value.isNullOrEmpty() || apiUrl.value.isNullOrEmpty()) {
            message.value = "\nTüm alanlar doldurulmalıdır. Lütfen boş bırakmayınız."
            showAlert(context, "Bilgi", "Tüm alanlar doldurulmalıdır. \nLütfen boş bırakmayınız.")
            return
        }

        saveCredentials(context, email.value!!, password.value!!, apiUrl.value!!)

        waitForServerAnswer.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(apiUrl.value)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.doOutput = true

                val jsonInputString = JSONObject()
                    .put("action", "dahili_bilgi")
                    .put("email", email.value)
                    .put("sifre", password.value)
                    .toString()

                /*
                // Gönderilen JSON verisini ekranda gösterme
                withContext(Dispatchers.Main) {
                    showAlert(context, "Sent JSON", jsonInputString)
                }
                */

                connection.outputStream.use { os ->
                    val input = jsonInputString.toByteArray()
                    os.write(input, 0, input.size)
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use(
                        BufferedReader::readText
                    )

                    Log.i("[Assistant] [Generic Login] API Response: $response")

                    val jsonResponse = JSONObject(response)

                    // Yanıtın type alanını kontrol edelim
                    if (jsonResponse.getString("type") == "success") {
                        if (jsonResponse.has("values") && jsonResponse.get("values") is JSONObject) {
                            val values = jsonResponse.getJSONObject("values")

                            val sipUsername = values.getString("as_dahili")
                            val sipPassword = values.getString("sifre")
                            val sipDomain = values.getString("santral_ip")
                            val sipPort = values.getString("santral_port")

                            withContext(Dispatchers.Main) {
                                removeExistingAccount(sipUsername, sipDomain)

                                accountCreator.username = sipUsername
                                accountCreator.password = sipPassword
                                accountCreator.domain = sipDomain
                                accountCreator.displayName = sipUsername
                                accountCreator.transport = TransportType.Tls

                                // Log connection settings
                                Log.i(
                                    "[Assistant] [Generic Login] Connection settings: $sipUsername@$sipDomain:$sipPort (TLS)"
                                )

                                val core = coreContext.core
                                val accountParams = core.createAccountParams()
                                accountParams.identityAddress = core.createAddress(
                                    "sip:$sipUsername@$sipDomain"
                                )
                                accountParams.serverAddress = core.createAddress(
                                    "sip:$sipDomain:$sipPort;transport=tls"
                                )
                                accountParams.isRegisterEnabled = true

                                // Auth info ekleme
                                val authInfo = Factory.instance().createAuthInfo(
                                    sipUsername,
                                    null,
                                    sipPassword,
                                    null,
                                    null,
                                    sipDomain
                                )
                                core.addAuthInfo(authInfo)

                                // Hesap ayarlarını logla
                                Log.i("[Assistant] [Generic Login] SIP Username: $sipUsername")
                                Log.i("[Assistant] [Generic Login] SIP Password: $sipPassword")
                                Log.i("[Assistant] [Generic Login] SIP Domain: $sipDomain")

                                val account = core.createAccount(accountParams)
                                core.addAccount(account)
                                core.defaultAccount = account

                                accountToCheck = account

                                if (account == null) {
                                    Log.e(
                                        "[Assistant] [Generic Login] Account creator couldn't create account"
                                    )
                                    coreContext.core.removeListener(coreListener)
                                    onErrorEvent.value = Event(
                                        "Error: Failed to create account object"
                                    )
                                    waitForServerAnswer.value = false
                                    showAlert(context, "Error", "Failed to create account object")
                                    return@withContext
                                }

                                Log.i("[Assistant] [Generic Login] Account created")
                                coreContext.core.addListener(coreListener)
                                showAlert(context, "Success", "Başarıyla giriş yapıldı")
                                onLoginSuccess?.invoke() // Başarılı giriş sonrası callback'i çağır
                                // JSON yanıtını gösterme
                                // showAlert(context, "Success", jsonResponse.toString(4))
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                showAlert(context, "Error", "Invalid response format: $response")
                                waitForServerAnswer.value = false
                            }
                        }
                    } else {
                        // type success değilse hata göster
                        withContext(Dispatchers.Main) {
                            showAlert(context, "Error", "Kullanıcı adı ve şifre hatalı\n$response")
                            invalidCredentialsEvent.postValue(Event(true))
                            waitForServerAnswer.value = false
                        }
                    }
                } else {
                    onErrorEvent.postValue(Event("Error: ${connection.responseMessage}"))
                    waitForServerAnswer.postValue(false)
                    withContext(Dispatchers.Main) {
                        showAlert(context, "Error", connection.responseMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("[Assistant] [Generic Login] API call failed: ${e.message}")
                onErrorEvent.postValue(Event("Error: API call failed"))
                waitForServerAnswer.value = false
                withContext(Dispatchers.Main) {
                    showAlert(context, "Error", "API call failed: ${e.message}")
                }
            }
        }
    }

    fun setOnLoginSuccessCallback(callback: () -> Unit) {
        onLoginSuccess = callback
    }

    private fun removeExistingAccount(username: String, domain: String) {
        val core = coreContext.core
        val accounts = core.accountList
        for (account in accounts) {
            val identityAddress = account.params.identityAddress
            if (identityAddress != null) {
                val accountUsername = identityAddress.username
                val accountDomain = identityAddress.domain
                if (accountUsername == username && accountDomain == domain) {
                    val authInfo = account.findAuthInfo()
                    if (authInfo != null) core.removeAuthInfo(authInfo)
                    core.removeAccount(account)
                    Log.i("[Assistant] [Generic Login] Removed existing account: $username@$domain")
                    break
                }
            }
        }
    }

    private fun saveCredentials(context: Context, email: String, password: String, apiUrl: String) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        with(sharedPreferences.edit()) {
            putString("email", email)
            putString("password", password)
            putString("apiUrl", apiUrl)
            apply()
        }
    }

    fun loadCredentials(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        email.value = sharedPreferences.getString("email", "")
        password.value = sharedPreferences.getString("password", "")
        apiUrl.value = sharedPreferences.getString("apiUrl", "")
    }

    fun removeInvalidProxyConfig() {
        val account = accountToCheck
        account ?: return

        val core = coreContext.core
        val authInfo = account.findAuthInfo()
        if (authInfo != null) core.removeAuthInfo(authInfo)
        core.removeAccount(account)
        accountToCheck = null

        val accounts = core.accountList
        if (accounts.isNotEmpty() && core.defaultAccount == null) {
            core.defaultAccount = accounts.first()
            core.refreshRegisters()
        }
    }

    fun continueEvenIfInvalidCredentials() {
        leaveAssistantEvent.value = Event(true)
    }

    private fun showAlert(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        @JvmStatic
        @BindingAdapter("onLoginButtonClicked")
        fun bindLoginButton(button: View, viewModel: GenericLoginViewModel) {
            button.setOnClickListener {
                viewModel.onLoginButtonClicked(button)
            }
        }
    }
}
